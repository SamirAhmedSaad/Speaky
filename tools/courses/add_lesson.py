#!/usr/bin/env python3
"""Add a course lesson from a YouTube video to Supabase.

  python add_lesson.py URL --book 1 --lesson 1            # generate + save as draft
  python add_lesson.py URL --book 1 --lesson 1 --publish  # ...and publish it
  python add_lesson.py --book 1 --lesson 1 --publish      # publish an existing draft
  python add_lesson.py --course-title "New name"           # rename the course
  python add_lesson.py --book 1 --book-title "Daily Life"  # rename a book

Flow: video title/channel (YouTube oEmbed) -> transcript (English, else Arabic captions) ->
Claude writes vocabulary, fine lines, expressions, grammar and a 10-question exam ->
saved to lessons/b<book>-l<lesson>.json (edit it if you want, then re-run with --from-json) ->
uploaded as a draft. Nothing is visible in the app until it is published.

Needs .env next to this file (see .env.example): SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY,
ANTHROPIC_API_KEY.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import List

import requests
from pydantic import BaseModel, Field

HERE = Path(__file__).resolve().parent
LESSONS_DIR = HERE / "lessons"


def load_env():
    env_file = HERE / ".env"
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip().strip('"'))


# ---------------- Lesson schema (what Claude must return) ----------------

class Vocab(BaseModel):
    word: str
    pos: str = Field(description="noun, verb, adjective, adverb, phrasal verb ...")
    meaning_en: str = Field(description="Simple English definition for learners")
    meaning_ar: str
    note_ar: str = Field(default="", description="Short Arabic note: pronunciation, US/UK, word family. Empty if none")
    example: str = Field(description="Your own short natural example sentence")


class FineLineExample(BaseModel):
    word: str
    sentence: str


class FineLine(BaseModel):
    title: str = Field(description='e.g. "aisle vs lane"')
    words: List[str]
    explanation_ar: str = Field(description="How the words differ, explained logically in Arabic")
    examples: List[FineLineExample]


class Expression(BaseModel):
    phrase: str
    meaning_ar: str
    example: str


class Grammar(BaseModel):
    title: str
    explanation_ar: str
    examples: List[str]


class LessonContent(BaseModel):
    vocabulary: List[Vocab]
    fine_lines: List[FineLine]
    expressions: List[Expression]
    grammar: List[Grammar]


class Question(BaseModel):
    question: str
    choices: List[str] = Field(description="Exactly 4 choices")
    answer: int = Field(description="Index (0-3) of the correct choice")
    explanation_ar: str


class Lesson(BaseModel):
    title: str = Field(description="Short English lesson title, e.g. 'At the Supermarket'")
    content: LessonContent
    questions: List[Question] = Field(description="Exactly 10 multiple-choice questions")


PROMPT = """You are preparing a lesson page for Speaky, an app where Arabic speakers learn English.
Below is the transcript of a YouTube lesson (it may be in Arabic, English or both, and auto-generated
captions contain spelling mistakes - work out what the teacher meant).

Extract what the video teaches and write:
- vocabulary: every English word or phrase the lesson teaches, with a simple English meaning, the
  Arabic meaning, a short Arabic note when the teacher mentions pronunciation, US/UK usage or the word
  family, and YOUR OWN natural example sentence.
- fine_lines: groups of similar words and how they differ (explained logically in Arabic, with an
  example for each word). Include the comparisons the teacher makes.
- expressions: useful phrases and idioms from the lesson.
- grammar: the grammar points the lesson explains, in Arabic, with short English examples.
- questions: exactly 10 multiple-choice questions (4 choices each) testing this lesson's vocabulary,
  fine lines and grammar. Mix the question types and the position of the correct answer.

Rules: do not copy the video's story or long passages; write your own explanations and examples.
Keep everything short and clear for B1 learners.

Video title: {title}

