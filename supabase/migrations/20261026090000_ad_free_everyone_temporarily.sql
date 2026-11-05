-- Applied via MCP. TEMPORARY: every signed-in user is ad-free.
-- To undo, re-apply 20261025120000_ad_free_not_admins.sql (only users in ad_free_users).
create or replace function public.my_ad_free() returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null;
$$;
