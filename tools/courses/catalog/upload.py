#!/usr/bin/env python3
"""Upload finished lessons from plan.json as drafts (keeps 'published' if already published).
Channel name/url come from the course row. Needs SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in
tools/courses/.env (same file add_lesson.py uses).

  python3 upload.py                  # every finished lesson
  python3 upload.py best-start       # one course
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
from add_lesson import Db, load_env  # noqa: E402

load_env()
db = Db()
only = sys.argv[1] if len(sys.argv) > 1 else None
courses, books, done = {}, {}, 0

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
    if p["course"] not in courses:
        courses[p["course"]] = db.find("courses", slug=p["course"])
    course = courses[p["course"]]
    key = (p["course"], p["book"])
    if key not in books:
        books[key] = db.find("course_books", course_id=course["id"], number=p["book"])
    lesson = json.load(open(path))
    existing = db.find("course_lessons", book_id=books[key]["id"], number=p["lesson_out"])
    db.upsert("course_lessons", {
        "book_id": books[key]["id"], "number": p["lesson_out"], "title": lesson["title"], "video_id": p["id"],
        "channel_name": course.get("channel_name"), "channel_url": course.get("channel_url"),
        "content": lesson["content"], "questions": lesson["questions"],
        "status": (existing or {}).get("status", "draft"),
    }, "book_id,number")
    done += 1
    print(f"{p['course']} b{p['book']}-l{p['lesson']}: {lesson['title']}")
print(f"{done} lessons uploaded")
