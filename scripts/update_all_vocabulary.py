#!/usr/bin/env python3
"""
Two-phase vocabulary updater for all 2,075 words.

Phase 1: Scrape englishcollocation.com (TalkEnglish redirect target) for
         learner-friendly meanings and example sentences.
Phase 2: Claude API Batches for any words that scraping couldn't cover.

Usage:
    pip install anthropic requests beautifulsoup4
    ANTHROPIC_API_KEY=sk-ant-... python3 update_all_vocabulary.py

    # Inspect a single word's scraped output first:
    python3 update_all_vocabulary.py --inspect good

    # Skip scraping, go straight to Claude API:
    ANTHROPIC_API_KEY=sk-ant-... python3 update_all_vocabulary.py --no-scrape

    # After reviewing the output:
    cp vocabulary_updated.json ../composeApp/src/commonMain/composeResources/files/vocabulary.json
"""

import json
import os
import re
import sys
import time
from pathlib import Path
from urllib.parse import quote

try:
    import requests as req_lib
    from bs4 import BeautifulSoup
except ImportError:
    print("Missing packages. Run: pip install requests beautifulsoup4 anthropic")
    sys.exit(1)

try:
    import anthropic
    from anthropic.types.message_create_params import MessageCreateParamsNonStreaming
    from anthropic.types.messages.batch_create_params import Request
except ImportError:
    print("Error: anthropic package not found. Run: pip install anthropic")
    sys.exit(1)

VOCAB_FILE = (
    Path(__file__).parent.parent
    / "composeApp/src/commonMain/composeResources/files/vocabulary.json"
)
OUTPUT_FILE = VOCAB_FILE.parent / "vocabulary_updated.json"
BATCH_ID_FILE = Path(__file__).parent / ".last_batch_id"
SCRAPE_CACHE_FILE = Path(__file__).parent / ".scrape_cache.json"

MODEL = "claude-haiku-4-5"
SCRAPE_BASE = "http://www.englishcollocation.com/how-to-use/{}"
SCRAPE_DELAY = 0.4  # seconds between requests — be polite to the server

LEVEL_GUIDE = {
    "A1": "Use very simple words. Sentences should be 6-10 words. Topics: home, food, greetings, numbers, family.",
    "A2": "Use simple, everyday words. Sentences should be 8-12 words. Topics: daily routines, shopping, travel, work basics.",
    "B1": "Use common conversational words. Sentences should be 10-14 words. Topics: work, hobbies, plans, opinions, experiences.",
    "B2": "Use varied vocabulary. Sentences should be 12-16 words. Topics: news, society, technology, health, environment.",
    "C1": "Use sophisticated vocabulary naturally. Sentences should be 12-18 words. Topics: analysis, debate, professional contexts.",
    "C2": "Use advanced, nuanced vocabulary. Sentences should be 14-20 words. Show subtle or idiomatic usage.",
}


# ---------------------------------------------------------------------------
# Phase 1: Scraping
# ---------------------------------------------------------------------------

POS_SHORT_MAP = {
    "noun": "n", "verb": "v", "adjective": "adj", "adverb": "adv",
    "preposition": "prep", "conjunction": "conj", "pronoun": "pron",
    "interjection": "interj", "determiner": "det", "article": "art",
}


