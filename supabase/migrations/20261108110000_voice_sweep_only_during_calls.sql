-- The voice-calls-sweep job runs only while a Hub call is live: a new call schedules it, and the
-- sweep removes itself once no call is left. Saves running it every minute all day.

create or replace function private.voice_calls_sweep()
returns void
language plpgsql security definer
set search_path = ''
as $$
declare
  r record;
begin
  for r in select call_id, user_id from public.voice_call_members where last_seen < now() - interval '90 seconds' loop
    perform private.voice_drop_member(r.call_id, r.user_id, 'empty');
  end loop;
  for r in select id from public.voice_calls
            where ended_at is null and peak_members <= 1 and started_at < now() - interval '5 minutes' loop
    perform private.voice_end_call(r.id, 'nobody_joined');
  end loop;
  -- No live call left: stop until the next call starts.
  if not exists (select 1 from public.voice_calls where ended_at is null)
     and exists (select 1 from cron.job where jobname = 'voice-calls-sweep') then
    perform cron.unschedule('voice-calls-sweep');
  end if;
end $$;

create or replace function private.voice_sweep_start()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  if not exists (select 1 from cron.job where jobname = 'voice-calls-sweep') then
    perform cron.schedule('voice-calls-sweep', '* * * * *', 'select private.voice_calls_sweep()');
  end if;
  return new;
end $$;

drop trigger if exists trg_voice_sweep_start on public.voice_calls;
create trigger trg_voice_sweep_start after insert on public.voice_calls
  for each row execute function private.voice_sweep_start();

-- Stop it now if no call is live.
select cron.unschedule('voice-calls-sweep')
 where exists (select 1 from cron.job where jobname = 'voice-calls-sweep')
   and not exists (select 1 from public.voice_calls where ended_at is null);
