-- Applied via MCP. Server-wide app switches readable by every app (guests too).
-- ads_off = true: no ads and no "watch or subscribe" dialog for anyone. Turn ads back on with:
--   update public.app_settings set value = 'false', updated_at = now() where key = 'ads_off';
create table if not exists public.app_settings (
  key text primary key,
  value jsonb not null,
  updated_at timestamptz not null default now()
);
alter table public.app_settings enable row level security;
create policy "app settings are readable" on public.app_settings for select to anon, authenticated using (true);
insert into public.app_settings (key, value) values ('ads_off', 'true'::jsonb)
on conflict (key) do update set value = excluded.value, updated_at = now();

-- courses_version: apps re-download courses when it changes (otherwise at most once a day).
-- After editing course content, bump it:
--   update public.app_settings set value = to_jsonb((value)::text::int + 1), updated_at = now() where key = 'courses_version';
insert into public.app_settings (key, value) values ('courses_version', '1'::jsonb) on conflict (key) do nothing;