def _clean_text(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _extract_from_div(div, word_lower: str) -> tuple[str | None, list[str]]:
    """
    Extract meaning and sentences from a single sm2-playlist-bd div.

    Confirmed structure: all <a href="*.mp3"> links in order are:
      [0] = the word itself
      [1] = definition text
      [2] = "Listen to all"
      [3] = "All sentences (with pause)"
      [4+] = example sentences (contain the word, 15-250 chars)

    Note: BeautifulSoup places all links inside the <table> context due to
    malformed HTML, so we cannot use find_parent("table") to split them.
    We filter by content instead.
    """
    SKIP_PHRASES = {"listen to all", "all sentences (with pause)", "listen"}

    all_links = div.find_all("a")
    if len(all_links) < 2:
        return None, []

    meaning = _clean_text(all_links[1].get_text(" "))
    if len(meaning) < 5:
        meaning = None

    sentences: list[str] = []
    for a in all_links[2:]:  # skip word + definition
        text = _clean_text(a.get_text(" "))
        if not text or text.lower() in SKIP_PHRASES:
            continue
        if word_lower in text.lower() and 15 < len(text) < 250:
            sentences.append(text)

    return meaning, sentences


def scrape_word(session: req_lib.Session, word: str, pos: str = "") -> dict | None:
    """
    Fetch meaning and sentences from englishcollocation.com (HTTP, no SSL).
    Returns {"meaning": str, "sentences": [str, str, str]} or None.
    """
    slug = quote(word.lower().replace(" ", "-"))
    url = SCRAPE_BASE.format(slug)

    try:
        resp = session.get(url, timeout=12, allow_redirects=True)
        if resp.status_code != 200:
            return None
    except Exception:
        return None

    soup = BeautifulSoup(resp.text, "html.parser")
    for tag in soup(["script", "style"]):
        tag.decompose()

    playlist_divs = soup.find_all("div", class_="sm2-playlist-bd")
    if not playlist_divs:
        return None

    word_lower = word.lower()
    pos_short = POS_SHORT_MAP.get(pos.lower(), pos.lower()[:3]) if pos else ""

    # Try to find the div matching the word's part of speech
    target_div = playlist_divs[0]
    if pos_short and len(playlist_divs) > 1:
        for div in playlist_divs:
            div_text = div.get_text()
            if f"({pos_short})" in div_text:
                target_div = div
                break

    meaning, sentences = _extract_from_div(target_div, word_lower)

    # If not enough sentences, pull from all divs
    if len(sentences) < 3:
        seen: set[str] = {s.lower() for s in sentences}
        for div in playlist_divs:
            if div is target_div:
                continue
            _, extra = _extract_from_div(div, word_lower)
            for s in extra:
                if s.lower() not in seen:
                    seen.add(s.lower())
                    sentences.append(s)
            if len(sentences) >= 5:
                break

        # If still no meaning, try other divs
        if not meaning:
            for div in playlist_divs:
                if div is target_div:
                    continue
                m, _ = _extract_from_div(div, word_lower)
                if m:
                    meaning = m
                    break

    if meaning and len(sentences) >= 3:
        return {"meaning": meaning, "sentences": sentences[:3]}

    return None


def inspect_word(word: str, pos: str = "") -> None:
    """--inspect mode: print scraped content for one word so you can verify."""
    session = req_lib.Session()
    session.headers["User-Agent"] = "Mozilla/5.0 (compatible; VocabBot/1.0; educational)"
    print(f"Scraping: {SCRAPE_BASE.format(quote(word.lower()))}\n")
    result = scrape_word(session, word, pos)
    if result:
        print(f"Meaning : {result['meaning']}")
        print("Sentences:")
        for i, s in enumerate(result["sentences"], 1):
            print(f"  {i}. {s}")
    else:
        print("No result — would fall back to Claude API.")


def run_scrape_phase(data: dict) -> tuple[dict, list]:
    """
    Scrape all words. Returns:
      scraped  — dict keyed by "level_idx_word_idx" → {meaning, sentences}
      failed   — list of (level_idx, word_idx, entry, level_name)
    """
    cache: dict = {}
    if SCRAPE_CACHE_FILE.exists():
        try:
            cache = json.loads(SCRAPE_CACHE_FILE.read_text())
            cached_hits = sum(1 for v in cache.values() if v)
            print(f"Loaded scrape cache: {len(cache)} words cached, {cached_hits} successful")
        except Exception:
            pass

    session = req_lib.Session()
    session.headers["User-Agent"] = "Mozilla/5.0 (compatible; VocabBot/1.0; educational)"

    scraped: dict = {}
    failed: list = []
    total = sum(len(lvl["words"]) for lvl in data["levels"])
    done = 0

    print(f"Scraping {total} words from TalkEnglish...")

    for level_idx, level in enumerate(data["levels"]):
        for word_idx, entry in enumerate(level["words"]):
            word = entry["word"]
            done += 1
            print(f"\r  {done}/{total}  {word:<25}", end="", flush=True)

            if word in cache:
                result = cache[word]
                if result:
                    scraped[f"{level_idx}_{word_idx}"] = result
                else:
                    failed.append((level_idx, word_idx, entry, level["level"]))
                continue

            result = scrape_word(session, word, entry.get("partOfSpeech", ""))
            cache[word] = result

            if result:
                scraped[f"{level_idx}_{word_idx}"] = result
            else:
                failed.append((level_idx, word_idx, entry, level["level"]))

            time.sleep(SCRAPE_DELAY)

    print()
    SCRAPE_CACHE_FILE.write_text(json.dumps(cache, ensure_ascii=False, indent=2))

    hits = len(scraped)
    misses = len(failed)
    print(f"Scrape done: {hits} succeeded, {misses} need Claude API fallback")
    return scraped, failed


# ---------------------------------------------------------------------------
# Phase 2: Claude API Batches fallback
# ---------------------------------------------------------------------------

def make_prompt(word: str, pos: str, level: str) -> str:
    guide = LEVEL_GUIDE.get(level, LEVEL_GUIDE["B1"])
    return f"""You are writing content for an English learning app for CEFR {level} learners.

Word: "{word}"
Part of speech: {pos}
Level guide: {guide}

Your task:
1. Write one MEANING: a clear, simple definition (max 12 words). Do NOT start with "A" or "The" + the word itself. Be direct.
2. Write THREE SENTENCES: natural, everyday example sentences showing the word in context.
   - Make sentences relatable (school, work, friends, daily life, travel, food).
   - Do NOT use historical quotes, violent content, religious references, or technical jargon.
   - Each sentence must clearly demonstrate the word's meaning.
   - Sentences must be grammatically correct.

Respond ONLY with this exact JSON (no markdown, no explanation):
{{"meaning": "...", "sentences": ["...", "...", "..."]}}"""


def submit_batch(client: anthropic.Anthropic, failed_entries: list) -> str:
    batch_requests = []
    for level_idx, word_idx, entry, level_name in failed_entries:
        custom_id = f"{level_idx}_{word_idx}"
        batch_requests.append(
            Request(
                custom_id=custom_id,
                params=MessageCreateParamsNonStreaming(
                    model=MODEL,
                    max_tokens=350,
                    messages=[{
                        "role": "user",
                        "content": make_prompt(
                            entry["word"],
                            entry.get("partOfSpeech", "word"),
                            level_name,
                        ),
                    }],
                ),
            )
        )

    total = len(batch_requests)
    cost = round(total * 0.001 * 0.5, 2)
    print(f"\nSubmitting Claude API batch: {total} words  (~${cost} with Batches 50% discount)")

    batch = client.messages.batches.create(requests=batch_requests)
    batch_id = batch.id
    BATCH_ID_FILE.write_text(batch_id)
    print(f"Batch ID: {batch_id}  (saved to {BATCH_ID_FILE})")
    return batch_id


def poll_batch(client: anthropic.Anthropic, batch_id: str) -> None:
    print("Waiting for batch to complete (typically 5–30 minutes)...")
    while True:
        batch = client.messages.batches.retrieve(batch_id)
        counts = batch.request_counts
        print(
            f"  [{batch.processing_status}]  "
            f"processing={counts.processing}  "
            f"succeeded={counts.succeeded}  "
            f"errored={counts.errored}"
        )
        if batch.processing_status == "ended":
            break
        time.sleep(30)


def apply_batch_results(client: anthropic.Anthropic, batch_id: str, data: dict) -> tuple[int, int]:
    print("Applying Claude API results...")
    updated = 0
    errors = 0

    for result in client.messages.batches.results(batch_id):
        if result.result.type != "succeeded":
            errors += 1
            continue

        text = next(
            (b.text for b in result.result.message.content if b.type == "text"), ""
        ).strip()

        if text.startswith("```"):
            lines = text.splitlines()
            text = "\n".join(lines[1:-1] if lines[-1].startswith("```") else lines[1:])

        try:
            parsed = json.loads(text)
            meaning = parsed.get("meaning", "").strip()
            sentences = parsed.get("sentences", [])

            if not meaning or len(sentences) < 3:
                errors += 1
                continue

            parts = result.custom_id.split("_")
            level_idx, word_idx = int(parts[0]), int(parts[1])
            data["levels"][level_idx]["words"][word_idx]["meaning"] = meaning
            data["levels"][level_idx]["words"][word_idx]["sentences"] = sentences[:3]
            updated += 1

        except (json.JSONDecodeError, ValueError, IndexError):
            errors += 1

    return updated, errors


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    no_scrape = "--no-scrape" in sys.argv
    inspect_mode = "--inspect" in sys.argv

    if inspect_mode:
        idx = sys.argv.index("--inspect")
        word = sys.argv[idx + 1] if idx + 1 < len(sys.argv) else "good"
        inspect_word(word)
        return

    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("Error: ANTHROPIC_API_KEY not set.")
        print("Usage: ANTHROPIC_API_KEY=sk-ant-... python3 update_all_vocabulary.py")
        sys.exit(1)

    client = anthropic.Anthropic(api_key=api_key)

    print(f"Loading: {VOCAB_FILE}")
    with open(VOCAB_FILE, encoding="utf-8") as f:
        data = json.load(f)

    total_words = sum(len(lvl["words"]) for lvl in data["levels"])
    level_counts = {lvl["level"]: len(lvl["words"]) for lvl in data["levels"]}
    print(f"Total words: {total_words}  |  Levels: {level_counts}")

    scraped: dict = {}
    failed: list = []

    # Phase 1 — scrape TalkEnglish
    if not no_scrape:
        scraped, failed = run_scrape_phase(data)
    else:
        print("Skipping scrape phase (--no-scrape).")
        for level_idx, level in enumerate(data["levels"]):
            for word_idx, entry in enumerate(level["words"]):
                failed.append((level_idx, word_idx, entry, level["level"]))

    # Apply scraped results immediately
    scrape_applied = 0
    for key, result in scraped.items():
        parts = key.split("_")
        li, wi = int(parts[0]), int(parts[1])
        data["levels"][li]["words"][wi]["meaning"] = result["meaning"]
        data["levels"][li]["words"][wi]["sentences"] = result["sentences"]
        scrape_applied += 1

    print(f"Applied {scrape_applied} scraped entries.")

    # Phase 2 — Claude API Batches for the rest
    if failed:
        batch_id = None
        if BATCH_ID_FILE.exists():
            saved_id = BATCH_ID_FILE.read_text().strip()
            print(f"\nFound saved batch ID: {saved_id}")
            resume = input("Resume this batch? [y/N]: ").strip().lower()
            if resume == "y":
                batch_id = saved_id
            else:
                BATCH_ID_FILE.unlink()

        if batch_id is None:
            batch_id = submit_batch(client, failed)

        poll_batch(client, batch_id)
        api_updated, api_errors = apply_batch_results(client, batch_id, data)
        print(f"Claude API: {api_updated} updated, {api_errors} errors")
        BATCH_ID_FILE.unlink(missing_ok=True)
    else:
        print("All words covered by scraping — Claude API not needed.")

    print(f"\nSaving to: {OUTPUT_FILE}")
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    # Clean up cache after successful run
    SCRAPE_CACHE_FILE.unlink(missing_ok=True)

    print("\nDone!")
    print(f"Output: {OUTPUT_FILE}")
    print("\nReview, then replace the original:")
    print(f"  cp '{OUTPUT_FILE}' '{VOCAB_FILE}'")


if __name__ == "__main__":
    main()
