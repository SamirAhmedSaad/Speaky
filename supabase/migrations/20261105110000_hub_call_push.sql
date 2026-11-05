-- Push "X started a voice call" to Hub members when a call starts (needs 20261105100000_hub_voice_calls.sql).
-- Same audience as Hub message alerts (hub_notify on), minus the starter and anyone banned.
create or replace function public.call_push_recipients(p_call uuid)
returns table(token text, user_id uuid, starter_nickname text, room_id uuid, room_name text)
language sql stable security definer
set search_path = ''
as $$
  select t.token, t.user_id, coalesce(s.nickname, 'Someone'), c.room_id, coalesce(r.name, 'Speaky Hub')
    from public.voice_calls c
    join public.rooms r on r.id = c.room_id
    left join public.community_users s on s.id = c.started_by
    join public.community_users cu on cu.id <> c.started_by and cu.hub_notify
    join public.device_tokens t on t.user_id = cu.id
   where c.id = p_call
     and c.ended_at is null
     and not public.is_banned(cu.id)
     and not private.voice_banned(cu.id)
$$;
revoke all on function public.call_push_recipients(uuid) from public, anon, authenticated;
grant execute on function public.call_push_recipients(uuid) to service_role;

create or replace function public.on_call_push()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  perform net.http_post(
    url := 'https://bkivjjvgzsqgbkfubjtx.supabase.co/functions/v1/call-notify',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-push-secret', (select value from private.app_secrets where name = 'push_secret')),
    body := jsonb_build_object('call_id', new.id),
    timeout_milliseconds := 5000);
  return new;
end $$;

drop trigger if exists trg_call_push on public.voice_calls;
create trigger trg_call_push after insert on public.voice_calls
  for each row execute function public.on_call_push();
