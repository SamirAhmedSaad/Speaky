#!/usr/bin/env python3
"""
Fetch example sentences for all vocabulary words using the Free Dictionary API
(api.dictionaryapi.dev) — completely free, no API key required.

Filters out bad sentences (too short, merged alternatives, wrong word form, technical).
For words with no good examples found, the original sentences are kept.

Usage:
    python3 fetch_sentences.py
"""

import json
import re
import time
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path

VOCAB_JSON_PATH = Path(__file__).parent.parent / "composeApp/src/commonMain/composeResources/files/vocabulary.json"
BACKUP_PATH = VOCAB_JSON_PATH.with_suffix(".json.bak")
API_BASE = "https://api.dictionaryapi.dev/api/v2/entries/en/"
DELAY = 0.4       # seconds between requests
MAX_SENTENCES = 3
MIN_LEN = 20      # minimum sentence character length
MAX_LEN = 130     # maximum sentence character length
MIN_WORDS = 4     # minimum word count


def is_good_sentence(sentence: str, word: str) -> bool:
    """Return True if the sentence is suitable for a learner."""
    s = sentence.strip()

    # Too short or too long
    if len(s) < MIN_LEN or len(s) > MAX_LEN:
        return False

    # Too few words
    if len(s.split()) < MIN_WORDS:
        return False

    # Contains merged alternative sentences (e.g. "Do X.   Do Y." or "Do X; Do Y.")
    if re.search(r'\.\s{2,}', s):
        return False
    if re.search(r';\s+[A-Z]', s):  # semicolon followed by capital = merged sentence
        return False

    # Starts with code/markup
    if s.startswith(("{{", "[[", "http", "<", "[")):
        return False

    # Contains pipe characters (wiki markup)
    if "|" in s:
        return False

    # Contains numbers used in technical/math contexts
    if re.search(r'\d+\s*(times|squared|cubed|×)', s, re.I):
        return False

    return True


def clean_sentence(s: str) -> str:
    """Clean up a sentence."""
    s = s.strip()
    # Capitalize first letter
    s = s[0].upper() + s[1:]
    # Ensure ends with punctuation
    if s and s[-1] not in ".!?":
        s += "."
    return s


def fetch_examples(word: str) -> list[str]:
    """Fetch and filter example sentences for a word."""
    url = API_BASE + urllib.parse.quote(word)
    req = urllib.request.Request(url, headers={"User-Agent": "SpeakMind-vocab-builder/1.0"})

    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.loads(r.read())
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return []
        return []
    except Exception:
        return []

    examples = []
    seen_lower = set()

    for entry in data:
        for meaning in entry.get("meanings", []):
            for defn in meaning.get("definitions", []):
                raw = defn.get("example", "").strip()
                if not raw:
                    continue

                cleaned = clean_sentence(raw)

                # Skip duplicates (case-insensitive)
                key = cleaned.lower()
                if key in seen_lower:
                    continue

                if not is_good_sentence(cleaned, word):
                    continue

                seen_lower.add(key)
                examples.append(cleaned)

                if len(examples) >= MAX_SENTENCES:
                    break
            if len(examples) >= MAX_SENTENCES:
                break
        if len(examples) >= MAX_SENTENCES:
            break

    return examples


def process_vocabulary():
    print(f"Loading vocabulary from:\n  {VOCAB_JSON_PATH}\n")
    with open(VOCAB_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    # Backup original (only if no backup exists yet)
    if not BACKUP_PATH.exists():
        print(f"Backing up original to:\n  {BACKUP_PATH}\n")
        with open(BACKUP_PATH, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    else:
        print(f"Backup already exists, skipping.\n")

    total_words = sum(len(level["words"]) for level in data["levels"])
    print(f"Total words: {total_words}\n")
    print("Legend: [fetched N] = new sentences from API | [kept] = kept original\n")

    processed = 0
    updated = 0
    kept_original = []

    for level in data["levels"]:
        level_name = level["level"]
        words = level["words"]
        print(f"=== Level {level_name} ({len(words)} words) ===")

        for word_entry in words:
            word = word_entry["word"]
            processed += 1

            examples = fetch_examples(word)

            if examples:
                word_entry["sentences"] = examples
                updated += 1
                status = f"[fetched {len(examples)}]"
            else:
                kept_original.append(word)
                status = "[kept]"

            print(f"  [{processed:4d}/{total_words}] {word:<22} {status}")
            time.sleep(DELAY)

        # Save progress after each level (so a crash doesn't lose everything)
        with open(VOCAB_JSON_PATH, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"  Progress saved.\n")

    print(f"Done!")
    print(f"  Sentences updated : {updated} words")
    print(f"  Kept original     : {len(kept_original)} words")
    if kept_original:
        print(f"\n  Words with no API examples ({len(kept_original)}):")
        for w in kept_original:
            print(f"    - {w}")


if __name__ == "__main__":
    process_vocabulary()
