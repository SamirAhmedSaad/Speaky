-- ===== Blocks =====
create table public.user_blocks (
  blocker_id uuid not null references public.community_users(id) on delete cascade,
  blocked_id uuid not null references public.community_users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index idx_user_blocks_blocked on public.user_blocks (blocked_id);
alter table public.user_blocks enable row level security;

create policy "Users see their own blocks" on public.user_blocks
  for select to authenticated using (blocker_id = (select auth.uid()));
create policy "Users block others" on public.user_blocks
  for insert to authenticated with check (blocker_id = (select auth.uid()));
create policy "Users unblock others" on public.user_blocks
  for delete to authenticated using (blocker_id = (select auth.uid()));

-- true if either user blocked the other
create function public.is_blocked_between(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.user_blocks
                 where (blocker_id = a and blocked_id = b) or (blocker_id = b and blocked_id = a));
$$;

-- Private messages are refused when either side has blocked the other
create or replace function public.can_post_room(p_room uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.rooms r
     where r.id = p_room
       and (r.kind = 'channel'
            or (exists (select 1 from public.room_members m
                         where m.room_id = r.id and m.user_id = (select auth.uid()))
                and (r.kind <> 'direct'
                     or not exists (select 1 from public.room_members o
                                     where o.room_id = r.id and o.user_id <> (select auth.uid())
                                       and public.is_blocked_between(o.user_id, (select auth.uid())))))));
$$;

create or replace function public.get_or_create_dm(p_other uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v_me uuid := auth.uid();
  v_key text;
  v_room uuid;
begin
  if v_me is null then raise exception 'not signed in'; end if;
  if p_other = v_me then raise exception 'cannot message yourself'; end if;
  if not exists (select 1 from public.community_users where id = p_other) then
    raise exception 'user not found';
  end if;
  v_key := least(v_me::text, p_other::text) || ':' || greatest(v_me::text, p_other::text);
  select id into v_room from public.rooms where dm_key = v_key;
  if v_room is null then
    -- Only an existing chat can be reopened once blocked; new ones can't be started
    if public.is_blocked_between(v_me, p_other) then
      raise exception 'blocked' using errcode = 'P0001';
    end if;
    insert into public.rooms (kind, dm_key) values ('direct', v_key)
    on conflict (dm_key) do nothing;
    select id into v_room from public.rooms where dm_key = v_key;
    insert into public.room_members (room_id, user_id)
    values (v_room, v_me), (v_room, p_other)
    on conflict do nothing;
  end if;
  return v_room;
end $$;

-- ===== Reports (reviewed by the app owner in the dashboard) =====
create table public.user_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.community_users(id) on delete cascade,
  reported_user_id uuid not null references public.community_users(id) on delete cascade,
  message_id uuid references public.room_messages(id) on delete set null,
  room_id uuid references public.rooms(id) on delete set null,
  reason text not null check (reason in ('spam', 'harassment', 'inappropriate', 'scam', 'other')),
  details text not null default '' check (char_length(details) <= 500),
  -- snapshot so the evidence survives message cleanup
  message_snapshot text not null default '',
  status text not null default 'open' check (status in ('open', 'reviewed', 'actioned', 'dismissed')),
  created_at timestamptz not null default now()
);
create index idx_user_reports_status on public.user_reports (status, created_at desc);
create index idx_user_reports_reported on public.user_reports (reported_user_id);
create index idx_user_reports_reporter on public.user_reports (reporter_id);
create index idx_user_reports_message on public.user_reports (message_id);
create index idx_user_reports_room on public.user_reports (room_id);
alter table public.user_reports enable row level security;

create policy "Users file reports as themselves" on public.user_reports
  for insert to authenticated
  with check (reporter_id = (select auth.uid()) and reported_user_id <> (select auth.uid()));
create policy "Users see their own reports" on public.user_reports
  for select to authenticated using (reporter_id = (select auth.uid()));

-- Admin view: who is who, with login email and report counts (service role / dashboard only)
create view private.community_user_overview as
select cu.id, cu.nickname, cu.gender, u.email,
       u.raw_app_meta_data->>'provider' as provider,
       u.created_at as signed_up_at, u.last_sign_in_at, cu.last_seen,
       (select count(*) from public.user_reports r where r.reported_user_id = cu.id) as times_reported,
       (select count(*) from public.user_blocks b where b.blocked_id = cu.id) as times_blocked,
       (select count(*) from public.room_messages m where m.sender_id = cu.id) as messages_sent
  from public.community_users cu
  join auth.users u on u.id = cu.id;
revoke all on private.community_user_overview from public, anon, authenticated;

revoke execute on function public.is_blocked_between(uuid, uuid) from public, anon;
grant execute on function public.is_blocked_between(uuid, uuid) to authenticated;
