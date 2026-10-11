-- Run in the Supabase SQL editor (contains a DELETE, which the MCP connection declines).
-- In-app moderator management for admins.
create or replace function public.admin_moderators()
returns table (user_id uuid, nickname text, photo_url text, level text)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  return query
    select a.user_id, coalesce(cu.nickname, ''), cu.photo_url, coalesce(cu.level, '')
      from private.community_admins a
      left join public.community_users cu on cu.id = a.user_id
     order by cu.nickname;
end $$;

create or replace function public.admin_set_moderator(p_user uuid, p_on boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  if p_on then
    if not exists (select 1 from public.community_users where id = p_user) then raise exception 'not allowed'; end if;
    insert into private.community_admins (user_id) values (p_user) on conflict do nothing;
  else
    if (select count(*) from private.community_admins) <= 1 then raise exception 'last_admin'; end if;
    delete from private.community_admins where user_id = p_user;
  end if;
end $$;

revoke execute on function public.admin_moderators(), public.admin_set_moderator(uuid, boolean) from public, anon;
grant execute on function public.admin_moderators(), public.admin_set_moderator(uuid, boolean) to authenticated;
