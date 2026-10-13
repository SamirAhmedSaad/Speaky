-- Applied via MCP. Hub alerts: one notification per user that grows (up to 5 messages) until they open the Hub.
alter table public.community_users add column if not exists hub_pending int not null default 0;

create or replace function public.mark_room_read(p_room uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if (select kind from public.rooms where id = p_room) = 'channel' then
    update public.community_users set hub_read_at = now(), hub_pending = 0 where id = (select auth.uid());
  else
    update public.room_members set last_read_at = now()
     where room_id = p_room and user_id = (select auth.uid());
  end if;
end $$;

create or replace function public.mark_hub_notified(p_users uuid[]) returns void
language sql security definer set search_path = '' as $$
  update public.community_users set hub_notified_at = now(), hub_pending = hub_pending + 1 where id = any(p_users);
$$;
revoke execute on function public.mark_hub_notified(uuid[]) from public, anon, authenticated;
grant execute on function public.mark_hub_notified(uuid[]) to service_role;

-- push_recipients_v2: the 'hub' branch now selects users with hub_pending < 5 and returns
-- unread = hub_pending + 1 (this message's line number in their notification).
create or replace function public.push_recipients_v2(p_message uuid)
returns table (token text, user_id uuid, kind text, room_id uuid, room_kind text, room_name text,
               sender_nickname text, preview text, unread bigint)
language sql stable security definer set search_path = '' as $$
  with msg as (
    select m.*, r.kind as room_kind, r.name as room_name, cu.nickname as sender_nickname,
           case when m.kind = 'voice' then '🎤 Voice message' else left(m.body, 140) end as preview
      from public.room_messages m
      join public.rooms r on r.id = m.room_id
      join public.community_users cu on cu.id = m.sender_id
     where m.id = p_message
  ),
  members as (
    select me.user_id, 'direct'::text as kind,
           (select count(*) from public.room_messages x
             where x.room_id = me.room_id and x.created_at > me.last_read_at and x.sender_id <> me.user_id) as unread
      from msg join public.room_members me on me.room_id = msg.room_id
     where msg.room_kind in ('group', 'direct') and me.user_id <> msg.sender_id and not me.muted
  ),
  reply as (
    select p.sender_id as user_id, 'reply'::text as kind, 1::bigint as unread
      from msg join public.room_messages p on p.id = msg.reply_to_id
     where msg.room_kind = 'channel' and p.sender_id <> msg.sender_id
  ),
  hub as (
    select cu.id as user_id, 'hub'::text as kind, (cu.hub_pending + 1)::bigint as unread
      from msg join public.community_users cu on cu.id <> msg.sender_id
     where msg.room_kind = 'channel'
       and cu.hub_notify
       and cu.banned_at is null
       and cu.hub_pending < 5
       and cu.id not in (select user_id from reply)
  ),
  everyone as (select * from members union all select * from reply union all select * from hub)
  select t.token, e.user_id, e.kind, msg.room_id, msg.room_kind, msg.room_name, msg.sender_nickname, msg.preview, e.unread
    from everyone e
    cross join msg
    join public.device_tokens t on t.user_id = e.user_id
   where not exists (select 1 from public.user_blocks b
                      where (b.blocker_id = e.user_id and b.blocked_id = msg.sender_id)
                         or (b.blocker_id = msg.sender_id and b.blocked_id = e.user_id));
$$;
revoke execute on function public.push_recipients_v2(uuid) from public, anon, authenticated;
grant execute on function public.push_recipients_v2(uuid) to service_role;
