-- Applied via MCP. Course level tag + creator channel, and three new courses (unpublished until reviewed).
alter table public.courses
  add column if not exists level text,
  add column if not exists channel_name text,
  add column if not exists channel_url text;

update public.courses set level = 'Intermediate', channel_name = 'Taleek',
  channel_url = 'https://www.youtube.com/@Taleek', sort = 30
where slug = 'english-5000';

insert into public.courses (slug, title, subtitle, sort, published, level, channel_name, channel_url) values
 ('best-start', 'The Best Start', 'Your first steps: letters, greetings and basic sentences', 10, false, 'Beginner',
  'مستر انجليزى', 'https://www.youtube.com/channel/UClkf2zTnLqFJeYlrRpKnl3w'),
 ('english-zero-pro', 'English from Zero to Pro', '10 levels · a complete path to fluency', 20, false, 'Beginner → Advanced',
  'تعلم الانجليزية مع مراد', 'https://www.youtube.com/channel/UC59I0n0C7a4vD6Vg6dDWu5g'),
 ('essential-words-phrases', 'Essential Words & Phrases', 'The 300 most-used words, key phrases and pronunciation', 40, false, 'Beginner',
  'دليلك للانجليزي', 'https://www.youtube.com/channel/UCj0klqeJtq7c5eD8p8V3e2w')
on conflict (slug) do update set title = excluded.title, subtitle = excluded.subtitle, sort = excluded.sort,
  level = excluded.level, channel_name = excluded.channel_name, channel_url = excluded.channel_url;

insert into public.course_books (course_id, number, title)
select c.id, b.number, b.title from public.courses c
join (values
  ('best-start', 1, 'Level 1'), ('best-start', 2, 'Level 2'), ('best-start', 3, 'Level 3'),
  ('english-zero-pro', 1, 'Level 1 · From Zero'), ('english-zero-pro', 2, 'Level 2'), ('english-zero-pro', 3, 'Level 3'),
  ('english-zero-pro', 4, 'Level 4'), ('english-zero-pro', 5, 'Level 5'),
  ('essential-words-phrases', 1, 'Top Words'), ('essential-words-phrases', 2, 'Key Phrases'),
  ('essential-words-phrases', 3, 'Pronunciation & Memory')
) as b(slug, number, title) on b.slug = c.slug
on conflict (course_id, number) do update set title = excluded.title;
