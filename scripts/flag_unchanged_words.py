#!/usr/bin/env python3
"""
After running fetch_sentences.py, this script compares the updated vocabulary.json
with the backup (.json.bak) and flags words whose sentences were NOT updated
(i.e. API had no examples — these need manual review).

Output: scripts/flagged_words.json — list of words to edit later.

Usage:
    python3 flag_unchanged_words.py
"""

import json
from pathlib import Path

VOCAB_JSON_PATH = Path(__file__).parent.parent / "composeApp/src/commonMain/composeResources/files/vocabulary.json"
BACKUP_PATH = VOCAB_JSON_PATH.with_suffix(".json.bak")
OUTPUT_PATH = Path(__file__).parent / "flagged_words.json"


def main():
    if not BACKUP_PATH.exists():
        print(f"Error: backup file not found at {BACKUP_PATH}")
        print("Run fetch_sentences.py first.")
        return

    with open(VOCAB_JSON_PATH, "r", encoding="utf-8") as f:
        updated = json.load(f)
    with open(BACKUP_PATH, "r", encoding="utf-8") as f:
        original = json.load(f)

    # Build lookup: word -> original sentences
    original_sentences = {}
    for level in original["levels"]:
        for word_entry in level["words"]:
            original_sentences[word_entry["word"]] = word_entry["sentences"]

    flagged = []

    for level in updated["levels"]:
        for word_entry in level["words"]:
            word = word_entry["word"]
            current = word_entry["sentences"]
            orig = original_sentences.get(word, [])

            if current == orig:
                flagged.append({
                    "word": word,
                    "level": level["level"],
                    "partOfSpeech": word_entry.get("partOfSpeech", ""),
                    "meaning": word_entry.get("meaning", ""),
                    "current_sentences": current,
                    "needs_review": True
                })

    flagged.sort(key=lambda x: (x["level"], x["word"]))

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(flagged, f, ensure_ascii=False, indent=2)

    print(f"Total words flagged for review: {len(flagged)}")
    print(f"Saved to: {OUTPUT_PATH}\n")

    by_level = {}
    for w in flagged:
        by_level.setdefault(w["level"], []).append(w["word"])

    for level, words in sorted(by_level.items()):
        print(f"  {level}: {len(words)} words")
        for w in words:
            print(f"    - {w}")
        print()


if __name__ == "__main__":
    main()
