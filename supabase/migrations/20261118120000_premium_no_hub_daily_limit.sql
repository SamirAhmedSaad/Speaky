-- Premium members have no daily Speaky Hub message limit (was 50/day for everyone).
-- Everything else is unchanged: bans, rules, content filter and the 20-per-minute anti-spam limit
-- still apply to everyone. Safe: replaces one trigger function in place, no table or data changes.
create or replace function public.on_message_rate_limit()
returns trigger
language plpgsql
security definer
set search_path to ''
as $function$
declare v_recent int; v_today int;
begin
  if public.is_banned(new.sender_id) then raise exception 'banned'; end if;
  if not public.has_accepted_terms(new.sender_id) then raise exception 'terms_not_accepted'; end if;
  if new.kind = 'voice' and public.is_guest() then raise exception 'guest_not_allowed'; end if;
  if new.kind = 'text' and public.violates_content_rules(new.body) then raise exception 'content_blocked'; end if;
  select count(*) into v_recent from public.room_messages
   where sender_id = new.sender_id and created_at > now() - interval '1 minute';
  if v_recent >= 20 then raise exception 'rate_limited'; end if;
  if (select kind from public.rooms where id = new.room_id) = 'channel'
     and not coalesce((select public.is_premium(u) from public.community_users u where u.id = new.sender_id), false) then
    select count(*) into v_today from public.room_messages m
     join public.rooms r on r.id = m.room_id and r.kind = 'channel'
     where m.sender_id = new.sender_id and m.created_at >= date_trunc('day', now());
    if v_today >= 50 then raise exception 'daily_limit'; end if;
  end if;
  return new;
end $function$;
