-- Hub voice calls: one live call per room, up to 10 people plus 5 Premium seats.
-- Audio goes through Agora (or LiveKit when Agora fails at the start); this only keeps who is in
-- the call. Every change goes through the security definer functions below; clients can only read.

create table if not exists public.voice_calls (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms(id) on delete cascade,
  provider text not null default 'agora' check (provider in ('agora', 'livekit')),
  started_by uuid not null references public.community_users(id) on delete cascade,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  -- 'empty', 'nobody_joined', 'admin', 'calls_off'
  end_reason text,
  -- Most people at once; 1 means nobody joined the starter.
  peak_members int not null default 1,
  -- Sum of everyone's time in the call, for the admin's usage numbers.
  member_seconds bigint not null default 0
);
create unique index if not exists voice_calls_one_live on public.voice_calls (room_id) where ended_at is null;

create table if not exists public.voice_call_members (
  call_id uuid not null references public.voice_calls(id) on delete cascade,
  user_id uuid not null references public.community_users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  muted boolean not null default false,
  premium_seat boolean not null default false,
  primary key (call_id, user_id)
);

-- until null = banned for good.
create table if not exists public.voice_call_bans (
  user_id uuid primary key references public.community_users(id) on delete cascade,
  until timestamptz,
  banned_by uuid references public.community_users(id) on delete set null,
  created_at timestamptz not null default now()
);

alter table public.voice_calls enable row level security;
alter table public.voice_call_members enable row level security;
alter table public.voice_call_bans enable row level security;

create policy "voice calls are readable" on public.voice_calls for select to authenticated using (true);
create policy "voice call members are readable" on public.voice_call_members for select to authenticated using (true);
create policy "own voice ban or admin" on public.voice_call_bans for select to authenticated
  using (user_id = (select auth.uid()) or public.is_community_admin());

insert into public.app_settings (key, value) values ('voice_calls_on', 'true'::jsonb) on conflict (key) do nothing;

-- Live updates for the Hub banner, the call grid and the admin switch.
do $$
begin
  begin alter publication supabase_realtime add table public.voice_calls; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.voice_call_members; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.app_settings; exception when duplicate_object then null; end;
end $$;

create or replace function private.voice_calls_on() returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce((select value = 'true'::jsonb from public.app_settings where key = 'voice_calls_on'), true);
$$;

create or replace function private.voice_banned(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.voice_call_bans b where b.user_id = p_user and (b.until is null or b.until > now()));
$$;

-- Removes one member, counting their time; ends the call when nobody is left.
create or replace function private.voice_drop_member(p_call uuid, p_user uuid, p_reason text default 'empty') returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_joined timestamptz;
begin
  delete from public.voice_call_members where call_id = p_call and user_id = p_user returning joined_at into v_joined;
  if v_joined is null then return; end if;
  update public.voice_calls set member_seconds = member_seconds + extract(epoch from now() - v_joined)::bigint where id = p_call;
  if not exists (select 1 from public.voice_call_members where call_id = p_call) then
    update public.voice_calls
       set ended_at = now(),
           end_reason = case when peak_members <= 1 and p_reason = 'empty' then 'nobody_joined' else p_reason end
     where id = p_call and ended_at is null;
  end if;
end $$;

