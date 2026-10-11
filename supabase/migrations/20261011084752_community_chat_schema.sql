-- Community chat: Speaky Hub channel, groups, private chats.
-- ===== Tables =====
create table public.community_users (
  id uuid primary key references auth.users(id) on delete cascade,
  nickname text not null check (char_length(nickname) between 2 and 30),
  nickname_search text generated always as (lower(nickname)) stored,
  gender text not null default 'male',
  photo_url text,
  last_seen timestamptz not null default now(),
  created_at timestamptz not null default now()
);
create index idx_community_users_search on public.community_users (nickname_search);
create index idx_community_users_last_seen on public.community_users (last_seen desc);

create table public.rooms (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('channel', 'group', 'direct')),
  name text not null default '',
  description text not null default '',
  created_by uuid references public.community_users(id) on delete set null,
  dm_key text unique,
  last_message_at timestamptz not null default now(),
  last_message_preview text not null default '',
  member_count int not null default 0,
  created_at timestamptz not null default now(),
  check (kind <> 'group' or char_length(name) between 2 and 40)
);
create index idx_rooms_kind_last_message on public.rooms (kind, last_message_at desc);
create index idx_rooms_created_by on public.rooms (created_by);

create table public.room_members (
  room_id uuid not null references public.rooms(id) on delete cascade,
  user_id uuid not null references public.community_users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  last_read_at timestamptz not null default now(),
  primary key (room_id, user_id)
);
create index idx_room_members_user on public.room_members (user_id);

create table public.room_messages (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  sender_id uuid not null references public.community_users(id) on delete cascade,
  kind text not null default 'text' check (kind in ('text', 'voice')),
  body text not null default '' check (char_length(body) <= 2000),
  voice_path text,
  voice_duration_ms int not null default 0,
  voice_size_bytes bigint not null default 0,
  created_at timestamptz not null default now()
);
-- keyset pagination: newest first within a room
create index idx_room_messages_page on public.room_messages (room_id, created_at desc, id desc);
create index idx_room_messages_created on public.room_messages (created_at);
create index idx_room_messages_sender on public.room_messages (sender_id);
create index idx_room_messages_voice on public.room_messages (created_at) where voice_path is not null;

-- The global Speaky Hub channel
insert into public.rooms (id, kind, name, description)
values ('00000000-0000-0000-0000-000000000001', 'channel', 'Speaky Hub', 'Your global learning community');

-- ===== Access helpers (security definer avoids RLS recursion) =====
create function public.is_room_member(p_room uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.room_members
                 where room_id = p_room and user_id = (select auth.uid()));
$$;

create function public.can_read_room(p_room uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.rooms r where r.id = p_room
                 and (r.kind in ('channel', 'group')
                      or exists (select 1 from public.room_members m
                                 where m.room_id = r.id and m.user_id = (select auth.uid()))));
$$;

create function public.can_post_room(p_room uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.rooms r where r.id = p_room
                 and (r.kind = 'channel'
                      or exists (select 1 from public.room_members m
                                 where m.room_id = r.id and m.user_id = (select auth.uid()))));
$$;

-- ===== RLS =====
alter table public.community_users enable row level security;
alter table public.rooms enable row level security;
alter table public.room_members enable row level security;
alter table public.room_messages enable row level security;

create policy "Community users are visible to signed-in users" on public.community_users
  for select to authenticated using (true);
create policy "Users create their own community profile" on public.community_users
  for insert to authenticated with check ((select auth.uid()) = id);
create policy "Users update their own community profile" on public.community_users
  for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

create policy "Read channel, groups, and own private rooms" on public.rooms
  for select to authenticated
  using (kind in ('channel', 'group') or public.is_room_member(id));
create policy "Users create groups they own" on public.rooms
  for insert to authenticated
  with check (kind = 'group' and created_by = (select auth.uid()) and dm_key is null);

create policy "Read memberships of readable rooms" on public.room_members
  for select to authenticated using (public.can_read_room(room_id));
create policy "Users join groups themselves" on public.room_members
  for insert to authenticated
  with check (user_id = (select auth.uid())
              and exists (select 1 from public.rooms r where r.id = room_id and r.kind = 'group'));
create policy "Users leave rooms themselves" on public.room_members
  for delete to authenticated using (user_id = (select auth.uid()));
