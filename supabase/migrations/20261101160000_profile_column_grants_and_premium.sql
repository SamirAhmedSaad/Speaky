-- Users could update every column of their own profile, including banned_at (a banned user could unban
-- themselves). They may now only write their own profile fields; bans and bookkeeping go through
-- security-definer functions.
revoke update, insert on public.community_users from anon, authenticated;
grant update (id, nickname, gender, level, photo_url, terms_accepted_at, last_seen) on public.community_users to authenticated;
grant insert (id, nickname, gender, level, photo_url, terms_accepted_at) on public.community_users to authenticated;

-- Premium badge: the app reports its Google Play subscription; it lapses on its own unless renewed.
alter table public.community_users add column if not exists premium_until timestamptz;

create or replace function public.set_my_premium(p_on boolean)
returns void
language sql security definer
set search_path = ''
as $$
  update public.community_users
     set premium_until = case when p_on then now() + interval '3 days' end
   where id = (select auth.uid());
$$;
revoke all on function public.set_my_premium(boolean) from public, anon;
grant execute on function public.set_my_premium(boolean) to authenticated;

-- Computed field (select "is_premium"): reported Premium, or granted on the server.
create or replace function public.is_premium(u public.community_users)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select coalesce(u.premium_until > now(), false)
      or exists (select 1 from public.ad_free_users a where a.user_id = u.id);
$$;
revoke all on function public.is_premium(public.community_users) from public;
grant execute on function public.is_premium(public.community_users) to anon, authenticated;
