-- Admin "Subscribers" screen: who has Premium (Google Play or granted), who lapsed, and grant/remove.

-- When the current Google Play run of Premium started, and when it last ended.
alter table public.community_users add column if not exists premium_since timestamptz;
alter table public.community_users add column if not exists premium_ended_at timestamptz;
update public.community_users set premium_since = coalesce(premium_since, now())
 where premium_until > now();

-- Who granted it.
alter table public.ad_free_users add column if not exists granted_by uuid;

create or replace function public.set_my_premium(p_on boolean)
returns void
language sql security definer
set search_path = ''
as $$
  update public.community_users
     set premium_since = case
           when not p_on then premium_since
           when premium_until is null or premium_until < now() then now()
           else coalesce(premium_since, now()) end,
         premium_ended_at = case
           when p_on then null
           when premium_until is not null then least(now(), premium_until)
           else premium_ended_at end,
         premium_until = case when p_on then now() + interval '3 days' end
   where id = (select auth.uid());
$$;
revoke all on function public.set_my_premium(boolean) from public, anon;
grant execute on function public.set_my_premium(boolean) to authenticated;

-- Everyone with Premium now, plus Google Play subscriptions that ended in the last 90 days.
-- source: 'play' | 'granted' | 'both' | 'lapsed'.
create or replace function public.admin_premium_members()
returns table (
  id uuid, nickname text, photo_url text, gender text, level text, email text,
  last_seen timestamptz, source text, premium_since timestamptz, play_confirmed_at timestamptz,
  ended_at timestamptz, granted_at timestamptz, note text, granted_by_name text
)
language plpgsql stable security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'admins only'; end if;
  return query
  select u.id, u.nickname, u.photo_url, u.gender, u.level, au.email::text, u.last_seen,
         case
           when coalesce(u.premium_until > now(), false) and a.user_id is not null then 'both'
           when coalesce(u.premium_until > now(), false) then 'play'
           when a.user_id is not null then 'granted'
           else 'lapsed' end,
         u.premium_since,
         case when u.premium_until > now() then u.premium_until - interval '3 days' end,
         u.premium_ended_at,
         a.created_at, a.note, g.nickname
    from public.community_users u
    left join public.ad_free_users a on a.user_id = u.id
    left join public.community_users g on g.id = a.granted_by
    left join auth.users au on au.id = u.id
   where coalesce(u.premium_until > now(), false)
      or a.user_id is not null
      or u.premium_ended_at > now() - interval '90 days'
   order by coalesce(u.premium_since, a.created_at, u.premium_ended_at) desc nulls last;
end;
$$;
revoke all on function public.admin_premium_members() from public, anon;
grant execute on function public.admin_premium_members() to authenticated;

-- People to grant Premium to: matches nickname or email; excludes those already granted.
create or replace function public.admin_premium_search(p_query text)
returns table (id uuid, nickname text, photo_url text, gender text, level text, email text, premium boolean)
language plpgsql stable security definer
set search_path = ''
as $$
declare q text := lower(trim(coalesce(p_query, '')));
begin
  if not public.is_community_admin() then raise exception 'admins only'; end if;
  if length(q) < 2 then return; end if;
  return query
  select u.id, u.nickname, u.photo_url, u.gender, u.level, au.email::text,
         coalesce(u.premium_until > now(), false)
    from public.community_users u
    left join auth.users au on au.id = u.id
   where not exists (select 1 from public.ad_free_users a where a.user_id = u.id)
     and (lower(u.nickname) like '%' || q || '%' or lower(au.email) like '%' || q || '%')
   order by u.last_seen desc nulls last
   limit 20;
end;
$$;
revoke all on function public.admin_premium_search(text) from public, anon;
grant execute on function public.admin_premium_search(text) to authenticated;

create or replace function public.admin_grant_premium(p_user uuid, p_note text default null)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'admins only'; end if;
  insert into public.ad_free_users (user_id, note, granted_by)
  values (p_user, nullif(trim(p_note), ''), (select auth.uid()))
  on conflict (user_id) do nothing;
end;
$$;
revoke all on function public.admin_grant_premium(uuid, text) from public, anon;
grant execute on function public.admin_grant_premium(uuid, text) to authenticated;

create or replace function public.admin_revoke_premium(p_user uuid)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'admins only'; end if;
  delete from public.ad_free_users where user_id = p_user;
end;
$$;
revoke all on function public.admin_revoke_premium(uuid) from public, anon;
grant execute on function public.admin_revoke_premium(uuid) to authenticated;