create policy "Users update their own read marker" on public.room_members
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create policy "Read messages of readable rooms" on public.room_messages
  for select to authenticated using (public.can_read_room(room_id));
create policy "Post as yourself where allowed" on public.room_messages
  for insert to authenticated
  with check (sender_id = (select auth.uid()) and public.can_post_room(room_id));
create policy "Delete your own messages" on public.room_messages
  for delete to authenticated using (sender_id = (select auth.uid()));

-- ===== Triggers =====
create function public.on_room_message_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  update public.rooms
     set last_message_at = new.created_at,
         last_message_preview = case when new.kind = 'voice' then '🎤 Voice message'
                                     else left(new.body, 120) end
   where id = new.room_id;
  return new;
end $$;
create trigger trg_room_message_insert after insert on public.room_messages
  for each row execute function public.on_room_message_insert();

create function public.on_room_member_change() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  update public.rooms
     set member_count = (select count(*) from public.room_members
                         where room_id = coalesce(new.room_id, old.room_id))
   where id = coalesce(new.room_id, old.room_id);
  return null;
end $$;
create trigger trg_room_member_change after insert or delete on public.room_members
  for each row execute function public.on_room_member_change();

create function public.on_group_created() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.kind = 'group' and new.created_by is not null then
    insert into public.room_members (room_id, user_id) values (new.id, new.created_by)
    on conflict do nothing;
  end if;
  return new;
end $$;
create trigger trg_group_created after insert on public.rooms
  for each row execute function public.on_group_created();

-- ===== RPCs =====
-- (get_or_create_dm is redefined with block checks in 20261011085940)
create function public.get_or_create_dm(p_other uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_me uuid := auth.uid();
  v_key text;
  v_room uuid;
begin
  if v_me is null then raise exception 'not signed in'; end if;
  if p_other = v_me then raise exception 'cannot message yourself'; end if;
  if not exists (select 1 from public.community_users where id = p_other) then
    raise exception 'user not found';
  end if;
  v_key := least(v_me::text, p_other::text) || ':' || greatest(v_me::text, p_other::text);
  insert into public.rooms (kind, dm_key) values ('direct', v_key)
  on conflict (dm_key) do nothing;
  select id into v_room from public.rooms where dm_key = v_key;
  insert into public.room_members (room_id, user_id)
  values (v_room, v_me), (v_room, p_other)
  on conflict do nothing;
  return v_room;
end $$;

-- Rooms the caller belongs to (groups + private chats), newest activity first
create function public.my_rooms()
returns table (
  room_id uuid, kind text, name text, last_message_at timestamptz,
  last_message_preview text, member_count int, unread_count bigint,
  other_user_id uuid, other_nickname text, other_gender text,
  other_photo_url text, other_last_seen timestamptz
)
language sql stable security definer set search_path = '' as $$
  select r.id, r.kind, r.name, r.last_message_at, r.last_message_preview, r.member_count,
         (select count(*) from public.room_messages msg
           where msg.room_id = r.id and msg.created_at > me.last_read_at
             and msg.sender_id <> me.user_id) as unread_count,
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

create function public.mark_room_read(p_room uuid) returns void
language sql security definer set search_path = '' as $$
  update public.room_members set last_read_at = now()
   where room_id = p_room and user_id = (select auth.uid());
$$;

revoke execute on function public.get_or_create_dm(uuid), public.my_rooms(), public.mark_room_read(uuid),
  public.is_room_member(uuid), public.can_read_room(uuid), public.can_post_room(uuid) from public, anon;
grant execute on function public.get_or_create_dm(uuid), public.my_rooms(), public.mark_room_read(uuid),
  public.is_room_member(uuid), public.can_read_room(uuid), public.can_post_room(uuid) to authenticated;
revoke execute on function public.on_room_message_insert(), public.on_room_member_change(),
  public.on_group_created() from public, anon, authenticated;

-- ===== Realtime =====
alter publication supabase_realtime add table public.room_messages;

-- ===== Voice bucket limits: 5 MB per clip, audio only =====
update storage.buckets
   set file_size_limit = 5242880,
       allowed_mime_types = array['audio/mp4', 'audio/m4a', 'audio/x-m4a', 'audio/aac', 'audio/mpeg']
 where id = 'voice-recordings';
