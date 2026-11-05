-- AI message correction/translation: one row per request, counted per rolling 24 hours.
-- Only the edge function (service role) reads and writes it.
create table if not exists public.ai_requests (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index if not exists ai_requests_user_time on public.ai_requests (user_id, created_at desc);
alter table public.ai_requests enable row level security;

-- Free users per 24 hours, and a fair-use cap for Premium (server-controlled).
insert into public.app_settings (key, value) values
  ('free_ai_corrections', '3'::jsonb),
  ('premium_ai_corrections', '200'::jsonb),
  ('ai_max_chars', '500'::jsonb)
on conflict (key) do nothing;
