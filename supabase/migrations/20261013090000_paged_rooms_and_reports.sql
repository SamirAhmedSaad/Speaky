-- Applied via MCP (paged_rooms_and_reports + my_unread_private_count).
-- Chat list, one page at a time (newest activity first). p_room returns just that room (opening a chat).
create or replace function public.my_rooms_page(
  p_limit int default 30,
  p_before_at timestamptz default null,
  p_before_id uuid default null,
  p_kind text default null,
  p_room uuid default null
)
returns table(room_id uuid, kind text, name text, description text, last_message_at timestamptz, last_message_preview text,
              member_count integer, unread_count bigint, my_role text, muted boolean, is_private boolean, invite_code text,
              other_user_id uuid, other_nickname text, other_gender text, other_photo_url text, other_last_seen timestamptz)
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
     and (p_kind is null or r.kind = p_kind)
     and (p_room is null or r.id = p_room)
     and (p_before_at is null or (r.last_message_at, r.id) < (p_before_at, coalesce(p_before_id, 'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid)))
   order by r.last_message_at desc, r.id desc
   limit least(greatest(coalesce(p_limit, 30), 1), 100);
$$;
revoke execute on function public.my_rooms_page(int, timestamptz, uuid, text, uuid) from public, anon;
grant execute on function public.my_rooms_page(int, timestamptz, uuid, text, uuid) to authenticated;

create index if not exists idx_rooms_last_message on public.rooms (last_message_at desc, id desc);

-- Reports for moderators, one page at a time.
create or replace function public.admin_reports_page(p_status text default 'open', p_limit int default 30, p_before timestamptz default null)
returns table (id uuid, created_at timestamptz, reason text, details text, message_snapshot text, status text,
               reporter_id uuid, reporter_nickname text, reported_user_id uuid, reported_nickname text,
               reported_banned boolean, times_reported bigint)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  return query
    select r.id, r.created_at, r.reason, r.details, r.message_snapshot, r.status,
           r.reporter_id, rep.nickname, r.reported_user_id, tgt.nickname, tgt.banned_at is not null,
           (select count(*) from public.user_reports x where x.reported_user_id = r.reported_user_id)
      from public.user_reports r
      join public.community_users rep on rep.id = r.reporter_id
      join public.community_users tgt on tgt.id = r.reported_user_id
     where (p_status = 'all' or r.status = p_status)
       and (p_before is null or r.created_at < p_before)
     order by r.created_at desc
     limit least(greatest(coalesce(p_limit, 30), 1), 100);
end $$;
revoke execute on function public.admin_reports_page(text, int, timestamptz) from public, anon;
grant execute on function public.admin_reports_page(text, int, timestamptz) to authenticated;

-- Home/Chats unread badge without downloading the chat list.
create or replace function public.my_unread_private_count() returns bigint
language sql stable security definer set search_path = '' as $$
  select count(*)
    from public.room_members me
    join public.rooms r on r.id = me.room_id and r.kind = 'direct'
    join public.room_messages msg on msg.room_id = me.room_id
   where me.user_id = (select auth.uid())
     and not me.muted
     and msg.created_at > me.last_read_at
     and msg.sender_id <> me.user_id;
$$;
revoke execute on function public.my_unread_private_count() from public, anon;
grant execute on function public.my_unread_private_count() to authenticated;