create or replace function private.voice_end_call(p_call uuid, p_reason text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  m record;
begin
  for m in select user_id from public.voice_call_members where call_id = p_call loop
    perform private.voice_drop_member(p_call, m.user_id, p_reason);
  end loop;
  update public.voice_calls set ended_at = now(), end_reason = p_reason where id = p_call and ended_at is null;
end $$;

-- Takes a seat in a live call. Returns the call row as json; raises 'full', 'banned', 'calls_off'.
create or replace function private.voice_take_seat(p_call uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_me uuid := (select auth.uid());
  v_call public.voice_calls;
  v_count int;
  v_premium_count int;
  v_premium boolean;
  v_seat boolean := false;
begin
  select * into v_call from public.voice_calls where id = p_call and ended_at is null for update;
  if v_call.id is null then raise exception 'ended'; end if;
  if exists (select 1 from public.voice_call_members where call_id = p_call and user_id = v_me) then
    update public.voice_call_members set last_seen = now() where call_id = p_call and user_id = v_me;
  else
    select count(*), count(*) filter (where premium_seat) into v_count, v_premium_count
      from public.voice_call_members where call_id = p_call;
    if v_count - v_premium_count >= 10 then
      select public.is_premium(u) into v_premium from public.community_users u where u.id = v_me;
      if not coalesce(v_premium, false) then raise exception 'full'; end if;
      if v_premium_count >= 5 then raise exception 'premium_full'; end if;
      v_seat := true;
    end if;
    insert into public.voice_call_members (call_id, user_id, premium_seat) values (p_call, v_me, v_seat);
    update public.voice_calls
       set peak_members = greatest(peak_members, (select count(*) from public.voice_call_members where call_id = p_call))
     where id = p_call;
  end if;
  return jsonb_build_object('call_id', p_call, 'provider', v_call.provider, 'premium_seat', v_seat);
end $$;

-- Starts the room's call, or joins it when one is already live.
create or replace function public.voice_call_start(p_room uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_me uuid := (select auth.uid());
  v_call uuid;
begin
  if v_me is null then raise exception 'not_signed_in'; end if;
  if not private.voice_calls_on() then raise exception 'calls_off'; end if;
  if private.voice_banned(v_me) or public.is_banned(v_me) then raise exception 'banned'; end if;
  select id into v_call from public.voice_calls where room_id = p_room and ended_at is null;
  if v_call is null then
    begin
      insert into public.voice_calls (room_id, started_by) values (p_room, v_me) returning id into v_call;
    exception when unique_violation then
      select id into v_call from public.voice_calls where room_id = p_room and ended_at is null;
    end;
  end if;
  return private.voice_take_seat(v_call);
end $$;

create or replace function public.voice_call_join(p_call uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_me uuid := (select auth.uid());
begin
  if v_me is null then raise exception 'not_signed_in'; end if;
  if not private.voice_calls_on() then raise exception 'calls_off'; end if;
  if private.voice_banned(v_me) or public.is_banned(v_me) then raise exception 'banned'; end if;
  return private.voice_take_seat(p_call);
end $$;

create or replace function public.voice_call_leave(p_call uuid) returns void
language sql security definer set search_path = '' as $$
  select private.voice_drop_member(p_call, (select auth.uid()), 'empty');
$$;

-- Every 30 s while in a call. False: the call ended or this user was removed, so hang up.
create or replace function public.voice_call_heartbeat(p_call uuid) returns boolean
language plpgsql security definer set search_path = '' as $$
begin
  update public.voice_call_members set last_seen = now()
   where call_id = p_call and user_id = (select auth.uid())
     and exists (select 1 from public.voice_calls c where c.id = p_call and c.ended_at is null);
  return found;
end $$;

create or replace function public.voice_call_set_muted(p_call uuid, p_muted boolean) returns void
language sql security definer set search_path = '' as $$
  update public.voice_call_members set muted = p_muted where call_id = p_call and user_id = (select auth.uid());
$$;

-- Agora failed for the starter: move the call to LiveKit while they are still alone in it.
create or replace function public.voice_call_use_fallback(p_call uuid) returns text
language plpgsql security definer set search_path = '' as $$
begin
  update public.voice_calls set provider = 'livekit'
   where id = p_call and ended_at is null and started_by = (select auth.uid())
     and (select count(*) from public.voice_call_members m where m.call_id = p_call) <= 1;
  return (select provider from public.voice_calls where id = p_call);
end $$;

-- ---------- Admin ----------

create or replace function public.voice_call_admin_end(p_call uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  perform private.voice_end_call(p_call, 'admin');
end $$;

create or replace function public.voice_call_admin_mute(p_call uuid, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  update public.voice_call_members set muted = true where call_id = p_call and user_id = p_user;
end $$;

create or replace function public.voice_call_admin_remove(p_call uuid, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  perform private.voice_drop_member(p_call, p_user, 'empty');
end $$;

-- p_days null = forever. Also takes them out of any live call.
create or replace function public.voice_call_admin_ban(p_user uuid, p_days int) returns void
language plpgsql security definer set search_path = '' as $$
declare
  m record;
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  insert into public.voice_call_bans (user_id, until, banned_by)
  values (p_user, case when p_days is null then null else now() + make_interval(days => p_days) end, (select auth.uid()))
  on conflict (user_id) do update set until = excluded.until, banned_by = excluded.banned_by, created_at = now();
  for m in select call_id from public.voice_call_members where user_id = p_user loop
    perform private.voice_drop_member(m.call_id, p_user, 'empty');
  end loop;
end $$;

create or replace function public.voice_call_admin_unban(p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  delete from public.voice_call_bans where user_id = p_user;
end $$;

-- Turning calls off: p_end_live true ends a live call now, false lets it finish.
create or replace function public.voice_calls_set_enabled(p_on boolean, p_end_live boolean default false) returns void
language plpgsql security definer set search_path = '' as $$
declare
  c record;
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  insert into public.app_settings (key, value) values ('voice_calls_on', to_jsonb(p_on))
  on conflict (key) do update set value = excluded.value, updated_at = now();
  if not p_on and p_end_live then
    for c in select id from public.voice_calls where ended_at is null loop
      perform private.voice_end_call(c.id, 'calls_off');
    end loop;
  end if;
end $$;

-- This month's numbers for the admin screen.
create or replace function public.voice_call_admin_stats() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not_admin'; end if;
  return (
    select jsonb_build_object(
      'calls', count(*),
      'agora_minutes', coalesce(sum(member_seconds) filter (where provider = 'agora'), 0) / 60,
      'livekit_minutes', coalesce(sum(member_seconds) filter (where provider = 'livekit'), 0) / 60,
      'fallback_calls', count(*) filter (where provider = 'livekit')
    )
    from public.voice_calls where started_at >= date_trunc('month', now())
  );
end $$;

do $$
declare
  f text;
begin
  foreach f in array array[
    'voice_call_start(uuid)', 'voice_call_join(uuid)', 'voice_call_leave(uuid)', 'voice_call_heartbeat(uuid)',
    'voice_call_set_muted(uuid, boolean)', 'voice_call_use_fallback(uuid)', 'voice_call_admin_end(uuid)',
    'voice_call_admin_mute(uuid, uuid)', 'voice_call_admin_remove(uuid, uuid)', 'voice_call_admin_ban(uuid, int)',
    'voice_call_admin_unban(uuid)', 'voice_calls_set_enabled(boolean, boolean)', 'voice_call_admin_stats()'
  ] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;

-- Every minute: drop members whose app stopped answering (90 s), and close a call nobody joined
-- within 5 minutes.
create or replace function private.voice_calls_sweep() returns void
language plpgsql security definer set search_path = '' as $$
declare
  r record;
begin
  for r in select call_id, user_id from public.voice_call_members where last_seen < now() - interval '90 seconds' loop
    perform private.voice_drop_member(r.call_id, r.user_id, 'empty');
  end loop;
  for r in select id from public.voice_calls
            where ended_at is null and peak_members <= 1 and started_at < now() - interval '5 minutes' loop
    perform private.voice_end_call(r.id, 'nobody_joined');
  end loop;
end $$;

select cron.schedule('voice-calls-sweep', '* * * * *', $$select private.voice_calls_sweep()$$);
