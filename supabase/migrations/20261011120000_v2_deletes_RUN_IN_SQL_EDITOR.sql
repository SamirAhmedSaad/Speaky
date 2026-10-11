-- NOT APPLIED: the MCP tool declines migrations that delete data.
-- Paste into Supabase Dashboard → SQL Editor → Run.
-- (Parts 1–3 of community v2 were applied via MCP as v2_part1_columns,
--  v2_part2_functions and v2_part3_policies_push; see Supabase migration history.)

create or replace function public.remove_group_member(p_room uuid, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v_my text := public.my_group_role(p_room); v_their text;
begin
  select role into v_their from public.room_members where room_id = p_room and user_id = p_user;
  if v_my is null or v_my = 'member' or v_their is null or v_their = 'owner'
     or (v_my = 'admin' and v_their = 'admin') then
    raise exception 'not allowed';
  end if;
  delete from public.room_members where room_id = p_room and user_id = p_user;
end $$;

create or replace function public.delete_group(p_room uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if coalesce(public.my_group_role(p_room), '') <> 'owner' then raise exception 'not allowed'; end if;
  delete from public.rooms where id = p_room and kind = 'group';
end $$;

-- When the owner leaves (or deletes their account), hand the group to the oldest admin/member
create or replace function public.on_room_member_change() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_room uuid := coalesce(new.room_id, old.room_id);
begin
  if tg_op = 'DELETE' and old.role = 'owner' then
    update public.room_members set role = 'owner'
     where (room_id, user_id) = (
       select room_id, user_id from public.room_members where room_id = v_room
        order by (role = 'admin') desc, joined_at asc limit 1);
  end if;
  update public.rooms
     set member_count = (select count(*) from public.room_members where room_id = v_room)
   where id = v_room;
  return null;
end $$;

-- Deletes the caller's own account; cascades remove profile, memberships, messages, blocks, tokens
create or replace function public.delete_my_account() returns void
language plpgsql security definer set search_path = '' as $$
declare v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'not signed in'; end if;
  delete from auth.users where id = v_me;
end $$;

-- Admin moderation:  select private.ban_user('<user id>', 'reason', true);  /  select private.unban_user('<user id>');
create or replace function private.ban_user(p_user uuid, p_reason text default '', p_delete_messages boolean default false)
returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.community_users set banned_at = now(), ban_reason = p_reason where id = p_user;
  if p_delete_messages then delete from public.room_messages where sender_id = p_user; end if;
  update public.user_reports set status = 'actioned' where reported_user_id = p_user and status = 'open';
end $$;
create or replace function private.unban_user(p_user uuid) returns void
language sql security definer set search_path = '' as $$
  update public.community_users set banned_at = null, ban_reason = null where id = p_user;
$$;

revoke execute on function public.remove_group_member(uuid, uuid), public.delete_group(uuid),
  public.delete_my_account() from public, anon;
grant execute on function public.remove_group_member(uuid, uuid), public.delete_group(uuid),
  public.delete_my_account() to authenticated;
