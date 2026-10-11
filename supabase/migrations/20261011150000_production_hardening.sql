-- Applied via MCP (server_content_backstop + terms_acceptance_and_message_checks) + SQL editor updates.

-- ===== Server-side content backstop (links, emails, phone numbers, profanity) =====
create or replace function public.violates_content_rules(p_text text) returns boolean
language sql immutable set search_path = '' as $$
  select coalesce(p_text, '') ~* '(https?://\S{4,}|\mwww\.\S{4,}|\m\w{3,}\.(com|net|org|io|app|me|co|tv|gg|ly|link)\M)'
      or coalesce(p_text, '') ~* '[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}'
      or regexp_replace(coalesce(p_text, ''), '[\s\-().]', '', 'g') ~ '\+?\d{8,}'
      or lower(coalesce(p_text, '')) ~ '\m(fuck|fucking|shit|bitch|cunt|nigga|nigger|faggot|whore|slut|porn|dick|pussy|asshole|motherfucker)\M';
$$;

create or replace function public.on_profile_guard() returns trigger
language plpgsql set search_path = '' as $$
begin
  if public.violates_content_rules(new.nickname) then raise exception 'content_blocked'; end if;
  return new;
end $$;
create trigger trg_profile_guard before insert or update of nickname on public.community_users
  for each row execute function public.on_profile_guard();

-- ===== Terms acceptance (13+ and community rules) =====
alter table public.community_users add column if not exists terms_accepted_at timestamptz;

create or replace function public.has_accepted_terms(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.community_users where id = p_user and terms_accepted_at is not null);
$$;
revoke execute on function public.has_accepted_terms(uuid) from public, anon;
grant execute on function public.has_accepted_terms(uuid) to authenticated;

create or replace function public.on_message_rate_limit() returns trigger
language plpgsql security definer set search_path = '' as $$
declare v_recent int; v_today int;
begin
  if public.is_banned(new.sender_id) then raise exception 'banned'; end if;
  if not public.has_accepted_terms(new.sender_id) then raise exception 'terms_not_accepted'; end if;
  if new.kind = 'voice' and public.is_guest() then raise exception 'guest_not_allowed'; end if;
  if new.kind = 'text' and public.violates_content_rules(new.body) then raise exception 'content_blocked'; end if;
  select count(*) into v_recent from public.room_messages
   where sender_id = new.sender_id and created_at > now() - interval '1 minute';
  if v_recent >= 20 then raise exception 'rate_limited'; end if;
  if (select kind from public.rooms where id = new.room_id) = 'channel' then
    select count(*) into v_today from public.room_messages m
     join public.rooms r on r.id = m.room_id and r.kind = 'channel'
     where m.sender_id = new.sender_id and m.created_at >= date_trunc('day', now());
    if v_today >= 50 then raise exception 'daily_limit'; end if;
  end if;
  return new;
end $$;

create or replace function public.on_message_update_guard() returns trigger
language plpgsql set search_path = '' as $$
begin
  if current_user = 'authenticated' then
    if new.id <> old.id or new.room_id <> old.room_id or new.sender_id <> old.sender_id
       or new.kind <> old.kind or new.created_at <> old.created_at
       or new.voice_path is distinct from old.voice_path then
      raise exception 'only the text can be edited';
    end if;
    if old.created_at < now() - interval '24 hours' then
      raise exception 'messages can only be edited for 24 hours';
    end if;
    if public.violates_content_rules(new.body) then raise exception 'content_blocked'; end if;
    new.edited_at := now();
  end if;
  return new;
end $$;

-- ===== Orphan avatars (owner deleted) for the prune-storage edge function =====
create or replace function public.avatar_prune_candidates() returns setof text
language sql stable security definer set search_path = '' as $$
  select o.name from storage.objects o
   where o.bucket_id = 'avatars'
     and o.created_at < now() - interval '1 day'
     and not exists (select 1 from public.community_users cu where cu.id::text = (storage.foldername(o.name))[1]);
$$;
revoke execute on function public.avatar_prune_candidates() from public, anon, authenticated;
grant execute on function public.avatar_prune_candidates() to service_role;

-- ===== Data updates (run once) =====
update public.community_users set terms_accepted_at = coalesce(created_at, now()) where terms_accepted_at is null;
update private.cleanup_config set value = 734003200 where key = 'voice_cap_bytes'; -- 700 MB, leaves room for avatars on the 1 GB plan
