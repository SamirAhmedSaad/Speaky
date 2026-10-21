-- Applied via MCP. Courses: course -> books -> lessons. Lessons are written by the local
-- tools/courses/add_lesson.py script (service role) and read by everyone once published.
create table if not exists public.courses (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  title text not null,
  subtitle text,
  sort int not null default 0,
  published boolean not null default false,
  updated_at timestamptz not null default now()
);

create table if not exists public.course_books (
  id uuid primary key default gen_random_uuid(),
  course_id uuid not null references public.courses(id) on delete cascade,
  number int not null,
  title text not null,
  updated_at timestamptz not null default now(),
  unique (course_id, number)
);

create table if not exists public.course_lessons (
  id uuid primary key default gen_random_uuid(),
  book_id uuid not null references public.course_books(id) on delete cascade,
  number int not null,
  title text not null,
  video_id text not null,
  channel_name text,
  channel_url text,
  -- {vocabulary:[], fine_lines:[], expressions:[], grammar:[]}
  content jsonb not null default '{}'::jsonb,
  -- [{question, choices[4], answer, explanation}] x 10
  questions jsonb not null default '[]'::jsonb,
  status text not null default 'draft' check (status in ('draft', 'published')),
  updated_at timestamptz not null default now(),
  unique (book_id, number)
);

create index if not exists course_books_course_idx on public.course_books(course_id);
create index if not exists course_lessons_book_idx on public.course_lessons(book_id);

alter table public.courses enable row level security;
alter table public.course_books enable row level security;
alter table public.course_lessons enable row level security;

-- Read-only for the app (visitors included); writes only through the service role.
create policy "published courses are readable" on public.courses
  for select to anon, authenticated using (published);
create policy "books of published courses are readable" on public.course_books
  for select to anon, authenticated
  using (exists (select 1 from public.courses c where c.id = course_id and c.published));
create policy "published lessons are readable" on public.course_lessons
  for select to anon, authenticated using (status = 'published');
