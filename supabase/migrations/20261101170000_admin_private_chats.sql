-- Moderation: admins can list every private chat and read its messages (read-only).
create or replace function public.admin_private_chats(p_limit int default 30, p_before timestamptz default null)
returns table(
  room_id uuid, last_message_at timestamptz, last_message_preview text, message_count bigint,
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
     -- Chats whose messages were all deleted have nothing to review.
     and exists (select 1 from public.room_messages m where m.room_id = r.id)
     and (p_before is null or r.last_message_at < p_before)
   order by r.last_message_at desc
   limit least(greatest(p_limit, 1), 100);
end $$;
revoke all on function public.admin_private_chats(int, timestamptz) from public, anon;
grant execute on function public.admin_private_chats(int, timestamptz) to authenticated;

create or replace function public.admin_chat_messages(p_room uuid, p_limit int default 50, p_before timestamptz default null)
returns table(id uuid, sender_id uuid, kind text, body text, voice_duration_ms int, created_at timestamptz, edited_at timestamptz)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  if (select r.kind from public.rooms r where r.id = p_room) is distinct from 'direct' then raise exception 'not allowed'; end if;
  return query
  select m.id, m.sender_id, m.kind, m.body, m.voice_duration_ms, m.created_at, m.edited_at
    from public.room_messages m
   where m.room_id = p_room and (p_before is null or m.created_at < p_before)
   order by m.created_at desc
   limit least(greatest(p_limit, 1), 200);
end $$;
revoke all on function public.admin_chat_messages(uuid, int, timestamptz) from public, anon;
grant execute on function public.admin_chat_messages(uuid, int, timestamptz) to authenticated;
