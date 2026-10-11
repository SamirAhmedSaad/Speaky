-- Community v2: guests, group roles + private groups, bans, edit/delete, spam limits,
-- account deletion, push tokens, private voice clips, realtime "Seen".
-- NOT YET APPLIED: paste into Supabase Dashboard → SQL Editor → Run.

-- ========== Guests (anonymous sign-in) ==========
create function public.is_guest() returns boolean
language sql stable set search_path = '' as $$
  select coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false);
$$;

-- ========== Group roles, private groups, mute ==========
alter table public.room_members
  add column role text not null default 'member' check (role in ('owner', 'admin', 'member')),
  add column muted boolean not null default false;

alter table public.rooms
  add column is_private boolean not null default false,
  add column invite_code text unique;

update public.rooms set invite_code = substr(md5(gen_random_uuid()::text), 1, 8) where kind = 'group';
update public.room_members m set role = 'owner'
  from public.rooms r where r.id = m.room_id and r.kind = 'group' and r.created_by = m.user_id;

create or replace function public.on_group_created() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.kind = 'group' and new.created_by is not null then
    insert into public.room_members (room_id, user_id, role) values (new.id, new.created_by, 'owner')
    on conflict do nothing;
  end if;
  return new;
end $$;

create function public.on_group_before_insert() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.kind = 'group' and new.invite_code is null then
    new.invite_code := substr(md5(gen_random_uuid()::text), 1, 8);
  end if;
  return new;
end $$;
create trigger trg_group_before_insert before insert on public.rooms
  for each row execute function public.on_group_before_insert();

drop policy "Users create groups they own" on public.rooms;
create policy "Signed-in users create groups they own" on public.rooms
  for insert to authenticated
  with check (kind = 'group' and created_by = (select auth.uid()) and dm_key is null
              and not public.is_guest());

-- ========== Bans ==========
alter table public.community_users
  add column banned_at timestamptz,
  add column ban_reason text;

create function public.is_banned(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.community_users where id = p_user and banned_at is not null);
$$;

-- ========== Read / post rules (private groups, bans, guests) ==========
create or replace function public.can_read_room(p_room uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.rooms r where r.id = p_room
                 and (r.kind = 'channel'
                      or (r.kind = 'group' and not r.is_private)
                      or exists (select 1 from public.room_members m
                                 where m.room_id = r.id and m.user_id = (select auth.uid()))));
$$;

