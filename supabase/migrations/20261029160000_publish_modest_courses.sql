update public.course_lessons l set status = 'published', updated_at = now()
from public.course_books b join public.courses c on c.id = b.course_id
where l.book_id = b.id and c.slug in ('singsing-dialogues','bbc-storytellers','english-together','james-grammar','james-vocab','harry-conversations');

update public.courses set published = true, updated_at = now()
where slug in ('singsing-dialogues','bbc-storytellers','english-together','james-grammar','james-vocab','harry-conversations');

-- Not modest (unveiled women on screen): hidden, not deleted.
update public.courses set published = false, updated_at = now()
where slug in ('voa-lets-learn','bbc-easy-conversations','bbc-english-we-speak');

update public.app_settings set value = to_jsonb(coalesce((value #>> '{}')::int, 0) + 1) where key = 'courses_version';
