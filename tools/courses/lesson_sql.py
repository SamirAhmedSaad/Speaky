#!/usr/bin/env python3
"""SQL that upserts a lesson JSON as a draft (keeps 'published' if it already is):
python lesson_sql.py lessons/b1-l2.json BOOK_ID NUMBER VIDEO_ID > out.sql"""
import json
import sys

path, book_id, number, video_id = sys.argv[1:5]
lesson = json.load(open(path))
assert len(lesson["questions"]) == 10 and all(len(q["choices"]) == 4 for q in lesson["questions"]), "exam must be 10 x 4"


def q(value: str) -> str:
    assert "$j$" not in value
    return "$j$" + value + "$j$"


print(f"""insert into public.course_lessons (book_id, number, title, video_id, channel_name, channel_url, content, questions, status)
values ('{book_id}', {int(number)}, {q(lesson['title'])}, '{video_id}', 'Taleek', 'https://www.youtube.com/@Taleek',
  {q(json.dumps(lesson['content'], ensure_ascii=False))}::jsonb, {q(json.dumps(lesson['questions'], ensure_ascii=False))}::jsonb, 'draft')
on conflict (book_id, number) do update set title = excluded.title, video_id = excluded.video_id,
  content = excluded.content, questions = excluded.questions, updated_at = now()
returning number, status;""")
