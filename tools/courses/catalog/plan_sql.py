#!/usr/bin/env python3
"""SQL that upserts finished lessons from plan.json as drafts (keeps 'published' if already published).
Channel name/url come from the course row. python3 plan_sql.py [course-slug] > out.sql"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
only = sys.argv[1] if len(sys.argv) > 1 else None


def q(value: str) -> str:
    assert "$j$" not in value
    return "$j$" + value + "$j$"


import glob
plans = [p for f in sorted(glob.glob(os.path.join(HERE, "plan*.json"))) for p in json.load(open(f))]
excluded = json.load(open(os.path.join(HERE, "excluded.json")))
skip = lambda p: p["lesson"] in excluded.get(p["course"], {}).get(str(p["book"]), [])
plans = [p for p in plans if not skip(p)]
# Renumber each book 1..n in the original order, so removed lessons leave no gaps.
counter = {}
for p in sorted(plans, key=lambda p: (p["course"], p["book"], p["lesson"])):
    key = (p["course"], p["book"])
    counter[key] = counter.get(key, 0) + 1
    p["lesson_out"] = counter[key]
for p in plans:
    path = os.path.join(ROOT, p["out"])
    if (only and p["course"] != only) or not os.path.exists(path):
        continue
    lesson = json.load(open(path))
    assert len(lesson["questions"]) == 10, path
    print(f"""insert into public.course_lessons (book_id, number, title, video_id, channel_name, channel_url, content, questions, status)
select b.id, {int(p['lesson_out'])}, {q(lesson['title'])}, '{p['id']}', c.channel_name, c.channel_url,
  {q(json.dumps(lesson['content'], ensure_ascii=False, separators=(',', ':')))}::jsonb, {q(json.dumps(lesson['questions'], ensure_ascii=False, separators=(',', ':')))}::jsonb, 'draft'
from public.courses c join public.course_books b on b.course_id = c.id and b.number = {int(p['book'])}
where c.slug = '{p['course']}'
on conflict (book_id, number) do update set title = excluded.title, video_id = excluded.video_id,
  channel_name = excluded.channel_name, channel_url = excluded.channel_url,
  content = excluded.content, questions = excluded.questions, updated_at = now();""")
