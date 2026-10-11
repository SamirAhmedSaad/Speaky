-- Automatic cleanup: voice clips capped at 1 GB, chat messages capped at 500k rows.
-- Both prune oldest-first from 90% down to 80% of the cap, every 15 minutes.
create extension if not exists pg_cron;
create extension if not exists pg_net;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table private.app_secrets (name text primary key, value text not null);
insert into private.app_secrets (name, value)
values ('cleanup_secret', encode(extensions.gen_random_bytes(32), 'hex'));

-- Tunables (edit these rows to change limits)
create table private.cleanup_config (
  key text primary key,
  value bigint not null
);
insert into private.cleanup_config values
  ('voice_cap_bytes',      1073741824),   -- 1 GB
  ('voice_high_pct',       90),           -- start pruning above 90%
  ('voice_low_pct',        80),           -- prune down to 80%
  ('message_cap_rows',     500000),
  ('message_high_pct',     90),
  ('message_low_pct',      80);

create function private.cfg(p_key text) returns bigint
language sql stable set search_path = '' as $$
  select value from private.cleanup_config where key = p_key;
$$;

-- Used by the prune-storage Edge Function (service role only)
create function public.verify_cleanup_secret(p_secret text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from private.app_secrets where name = 'cleanup_secret' and value = p_secret);
$$;

-- Oldest voice files to delete (final version in 20261011084846)
create function public.voice_prune_candidates() returns setof text
language plpgsql stable security definer set search_path = '' as $$
begin
  return;
end $$;

-- After the Edge Function removes files, keep the messages but mark the clip as expired
create function public.mark_voice_expired(p_paths text[]) returns int
language sql security definer set search_path = '' as $$
  with u as (
    update public.room_messages set voice_path = null
     where voice_path = any(p_paths) returning 1
  ) select count(*)::int from u;
$$;

-- Messages: keep the table under the row cap by deleting the oldest rows
create function private.prune_messages() returns int
language plpgsql security definer set search_path = '' as $$
declare
  v_cap bigint := private.cfg('message_cap_rows');
  v_count bigint;
  v_excess bigint;
  v_deleted int := 0;
begin
  select count(*) into v_count from public.room_messages;
  if v_count > v_cap * private.cfg('message_high_pct') / 100 then
    v_excess := v_count - v_cap * private.cfg('message_low_pct') / 100;
    with old as (
      select id from public.room_messages order by created_at asc limit v_excess
    ), d as (
      delete from public.room_messages m using old where m.id = old.id returning 1
    ) select count(*) into v_deleted from d;
  end if;
  return v_deleted;
end $$;

revoke execute on function public.verify_cleanup_secret(text), public.voice_prune_candidates(),
  public.mark_voice_expired(text[]) from public, anon, authenticated;
grant execute on function public.verify_cleanup_secret(text), public.voice_prune_candidates(),
  public.mark_voice_expired(text[]) to service_role;

-- Schedules
select cron.schedule('prune-messages', '*/15 * * * *', $$ select private.prune_messages(); $$);
select cron.schedule('prune-voice-storage', '*/15 * * * *', $$
  select net.http_post(
    url := 'https://bkivjjvgzsqgbkfubjtx.supabase.co/functions/v1/prune-storage',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cleanup-secret', (select value from private.app_secrets where name = 'cleanup_secret')
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 30000
  );
$$);
