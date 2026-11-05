-- Free AI corrections left for the signed-in user (rolling 24 hours), for the chat's counter.
create or replace function public.my_ai_status() returns json
language sql stable security definer
set search_path = ''
as $$
  with used as (
    select count(*) as n from public.ai_requests
    where user_id = auth.uid() and created_at > now() - interval '24 hours'
  )
  select json_build_object(
    'used', (select n from used),
    'limit', coalesce((select (value #>> '{}')::int from public.app_settings where key = 'free_ai_corrections'), 3)
  )
$$;
revoke all on function public.my_ai_status() from public, anon;
grant execute on function public.my_ai_status() to authenticated;
