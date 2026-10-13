-- Applied via MCP. Gentle Speaky Hub notifications: a summary at most twice a day, only after missed messages.
alter table public.community_users
  add column if not exists hub_notify boolean not null default true,
  add column if not exists hub_read_at timestamptz,
  add column if not exists hub_notified_at timestamptz;

-- Reading the Hub (a channel without membership rows) is remembered on the user.
create or replace function public.mark_room_read(p_room uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if (select kind from public.rooms where id = p_room) = 'channel' then
    update public.community_users set hub_read_at = now() where id = (select auth.uid());
  else
    update public.room_members set last_read_at = now()
     where room_id = p_room and user_id = (select auth.uid());
  end if;
end $$;

create or replace function public.set_hub_notify(p_on boolean) returns void
language sql security definer set search_path = '' as $$
  update public.community_users set hub_notify = p_on where id = (select auth.uid());
$$;
revoke execute on function public.set_hub_notify(boolean) from public, anon;
grant execute on function public.set_hub_notify(boolean) to authenticated;

create or replace function public.my_hub_notify() returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce((select hub_notify from public.community_users where id = (select auth.uid())), true);
$$;
revoke execute on function public.my_hub_notify() from public, anon;
grant execute on function public.my_hub_notify() to authenticated;

-- Who gets a Hub summary now: opted in, not banned, not active in the last 10 minutes,
-- no summary in the last 6 hours (so at most 2 a day), and 3+ messages from others since
-- they last opened the Hub or were last notified.
create or replace function public.hub_digest_candidates()
returns table (user_id uuid, token text, new_count bigint, last_sender text, last_preview text)
language sql stable security definer set search_path = '' as $$
  with hub as (select id from public.rooms where kind = 'channel' order by created_at limit 1),
  eligible as (
    select cu.id, greatest(coalesce(cu.hub_read_at, cu.created_at), coalesce(cu.hub_notified_at, '-infinity')) as since
      from public.community_users cu
     where cu.hub_notify
       and cu.banned_at is null
       and (cu.last_seen is null or cu.last_seen < now() - interval '10 minutes')
       and (cu.hub_notified_at is null or cu.hub_notified_at < now() - interval '6 hours')
  ),
  counts as (
    select e.id, count(m.id) as n, max(m.created_at) as last_at
      from eligible e
      join public.room_messages m on m.room_id = (select id from hub) and m.created_at > e.since and m.sender_id <> e.id
       and not exists (select 1 from public.user_blocks b where b.blocker_id = e.id and b.blocked_id = m.sender_id)
     group by e.id
    having count(m.id) >= 3
  )
  select c.id, t.token, c.n, s.nickname,
         case when lm.kind = 'voice' then '🎤 Voice message' else left(lm.body, 80) end
    from counts c
    join public.device_tokens t on t.user_id = c.id
    join lateral (
      select m.* from public.room_messages m
       where m.room_id = (select id from hub) and m.created_at = c.last_at and m.sender_id <> c.id
       limit 1
    ) lm on true
    join public.community_users s on s.id = lm.sender_id;
$$;
revoke execute on function public.hub_digest_candidates() from public, anon, authenticated;
grant execute on function public.hub_digest_candidates() to service_role;

create or replace function public.mark_hub_notified(p_users uuid[]) returns void
language sql security definer set search_path = '' as $$
  update public.community_users set hub_notified_at = now() where id = any(p_users);
$$;
revoke execute on function public.mark_hub_notified(uuid[]) from public, anon, authenticated;
grant execute on function public.mark_hub_notified(uuid[]) to service_role;

-- Turning it on (sends to real users): run every 15 minutes.
-- select cron.schedule('hub-digest', '*/15 * * * *', $$
--   select net.http_post(
--     url := 'https://bkivjjvgzsqgbkfubjtx.supabase.co/functions/v1/hub-digest',
--     headers := jsonb_build_object('Content-Type', 'application/json',
--       'x-push-secret', (select value from private.app_secrets where name = 'push_secret')),
--     body := '{}'::jsonb, timeout_milliseconds := 15000);
-- $$);
