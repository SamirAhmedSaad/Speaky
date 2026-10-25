-- Applied via MCP. Server-controlled ad-free accounts (no Google Play purchase needed).
-- Admins are always ad-free; anyone else can be added here from the dashboard / SQL editor:
--   insert into public.ad_free_users (user_id, note) values ('<community user id>', 'reason');
create table if not exists public.ad_free_users (
  user_id uuid primary key references auth.users(id) on delete cascade,
  note text,
  created_at timestamptz not null default now()
);
-- RLS on with no policies: the app can't read or change this table directly.
alter table public.ad_free_users enable row level security;

create or replace function public.my_ad_free() returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null and (
    public.is_community_admin()
    or exists (select 1 from public.ad_free_users where user_id = (select auth.uid()))
  );
$$;
revoke execute on function public.my_ad_free() from public, anon;
grant execute on function public.my_ad_free() to authenticated;
