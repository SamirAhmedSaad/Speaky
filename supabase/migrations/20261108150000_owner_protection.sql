-- Roles: the owner accounts (private.monitor_hidden_users: Samir and Ahmed Saad) are the admins;
-- everyone else in private.community_admins is a moderator with the same tools, except they can't:
--   monitor the owner's private chats (20261108140000), ban the owner, delete the owner's messages,
--   remove the owner's photo, ban the owner from calls, or add/remove moderators.
-- Additive: same function signatures, only checks added.

create or replace function private.is_owner(p_user uuid)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (select 1 from private.monitor_hidden_users where user_id = p_user);
$$;

-- A moderator acting on the owner is refused.
create or replace function private.guard_owner(p_target uuid)
returns void
language plpgsql stable security definer
set search_path = ''
as $$
begin
  if private.is_owner(p_target) and not private.is_owner((select auth.uid())) then
    raise exception 'not allowed';
  end if;
end $$;

create or replace function public.admin_ban(p_user uuid, p_reason text, p_remove_messages boolean)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  perform private.guard_owner(p_user);
  perform private.ban_user(p_user, p_reason, p_remove_messages);
end $$;

create or replace function public.admin_delete_message(p_message uuid)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'admins only'; end if;
  perform private.guard_owner((select sender_id from public.room_messages where id = p_message));
  delete from public.room_messages where id = p_message;
end $$;

create or replace function public.admin_reject_photo(p_user uuid, p_reason text default null)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'admins only'; end if;
  perform private.guard_owner(p_user);
  update public.community_users
     set photo_url = null, photo_status = 'rejected', photo_reason = coalesce(p_reason, 'admin'), photo_checked_at = now()
   where id = p_user;
end $$;

create or replace function public.voice_call_admin_ban(p_user uuid, p_days integer)
returns void
language plpgsql security definer
set search_path = ''
as $$
declare
  m record;
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  perform private.guard_owner(p_user);
  insert into public.voice_call_bans (user_id, until, banned_by)
  values (p_user, case when p_days is null then null else now() + make_interval(days => p_days) end, (select auth.uid()))
  on conflict (user_id) do update set until = excluded.until, banned_by = excluded.banned_by, created_at = now();
  for m in select call_id from public.voice_call_members where user_id = p_user loop
    perform private.voice_drop_member(m.call_id, p_user, 'empty');
  end loop;
end $$;

-- Only the owner adds or removes moderators.
create or replace function public.admin_set_moderator(p_user uuid, p_on boolean)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not private.is_owner((select auth.uid())) then raise exception 'not allowed'; end if;
  if p_on then
    if not exists (select 1 from public.community_users where id = p_user) then raise exception 'not allowed'; end if;
    insert into private.community_admins (user_id) values (p_user) on conflict do nothing;
  else
    if private.is_owner(p_user) then raise exception 'not allowed'; end if;
    if (select count(*) from private.community_admins) <= 1 then raise exception 'last_admin'; end if;
    delete from private.community_admins where user_id = p_user;
  end if;
end $$;

-- The app asks this to show owner-only buttons (add/remove moderator).
create or replace function public.is_community_owner()
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select private.is_owner((select auth.uid()));
$$;
revoke all on function public.is_community_owner() from public, anon;
grant execute on function public.is_community_owner() to authenticated;
