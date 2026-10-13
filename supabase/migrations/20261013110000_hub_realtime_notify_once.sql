-- Applied via MCP. Speaky Hub: notify on a new message, but each user only once until they open the Hub
-- again (mark_room_read sets hub_read_at; push-notify sets hub_notified_at via mark_hub_notified).
-- kind: 'direct' / 'group' (members), 'reply' (author of the quoted Hub message), 'hub' (everyone else).
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
    select cu.id as user_id, 'hub'::text as kind, 1::bigint as unread
      from msg join public.community_users cu on cu.id <> msg.sender_id
     where msg.room_kind = 'channel'
       and cu.hub_notify
       and cu.banned_at is null
       -- one Hub notification until they open the Hub again
       and (cu.hub_notified_at is null or cu.hub_notified_at <= coalesce(cu.hub_read_at, '-infinity'))
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

-- Every message goes to push-notify (it decides who still needs a notification).
create or replace function public.on_message_push() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform net.http_post(
    url := 'https://bkivjjvgzsqgbkfubjtx.supabase.co/functions/v1/push-notify',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-push-secret', (select value from private.app_secrets where name = 'push_secret')),
    body := jsonb_build_object('message_id', new.id),
    timeout_milliseconds := 5000);
  return new;
end $$;
