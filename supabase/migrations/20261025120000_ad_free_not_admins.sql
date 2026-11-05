-- Applied via MCP. Admins are no longer ad-free automatically: only users listed in ad_free_users.
create or replace function public.my_ad_free() returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null
    and exists (select 1 from public.ad_free_users where user_id = (select auth.uid()));
$$;
