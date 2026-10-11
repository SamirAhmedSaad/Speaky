-- Applied via MCP. Replies, reactions, profile level/photo, in-app admin moderation.
-- To make yourself an admin (after signing in once):
--   insert into private.community_admins (user_id)
--   select id from auth.users where email = 'you@example.com';

-- ===== Replies =====
alter table public.room_messages
  add column if not exists reply_to_id uuid references public.room_messages(id) on delete set null;
create index if not exists idx_room_messages_reply on public.room_messages (reply_to_id);

-- ===== Reactions =====
create table public.message_reactions (
  message_id uuid not null references public.room_messages(id) on delete cascade,
  room_id uuid not null references public.rooms(id) on delete cascade,
  user_id uuid not null references public.community_users(id) on delete cascade,
  emoji text not null check (emoji in ('👍','❤️','😂','😮','😢','🙏')),
  created_at timestamptz not null default now(),
  primary key (message_id, user_id, emoji)
);
create index idx_message_reactions_room on public.message_reactions (room_id);
create index idx_message_reactions_user on public.message_reactions (user_id);
alter table public.message_reactions enable row level security;
alter table public.message_reactions replica identity full;

create policy "Read reactions in readable rooms" on public.message_reactions
  for select to authenticated, anon using (public.can_read_room(room_id));
create policy "React as yourself where you can post" on public.message_reactions
  for insert to authenticated
  with check (user_id = (select auth.uid()) and public.can_post_room(room_id)
              and exists (select 1 from public.room_messages m where m.id = message_id and m.room_id = room_id));
create policy "Remove your own reactions" on public.message_reactions
  for delete to authenticated using (user_id = (select auth.uid()));
alter publication supabase_realtime add table public.message_reactions;

-- ===== Profile: level + photo =====
alter table public.community_users add column if not exists level text not null default '';
create index if not exists idx_community_users_level on public.community_users (level);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;
create policy "Users upload their own avatar" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text
              and not public.is_guest());
create policy "Users replace their own avatar" on storage.objects
  for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- ===== In-app moderation for admins =====
create table private.community_admins (user_id uuid primary key references auth.users(id) on delete cascade);

create function public.is_community_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from private.community_admins where user_id = (select auth.uid()));
$$;

create function public.admin_reports(p_status text default 'open')
returns table (id uuid, created_at timestamptz, reason text, details text, message_snapshot text, status text,
               reporter_id uuid, reporter_nickname text, reported_user_id uuid, reported_nickname text,
               reported_banned boolean, times_reported bigint)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  return query
    select r.id, r.created_at, r.reason, r.details, r.message_snapshot, r.status,
           r.reporter_id, rep.nickname, r.reported_user_id, tgt.nickname, tgt.banned_at is not null,
           (select count(*) from public.user_reports x where x.reported_user_id = r.reported_user_id)
      from public.user_reports r
      join public.community_users rep on rep.id = r.reporter_id
      join public.community_users tgt on tgt.id = r.reported_user_id
     where p_status = 'all' or r.status = p_status
     order by r.created_at desc
     limit 200;
end $$;

create function public.admin_set_report_status(p_report uuid, p_status text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  update public.user_reports set status = p_status where id = p_report;
end $$;

create function public.admin_ban(p_user uuid, p_reason text, p_remove_messages boolean) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  perform private.ban_user(p_user, p_reason, p_remove_messages);
end $$;

create function public.admin_unban(p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_community_admin() then raise exception 'not allowed'; end if;
  perform private.unban_user(p_user);
end $$;

revoke execute on function public.is_community_admin(), public.admin_reports(text),
  public.admin_set_report_status(uuid, text), public.admin_ban(uuid, text, boolean), public.admin_unban(uuid)
  from public, anon;
grant execute on function public.is_community_admin(), public.admin_reports(text),
  public.admin_set_report_status(uuid, text), public.admin_ban(uuid, text, boolean), public.admin_unban(uuid)
  to authenticated;
