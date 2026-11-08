-- Profile photo moderation with Google Cloud Vision SafeSearch (edge function check-photo).
-- Every new photo_url (Google sign-in photo, upload, anything) is checked on the server; an immodest
-- photo is removed and the person is asked for a modest one. Admins can remove a photo themselves.
-- Free quota guard: at most 950 Vision calls a month; after that new photos are allowed ("unchecked").
-- Only when a photo is set (sign-up or change); no daily scan of old photos.

alter table public.community_users add column if not exists photo_status text not null default 'ok';
alter table public.community_users add column if not exists photo_reason text;
alter table public.community_users add column if not exists photo_checked_at timestamptz;

do $$ begin
  alter table public.community_users add constraint community_users_photo_status_check
    check (photo_status in ('ok', 'pending', 'rejected', 'unchecked'));
exception when duplicate_object then null; end $$;

-- Existing photos are kept as they are; only new or changed photos are checked.

create table if not exists private.vision_usage (
  month text primary key,
  calls int not null default 0
);

-- Takes one Vision call from this month's allowance; false when it is used up.
create or replace function public.vision_take_quota(p_limit int default 950)
returns boolean
language plpgsql security definer
set search_path = ''
as $$
declare
  m text := to_char(now() at time zone 'utc', 'YYYY-MM');
  n int;
begin
  insert into private.vision_usage (month, calls) values (m, 1)
  on conflict (month) do update set calls = private.vision_usage.calls + 1
   where private.vision_usage.calls < p_limit
  returning calls into n;
  return n is not null;
end $$;
revoke all on function public.vision_take_quota(int) from public, anon, authenticated;
grant execute on function public.vision_take_quota(int) to service_role;

-- The check's result; ignored if the person changed their photo meanwhile. Rejected removes the photo.
create or replace function public.set_photo_result(p_user uuid, p_url text, p_status text, p_reason text)
returns void
language sql security definer
set search_path = ''
as $$
  update public.community_users
     set photo_status = p_status,
         photo_reason = p_reason,
         photo_checked_at = now(),
         photo_url = case when p_status = 'rejected' then null else photo_url end
   where id = p_user and photo_url is not distinct from p_url;
$$;
revoke all on function public.set_photo_result(uuid, text, text, text) from public, anon, authenticated;
grant execute on function public.set_photo_result(uuid, text, text, text) to service_role;

-- A new photo is "pending" until checked; our cartoon avatars are fine straight away.
create or replace function public.on_photo_change_mark()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if coalesce(new.photo_url, '') <> ''
     and (tg_op = 'INSERT' or new.photo_url is distinct from old.photo_url) then
    if new.photo_url like '%/avatars/751ad4b1-785b-4b9c-a433-39d79fc6dedd/fun/%' then
      new.photo_status := 'ok';
      new.photo_reason := null;
    else
      new.photo_status := 'pending';
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trg_photo_mark on public.community_users;
create trigger trg_photo_mark before insert or update of photo_url on public.community_users
  for each row execute function public.on_photo_change_mark();

create or replace function public.on_photo_change_check()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  if new.photo_status = 'pending' and (tg_op = 'INSERT' or new.photo_url is distinct from old.photo_url) then
    perform net.http_post(
      url := 'https://bkivjjvgzsqgbkfubjtx.supabase.co/functions/v1/check-photo',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'x-push-secret', (select value from private.app_secrets where name = 'push_secret')),
      body := jsonb_build_object('user_id', new.id, 'url', new.photo_url),
      timeout_milliseconds := 5000);
  end if;
  return new;
end $$;

drop trigger if exists trg_photo_check on public.community_users;
create trigger trg_photo_check after insert or update of photo_url on public.community_users
  for each row execute function public.on_photo_change_check();

-- Admins: remove someone's photo and ask them for a modest one.
create or replace function public.admin_reject_photo(p_user uuid, p_reason text default null)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_community_admin() then raise exception 'admins only'; end if;
  update public.community_users
     set photo_url = null, photo_status = 'rejected', photo_reason = coalesce(p_reason, 'admin'), photo_checked_at = now()
   where id = p_user;
end $$;
revoke all on function public.admin_reject_photo(uuid, text) from public, anon;
grant execute on function public.admin_reject_photo(uuid, text) to authenticated;

-- My photo's status (for the "please choose a modest photo" screen).
create or replace function public.my_photo_status()
returns table (status text, reason text)
language sql stable security definer
set search_path = ''
as $$
  select photo_status, photo_reason from public.community_users where id = (select auth.uid());
$$;
revoke all on function public.my_photo_status() from public, anon;
grant execute on function public.my_photo_status() to authenticated;
