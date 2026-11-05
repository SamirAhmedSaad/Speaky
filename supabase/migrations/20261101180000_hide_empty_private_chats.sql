-- A private chat that was opened but never used doesn't show in the chat list (it can still be opened by id).
create or replace function public.my_rooms_page(p_limit integer default 30, p_before_at timestamp with time zone default null, p_before_id uuid default null, p_kind text default null, p_room uuid default null)
returns table(room_id uuid, kind text, name text, description text, last_message_at timestamp with time zone, last_message_preview text, member_count integer, unread_count bigint, my_role text, muted boolean, is_private boolean, invite_code text, other_user_id uuid, other_nickname text, other_gender text, other_photo_url text, other_last_seen timestamp with time zone)
language sql
stable security definer
set search_path to ''
as $function$
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
     and (p_kind is null or r.kind = p_kind)
     and (p_room is null or r.id = p_room)
     and (p_room is not null or r.kind <> 'direct'
          or exists (select 1 from public.room_messages m where m.room_id = r.id))
     and (p_before_at is null or (r.last_message_at, r.id) < (p_before_at, coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
   order by r.last_message_at desc, r.id desc
   limit least(greatest(coalesce(p_limit, 30), 1), 100);
$function$;
