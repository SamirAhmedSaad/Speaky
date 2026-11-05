-- Applied via MCP. Premium features (unlimited lessons, private chat, who's online) only for accounts
-- listed in ad_free_users; Google Play subscribers get them in the app. Ads are off for everyone
-- via app_settings.ads_off, independently of this.
create or replace function public.my_ad_free() returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null
    and exists (select 1 from public.ad_free_users where user_id = (select auth.uid()));
$$;