create or replace function public.can_post_room(p_room uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select not public.is_banned((select auth.uid()))
     and exists (
    select 1 from public.rooms r
     where r.id = p_room
       and (r.kind = 'channel'
            or (not public.is_guest()
                and exists (select 1 from public.room_members m
                             where m.room_id = r.id and m.user_id = (select auth.uid()))
                and (r.kind <> 'direct'
                     or not exists (select 1 from public.room_members o
                                     where o.room_id = r.id and o.user_id <> (select auth.uid())
                                       and public.is_blocked_between(o.user_id, (select auth.uid())))))));
$$;

create or replace function public.get_or_create_dm(p_other uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_me uuid := auth.uid();
  v_key text;
  v_room uuid;
begin
  if v_me is null then raise exception 'not signed in'; end if;
  if public.is_guest() then raise exception 'guest_not_allowed'; end if;
  if public.is_banned(v_me) then raise exception 'banned'; end if;
  if p_other = v_me then raise exception 'cannot message yourself'; end if;
  if not exists (select 1 from public.community_users where id = p_other) then
    raise exception 'user not found';
  end if;
  v_key := least(v_me::text, p_other::text) || ':' || greatest(v_me::text, p_other::text);
  select id into v_room from public.rooms where dm_key = v_key;
  if v_room is null then
    if public.is_blocked_between(v_me, p_other) then
      raise exception 'blocked' using errcode = 'P0001';
    end if;
    insert into public.rooms (kind, dm_key) values ('direct', v_key)
    on conflict (dm_key) do nothing;
    select id into v_room from public.rooms where dm_key = v_key;
    insert into public.room_members (room_id, user_id)
    values (v_room, v_me), (v_room, p_other)
    on conflict do nothing;
  end if;
  return v_room;
end $$;

drop policy "Read channel, groups, and own private rooms" on public.rooms;
create policy "Read channel, public groups, and own rooms" on public.rooms
  for select to authenticated
  using (kind = 'channel' or (kind = 'group' and not is_private) or public.is_room_member(id));

drop policy "Users join groups themselves" on public.room_members;
create policy "Signed-in users join public groups" on public.room_members
  for insert to authenticated
  with check (user_id = (select auth.uid()) and role = 'member'
              and not public.is_guest() and not public.is_banned((select auth.uid()))
              and exists (select 1 from public.rooms r
                          where r.id = room_id and r.kind = 'group' and not r.is_private));

drop policy "Users update their own read marker" on public.room_members;
create policy "Users update their own read marker and mute" on public.room_members
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create function public.on_member_update_guard() returns trigger
language plpgsql set search_path = '' as $$
begin
  if current_user = 'authenticated' and (new.role <> old.role or new.room_id <> old.room_id or new.user_id <> old.user_id) then
    raise exception 'not allowed';
  end if;
  return new;
end $$;
create trigger trg_member_update_guard before update on public.room_members
  for each row execute function public.on_member_update_guard();

-- ========== Group management RPCs ==========
create function public.my_group_role(p_room uuid) returns text
language sql stable security definer set search_path = '' as $$
  select role from public.room_members where room_id = p_room and user_id = (select auth.uid());
$$;

create function public.update_group(p_room uuid, p_name text, p_description text, p_is_private boolean)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if coalesce(public.my_group_role(p_room), '') not in ('owner', 'admin') then raise exception 'not allowed'; end if;
  update public.rooms set name = trim(p_name), description = trim(coalesce(p_description, '')),
                          is_private = p_is_private
   where id = p_room and kind = 'group';
end $$;

create function public.regenerate_invite_code(p_room uuid) returns text
language plpgsql security definer set search_path = '' as $$
declare v_code text := substr(md5(gen_random_uuid()::text), 1, 8);
begin
  if coalesce(public.my_group_role(p_room), '') not in ('owner', 'admin') then raise exception 'not allowed'; end if;
  update public.rooms set invite_code = v_code where id = p_room and kind = 'group';
  return v_code;
end $$;

create function public.join_group_by_code(p_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_room uuid;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if public.is_guest() then raise exception 'guest_not_allowed'; end if;
  if public.is_banned(auth.uid()) then raise exception 'banned'; end if;
  select id into v_room from public.rooms where kind = 'group' and invite_code = lower(trim(p_code));
  if v_room is null then raise exception 'invalid code'; end if;
  insert into public.room_members (room_id, user_id) values (v_room, auth.uid()) on conflict do nothing;
  return v_room;
end $$;

create function public.remove_group_member(p_room uuid, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v_my text := public.my_group_role(p_room); v_their text;
begin
  select role into v_their from public.room_members where room_id = p_room and user_id = p_user;
  if v_my is null or v_my = 'member' or v_their is null or v_their = 'owner'
     or (v_my = 'admin' and v_their = 'admin') then
    raise exception 'not allowed';
  end if;
  delete from public.room_members where room_id = p_room and user_id = p_user;
end $$;

create function public.set_group_member_role(p_room uuid, p_user uuid, p_role text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if coalesce(public.my_group_role(p_room), '') <> 'owner' or p_role not in ('admin', 'member') or p_user = auth.uid() then
    raise exception 'not allowed';
  end if;
  update public.room_members set role = p_role where room_id = p_room and user_id = p_user;
end $$;

create function public.delete_group(p_room uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if coalesce(public.my_group_role(p_room), '') <> 'owner' then raise exception 'not allowed'; end if;
  delete from public.rooms where id = p_room and kind = 'group';
end $$;

-- When the owner leaves (or deletes their account), hand the group to the oldest admin/member
create or replace function public.on_room_member_change() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_room uuid := coalesce(new.room_id, old.room_id);
begin
  if tg_op = 'DELETE' and old.role = 'owner' then
    update public.room_members set role = 'owner'
     where (room_id, user_id) = (
       select room_id, user_id from public.room_members where room_id = v_room
        order by (role = 'admin') desc, joined_at asc limit 1);
  end if;
  update public.rooms
     set member_count = (select count(*) from public.room_members where room_id = v_room)
   where id = v_room;
  return null;
end $$;

create function public.room_member_list(p_room uuid)
returns table (user_id uuid, role text, last_read_at timestamptz, nickname text, gender text,
               photo_url text, last_seen timestamptz)
language sql stable security definer set search_path = '' as $$
  select m.user_id, m.role, m.last_read_at, cu.nickname, cu.gender, cu.photo_url, cu.last_seen
    from public.room_members m join public.community_users cu on cu.id = m.user_id
   where m.room_id = p_room and public.can_read_room(p_room)
   order by case m.role when 'owner' then 0 when 'admin' then 1 else 2 end, cu.nickname;
$$;

drop function public.my_rooms();
create function public.my_rooms()
returns table (
  room_id uuid, kind text, name text, description text, last_message_at timestamptz,
  last_message_preview text, member_count int, unread_count bigint,
  my_role text, muted boolean, is_private boolean, invite_code text,
  other_user_id uuid, other_nickname text, other_gender text,
  other_photo_url text, other_last_seen timestamptz
)
language sql stable security definer set search_path = '' as $$
  select r.id, r.kind, r.name, r.description, r.last_message_at, r.last_message_preview, r.member_count,
         (select count(*) from public.room_messages msg
           where msg.room_id = r.id and msg.created_at > me.last_read_at
             and msg.sender_id <> me.user_id) as unread_count,
         me.role, me.muted, r.is_private, r.invite_code,
         o.id, o.nickname, o.gender, o.photo_url, o.last_seen
    from public.room_members me
    join public.rooms r on r.id = me.room_id
    left join lateral (
      select cu.* from public.room_members om
      join public.community_users cu on cu.id = om.user_id
      where r.kind = 'direct' and om.room_id = r.id and om.user_id <> me.user_id
      limit 1
    ) o on true
   where me.user_id = (select auth.uid())
   order by r.last_message_at desc;
$$;

-- ========== Edit / delete messages ==========
alter table public.room_messages add column edited_at timestamptz;

create policy "Edit your own text messages" on public.room_messages
  for update to authenticated
  using (sender_id = (select auth.uid()) and kind = 'text')
  with check (sender_id = (select auth.uid()) and kind = 'text');

create function public.on_message_update_guard() returns trigger
language plpgsql set search_path = '' as $$
begin
  if current_user = 'authenticated' then
    if new.id <> old.id or new.room_id <> old.room_id or new.sender_id <> old.sender_id
       or new.kind <> old.kind or new.created_at <> old.created_at
       or new.voice_path is distinct from old.voice_path then
      raise exception 'only the text can be edited';
    end if;
    if old.created_at < now() - interval '24 hours' then
      raise exception 'messages can only be edited for 24 hours';
    end if;
    new.edited_at := now();
  end if;
  return new;
end $$;
create trigger trg_message_update_guard before update on public.room_messages
  for each row execute function public.on_message_update_guard();

-- Deletes must reach other clients with the row's room_id
alter table public.room_messages replica identity full;

-- ========== Server-side spam limits ==========
create function public.on_message_rate_limit() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_recent int; v_today int;
begin
  if public.is_banned(new.sender_id) then raise exception 'banned'; end if;
  if new.kind = 'voice' and public.is_guest() then raise exception 'guest_not_allowed'; end if;
  select count(*) into v_recent from public.room_messages
   where sender_id = new.sender_id and created_at > now() - interval '1 minute';
  if v_recent >= 20 then raise exception 'rate_limited'; end if;
  if (select kind from public.rooms where id = new.room_id) = 'channel' then
    select count(*) into v_today from public.room_messages m
     join public.rooms r on r.id = m.room_id and r.kind = 'channel'
     where m.sender_id = new.sender_id and m.created_at >= date_trunc('day', now());
    if v_today >= 50 then raise exception 'daily_limit'; end if;
  end if;
  return new;
end $$;
create trigger trg_message_rate_limit before insert on public.room_messages
  for each row execute function public.on_message_rate_limit();

-- ========== Account deletion ==========
-- Deletes the caller's own login; cascades remove profile, memberships, messages, blocks, tokens.
create function public.delete_my_account() returns void
language plpgsql security definer set search_path = '' as $$
declare v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'not signed in'; end if;
  delete from auth.users where id = v_me;
end $$;

-- ========== Push notification tokens ==========
create table public.device_tokens (
  token text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);
create index idx_device_tokens_user on public.device_tokens (user_id);
alter table public.device_tokens enable row level security;
create policy "Users manage their own device tokens" on public.device_tokens
  for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

insert into private.app_secrets (name, value)
values ('push_secret', encode(extensions.gen_random_bytes(32), 'hex'));

create function public.verify_push_secret(p_secret text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from private.app_secrets where name = 'push_secret' and value = p_secret);
$$;

create function public.on_message_push() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (select kind from public.rooms where id = new.room_id) in ('group', 'direct') then
    perform net.http_post(
      url := 'https://bkivjjvgzsqgbkfubjtx.supabase.co/functions/v1/push-notify',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'x-push-secret', (select value from private.app_secrets where name = 'push_secret')),
      body := jsonb_build_object('message_id', new.id),
      timeout_milliseconds := 5000);
  end if;
  return new;
end $$;
create trigger trg_message_push after insert on public.room_messages
  for each row execute function public.on_message_push();

create function public.push_recipients(p_message uuid)
returns table (token text, room_id uuid, room_kind text, room_name text,
               sender_nickname text, preview text, unread bigint)
language sql stable security definer set search_path = '' as $$
  select t.token, r.id, r.kind, r.name, cu.nickname,
         case when msg.kind = 'voice' then '🎤 Voice message' else left(msg.body, 140) end,
         (select count(*) from public.room_messages x
           where x.room_id = r.id and x.created_at > m.last_read_at and x.sender_id <> m.user_id)
    from public.room_messages msg
    join public.rooms r on r.id = msg.room_id
    join public.community_users cu on cu.id = msg.sender_id
    join public.room_members m on m.room_id = r.id and m.user_id <> msg.sender_id and not m.muted
    join public.device_tokens t on t.user_id = m.user_id
   where msg.id = p_message
     and not exists (select 1 from public.user_blocks b
                      where (b.blocker_id = m.user_id and b.blocked_id = msg.sender_id)
                         or (b.blocker_id = msg.sender_id and b.blocked_id = m.user_id));
$$;

-- ========== Admin moderation helpers (run from the SQL editor) ==========
-- select private.ban_user('<user uuid>', 'reason', true);   -- true = also delete their messages
-- select private.unban_user('<user uuid>');
create function private.ban_user(p_user uuid, p_reason text default '', p_delete_messages boolean default false)
returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.community_users set banned_at = now(), ban_reason = p_reason where id = p_user;
  if p_delete_messages then delete from public.room_messages where sender_id = p_user; end if;
  update public.user_reports set status = 'actioned' where reported_user_id = p_user and status = 'open';
end $$;
create function private.unban_user(p_user uuid) returns void
language sql security definer set search_path = '' as $$
  update public.community_users set banned_at = null, ban_reason = null where id = p_user;
$$;

-- ========== Private voice clips ==========
create function public.can_read_voice(p_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select case
    when (storage.foldername(p_name))[2] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      then public.can_read_room(((storage.foldername(p_name))[2])::uuid)
    else false end;
$$;
create function public.can_upload_voice(p_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select not public.is_guest()
     and (storage.foldername(p_name))[1] = (select auth.uid())::text
     and case
       when (storage.foldername(p_name))[2] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
         then public.can_post_room(((storage.foldername(p_name))[2])::uuid)
       else false end;
$$;

update storage.buckets set public = false where id = 'voice-recordings';
drop policy "Users read own voice recordings" on storage.objects;
drop policy "Users upload own voice recordings" on storage.objects;
create policy "Chat members read voice clips" on storage.objects
  for select to authenticated
  using (bucket_id = 'voice-recordings' and public.can_read_voice(name));
create policy "Members upload voice clips to their chats" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'voice-recordings' and public.can_upload_voice(name));

-- ========== Realtime for "Seen" ==========
alter publication supabase_realtime add table public.room_members;

-- ========== Grants ==========
revoke execute on function public.is_banned(uuid), public.my_group_role(uuid),
  public.update_group(uuid, text, text, boolean), public.regenerate_invite_code(uuid),
  public.join_group_by_code(text), public.remove_group_member(uuid, uuid),
  public.set_group_member_role(uuid, uuid, text), public.delete_group(uuid),
  public.room_member_list(uuid), public.my_rooms(), public.delete_my_account(),
  public.can_read_voice(text), public.can_upload_voice(text) from public, anon;
grant execute on function public.is_banned(uuid), public.my_group_role(uuid),
  public.update_group(uuid, text, text, boolean), public.regenerate_invite_code(uuid),
  public.join_group_by_code(text), public.remove_group_member(uuid, uuid),
  public.set_group_member_role(uuid, uuid, text), public.delete_group(uuid),
  public.room_member_list(uuid), public.my_rooms(), public.delete_my_account(),
  public.can_read_voice(text), public.can_upload_voice(text) to authenticated;
revoke execute on function public.verify_push_secret(text), public.push_recipients(uuid)
  from public, anon, authenticated;
grant execute on function public.verify_push_secret(text), public.push_recipients(uuid) to service_role;
revoke execute on function public.on_message_push(), public.on_message_rate_limit(),
  public.on_message_update_guard(), public.on_member_update_guard(), public.on_group_before_insert()
  from public, anon, authenticated;