Transcript:
{transcript}
"""


# ---------------- YouTube ----------------

def video_id_from(url: str) -> str:
    match = re.search(r"(?:v=|youtu\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})", url)
    if not match:
        sys.exit(f"Not a YouTube link: {url}")
    return match.group(1)


def video_info(video_id: str) -> dict:
    response = requests.get(
        "https://www.youtube.com/oembed",
        params={"url": f"https://www.youtube.com/watch?v={video_id}", "format": "json"},
        timeout=20,
    )
    response.raise_for_status()
    return response.json()


def transcript(video_id: str) -> str:
    from youtube_transcript_api import YouTubeTranscriptApi

    try:
        fetched = YouTubeTranscriptApi().fetch(video_id, languages=["en", "ar"])
    except Exception as e:  # no captions, private video, ...
        sys.exit(f"Could not get captions for this video: {e}")
    return " ".join(snippet.text for snippet in fetched)


# ---------------- Claude ----------------

def generate(title: str, text: str) -> Lesson:
    import anthropic

    client = anthropic.Anthropic()
    print("Asking Claude to build the lesson (about a minute)...")
    response = client.messages.parse(
        model="claude-opus-5-5",
        max_tokens=16000,
        output_config={"effort": "high"},
        messages=[{"role": "user", "content": PROMPT.format(title=title, transcript=text)}],
        output_format=Lesson,
        # If a safety classifier declines, the API retries on a fallback model.
        extra_headers={"anthropic-beta": "server-side-fallback-2026-07-01"},
        extra_body={"fallbacks": "default"},
    )
    if response.stop_reason == "refusal":
        sys.exit("Claude declined to process this transcript.")
    lesson = response.parsed_output
    if len(lesson.questions) != 10 or any(len(q.choices) != 4 for q in lesson.questions):
        print("Warning: the exam does not have exactly 10 questions with 4 choices - check the JSON.")
    return lesson


# ---------------- Supabase (service role, bypasses RLS) ----------------

class Db:
    def __init__(self):
        url = os.environ.get("SUPABASE_URL")
        key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
        if not url or not key:
            sys.exit("Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in tools/courses/.env")
        self.base = url.rstrip("/") + "/rest/v1"
        self.headers = {"apikey": key, "Authorization": f"Bearer {key}", "Content-Type": "application/json"}

    def upsert(self, table: str, row: dict, on_conflict: str) -> dict:
        response = requests.post(
            f"{self.base}/{table}",
            params={"on_conflict": on_conflict},
            headers={**self.headers, "Prefer": "resolution=merge-duplicates,return=representation"},
            data=json.dumps(row),
            timeout=30,
        )
        if not response.ok:
            sys.exit(f"Supabase error ({table}): {response.status_code} {response.text}")
        return response.json()[0]

    def find(self, table: str, **filters) -> dict | None:
        params = {k: f"eq.{v}" for k, v in filters.items()}
        response = requests.get(f"{self.base}/{table}", params=params, headers=self.headers, timeout=30)
        response.raise_for_status()
        rows = response.json()
        return rows[0] if rows else None

    def update(self, table: str, row_id: str, values: dict):
        response = requests.patch(
            f"{self.base}/{table}", params={"id": f"eq.{row_id}"}, headers=self.headers,
            data=json.dumps(values), timeout=30,
        )
        if not response.ok:
            sys.exit(f"Supabase error ({table}): {response.status_code} {response.text}")


def main():
    load_env()
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("url", nargs="?", help="YouTube link")
    p.add_argument("--course", default="english-5000", help="course slug (default: english-5000)")
    p.add_argument("--course-title", help="set the course title")
    p.add_argument("--course-subtitle", help="set the course subtitle")
    p.add_argument("--book", type=int, help="book number")
    p.add_argument("--book-title", help="set the book title")
    p.add_argument("--lesson", type=int, help="lesson number in the book")
    p.add_argument("--title", help="override the lesson title")
    p.add_argument("--from-json", help="upload this lesson JSON instead of generating one")
    p.add_argument("--publish", action="store_true", help="make the lesson (and course) visible in the app")
    args = p.parse_args()

    db = Db()
    course = db.find("courses", slug=args.course)
    course_row = {"slug": args.course, "title": args.course_title or (course or {}).get("title") or "English Course"}
    if args.course_subtitle:
        course_row["subtitle"] = args.course_subtitle
    if args.publish:
        course_row["published"] = True
    course = db.upsert("courses", course_row, "slug")
    print(f"Course: {course['title']}")
    if args.book is None:
        return

    book = db.find("course_books", course_id=course["id"], number=args.book)
    book = db.upsert("course_books", {
        "course_id": course["id"], "number": args.book,
        "title": args.book_title or (book or {}).get("title") or f"Book {args.book}",
    }, "course_id,number")
    print(f"Book {book['number']}: {book['title']}")
    if args.lesson is None:
        return

    existing = db.find("course_lessons", book_id=book["id"], number=args.lesson)
    json_path = Path(args.from_json) if args.from_json else LESSONS_DIR / f"b{args.book}-l{args.lesson}.json"

    if args.url or args.from_json:
        if not args.url:
            if not existing:
                sys.exit("New lesson: also pass the YouTube link.")
            video_id = existing["video_id"]
        else:
            video_id = video_id_from(args.url)
        info = video_info(video_id)
        if args.from_json:
            lesson = json.loads(json_path.read_text())
        else:
            lesson = generate(info["title"], transcript(video_id)).model_dump()
            LESSONS_DIR.mkdir(exist_ok=True)
            json_path.write_text(json.dumps(lesson, ensure_ascii=False, indent=2))
            print(f"Saved {json_path} (edit it and re-run with --from-json to change anything)")
        row = {
            "book_id": book["id"], "number": args.lesson,
            "title": args.title or lesson["title"],
            "video_id": video_id,
            "channel_name": info.get("author_name"), "channel_url": info.get("author_url"),
            "content": lesson["content"], "questions": lesson["questions"],
            "status": "published" if args.publish else (existing or {}).get("status", "draft"),
        }
        saved = db.upsert("course_lessons", row, "book_id,number")
    elif existing:
        saved = existing
        if args.publish:
            db.update("course_lessons", existing["id"], {"status": "published"})
            saved["status"] = "published"
    else:
        sys.exit("No such lesson yet: pass the YouTube link.")

    content = saved["content"]
    print(f"Lesson {saved['number']}: {saved['title']}  [{saved['status']}]")
    print(f"  {len(content.get('vocabulary', []))} words, {len(content.get('fine_lines', []))} fine lines, "
          f"{len(content.get('expressions', []))} expressions, {len(content.get('grammar', []))} grammar, "
          f"{len(saved['questions'])} questions")
    if saved["status"] != "published":
        print(f"  Draft - publish with: python add_lesson.py --book {args.book} --lesson {args.lesson} --publish")


if __name__ == "__main__":
    main()
