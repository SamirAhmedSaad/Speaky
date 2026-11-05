-- Free users: N private messages per rolling 24 hours (N = app_settings.free_private_messages, default 25).
insert into public.app_settings (key, value) values ('free_private_messages', '25'::jsonb)
on conflict (key) do nothing;

create or replace function public.my_free_dm_status() returns json
language sql stable security definer
set search_path = ''
as $$
  with sent as (
    select m.created_at
    from public.room_messages m
    join public.rooms r on r.id = m.room_id
    where m.sender_id = auth.uid() and r.kind = 'direct' and m.created_at > now() - interval '24 hours'
  )
  select json_build_object(
    'used', (select count(*) from sent),
    'limit', coalesce((select (value #>> '{}')::int from public.app_settings where key = 'free_private_messages'), 25),
    -- When the oldest message in the window drops out, one message becomes free again.
    'wait_ms', coalesce((select (extract(epoch from (min(created_at) + interval '24 hours' - now())) * 1000)::bigint from sent), 0)
  )
$$;
revoke all on function public.my_free_dm_status() from public, anon;
grant execute on function public.my_free_dm_status() to authenticated;
