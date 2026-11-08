-- Profile photos are now checked on the phone (offline model) before they're set, not with Google
-- Vision. Applied in the SQL editor. The check-photo edge function is no longer called.
drop trigger if exists trg_photo_check on public.community_users;

create or replace function public.on_photo_change_mark()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  -- Photos are checked on the phone before they're set; a new photo clears an earlier rejection.
  if coalesce(new.photo_url, '') <> ''
     and (tg_op = 'INSERT' or new.photo_url is distinct from old.photo_url) then
    new.photo_status := 'ok';
    new.photo_reason := null;
  end if;
  return new;
end $$;
