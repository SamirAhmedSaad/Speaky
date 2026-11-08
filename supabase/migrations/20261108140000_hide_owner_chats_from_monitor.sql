-- The owner's private chats are hidden from the admin "Private chats" monitor for every other admin.
-- Additive and backward compatible: same function signatures, only a filter added.

create table if not exists private.monitor_hidden_users (
  user_id uuid primary key
);
alter table private.monitor_hidden_users enable row level security;

insert into private.monitor_hidden_users (user_id) values
  ('11abfeaf-1935-4541-b2cc-9eb71acd5333'),  -- Samir (samir.hayan@gmail.com)
  ('751ad4b1-785b-4b9c-a433-39d79fc6dedd')   -- Ahmed Saad (samir.hayan2@gmail.com)
on conflict do nothing;

-- True when the chat includes a hidden account and the viewer isn't one of them.
create or replace function private.room_hidden_from_monitor(p_room uuid)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
           select 1 from public.room_members mm
             join private.monitor_hidden_users h on h.user_id = mm.user_id
            where mm.room_id = p_room)
     and not exists (select 1 from private.monitor_hidden_users where user_id = (select auth.uid()));
$$;

create or replace function public.admin_private_chats(p_limit integer default 30, p_before timestamptz default null)
returns table(room_id uuid, last_message_at timestamptz, last_message_preview text, message_count bigint,
              user_a_id uuid, user_a_nickname text, user_a_photo_url text,
              user_b_id uuid, user_b_nickname text, user_b_photo_url text)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  return query
  select r.id, r.last_message_at, r.last_message_preview,
         (select count(*) from public.room_messages m where m.room_id = r.id),
         a.id, a.nickname, a.photo_url, b.id, b.nickname, b.photo_url
    from public.rooms r
    cross join lateral (
      select cu.id, cu.nickname, cu.photo_url from public.room_members mm
        join public.community_users cu on cu.id = mm.user_id
       where mm.room_id = r.id order by mm.user_id limit 1) a
    left join lateral (
      select cu.id, cu.nickname, cu.photo_url from public.room_members mm
        join public.community_users cu on cu.id = mm.user_id
       where mm.room_id = r.id and mm.user_id <> a.id limit 1) b on true
   where r.kind = 'direct'
     and r.last_message_at is not null
     and exists (select 1 from public.room_messages m where m.room_id = r.id)
     and not private.room_hidden_from_monitor(r.id)
     and (p_before is null or r.last_message_at < p_before)
   order by r.last_message_at desc
   limit least(greatest(p_limit, 1), 100);
end $$;

create or replace function public.admin_chat_messages(p_room uuid, p_limit integer default 50, p_before timestamptz default null)
returns table(id uuid, sender_id uuid, kind text, body text, voice_duration_ms integer, created_at timestamptz, edited_at timestamptz)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  if (select r.kind from public.rooms r where r.id = p_room) is distinct from 'direct' then raise exception 'not allowed'; end if;
  if private.room_hidden_from_monitor(p_room) then raise exception 'not allowed'; end if;
  return query
  select m.id, m.sender_id, m.kind, m.body, m.voice_duration_ms, m.created_at, m.edited_at
    from public.room_messages m
   where m.room_id = p_room and (p_before is null or m.created_at < p_before)
   order by m.created_at desc
   limit least(greatest(p_limit, 1), 200);
end $$;

create or replace function public.admin_chat_messages_v2(p_room uuid, p_limit int default 50, p_before timestamptz default null)
returns table (id uuid, sender_id uuid, kind text, body text, voice_path text, voice_duration_ms int,
               voice_waveform text, created_at timestamptz, edited_at timestamptz)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  if (select r.kind from public.rooms r where r.id = p_room) is distinct from 'direct' then raise exception 'not allowed'; end if;
  if private.room_hidden_from_monitor(p_room) then raise exception 'not allowed'; end if;
  return query
  select m.id, m.sender_id, m.kind, m.body, m.voice_path, m.voice_duration_ms, m.voice_waveform, m.created_at, m.edited_at
    from public.room_messages m
   where m.room_id = p_room and (p_before is null or m.created_at < p_before)
   order by m.created_at desc
   limit least(greatest(p_limit, 1), 200);
end $$;
