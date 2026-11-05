-- Trusted time for the app (free-lesson limit), so changing the phone's clock doesn't matter.
create or replace function public.server_now() returns bigint
language sql stable
set search_path = ''
as $$ select (extract(epoch from now()) * 1000)::bigint $$;
grant execute on function public.server_now() to anon, authenticated;
