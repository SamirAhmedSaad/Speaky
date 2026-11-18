-- In-app updates (additive, safe): 0 = no mandatory update; optional banner on; no custom message.
-- Force everyone below a build to update: update app_settings set value = '57' where key = 'update_min_version_code';
insert into public.app_settings (key, value) values
  ('update_min_version_code', '0'::jsonb),
  ('update_optional_on', 'true'::jsonb),
  ('update_message', '""'::jsonb)
on conflict (key) do nothing;
