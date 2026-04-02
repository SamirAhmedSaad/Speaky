#!/usr/bin/env python3
"""
Generate high-quality example sentences for all vocabulary words in vocabulary.json.
Uses Claude API (claude-haiku-4-5) for cost-efficiency, processing words in batches.

Usage:
    ANTHROPIC_API_KEY=sk-ant-... python3 generate_sentences.py
"""

import json
import os
import time
import sys
from pathlib import Path

try:
    import anthropic
except ImportError:
    print("Error: anthropic package not found. Run: pip install anthropic")
    sys.exit(1)

VOCAB_JSON_PATH = Path(__file__).parent.parent / "composeApp/src/commonMain/composeResources/files/vocabulary.json"
BACKUP_PATH = VOCAB_JSON_PATH.with_suffix(".json.bak")
BATCH_SIZE = 30  # words per API call
MODEL = "claude-haiku-4-5-20251001"


def generate_sentences_for_batch(client: anthropic.Anthropic, words: list[dict]) -> dict[str, list[str]]:
    """Generate 3 example sentences for each word in the batch. Returns {word: [s1, s2, s3]}."""

    word_list = "\n".join(
        f'- "{w["word"]}" ({w["partOfSpeech"]}): {w["meaning"]}'
        for w in words
    )

    prompt = f"""Generate exactly 3 natural example sentences for each English vocabulary word below.

Requirements:
- Sentences must be short (8-15 words), natural, and conversational
- Use the word in a common, everyday context that learners will encounter
- Vary the sentence structures (question, statement, exclamation)
- Sentences should clearly show the meaning of the word in context
- Suitable for English learners (A1-C1 level)

Words:
{word_list}

Respond in JSON format like this (include ALL words, no exceptions):
{{
  "word1": ["Sentence one.", "Sentence two.", "Sentence three."],
  "word2": ["Sentence one.", "Sentence two.", "Sentence three."]
}}

Return ONLY the JSON object, no other text."""

    message = client.messages.create(
        model=MODEL,
        max_tokens=4096,
        messages=[{"role": "user", "content": prompt}],
    )

    response_text = message.content[0].text.strip()

    # Strip markdown code fences if present
    if response_text.startswith("```"):
        lines = response_text.split("\n")
        response_text = "\n".join(lines[1:-1])

    return json.loads(response_text)


def process_vocabulary():
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("Error: ANTHROPIC_API_KEY environment variable not set.")
        print("Usage: ANTHROPIC_API_KEY=sk-ant-... python3 generate_sentences.py")
        sys.exit(1)

    client = anthropic.Anthropic(api_key=api_key)

    print(f"Loading vocabulary from: {VOCAB_JSON_PATH}")
    with open(VOCAB_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    # Backup original
    print(f"Backing up original to: {BACKUP_PATH}")
    with open(BACKUP_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    total_words = sum(len(level["words"]) for level in data["levels"])
    print(f"Total words to process: {total_words}")
    print(f"Batch size: {BATCH_SIZE} | Estimated API calls: {(total_words + BATCH_SIZE - 1) // BATCH_SIZE}\n")

    processed = 0
    failed_words = []

    for level in data["levels"]:
        level_name = level["level"]
        words = level["words"]
        print(f"=== Processing level {level_name} ({len(words)} words) ===")

        # Process in batches
        for i in range(0, len(words), BATCH_SIZE):
            batch = words[i : i + BATCH_SIZE]
            batch_num = i // BATCH_SIZE + 1
            total_batches = (len(words) + BATCH_SIZE - 1) // BATCH_SIZE
            print(f"  Batch {batch_num}/{total_batches} ({len(batch)} words)...", end=" ", flush=True)

            retries = 3
            for attempt in range(retries):
                try:
                    sentences_map = generate_sentences_for_batch(client, batch)

                    # Update sentences for each word in batch
                    for word_entry in batch:
                        word = word_entry["word"]
                        if word in sentences_map and len(sentences_map[word]) >= 1:
                            word_entry["sentences"] = sentences_map[word][:3]
                        else:
                            failed_words.append(word)
                            print(f"\n    Warning: No sentences generated for '{word}'", end="")

                    processed += len(batch)
                    print(f"done ({processed}/{total_words})")
                    break

                except json.JSONDecodeError as e:
                    if attempt < retries - 1:
                        print(f"\n    JSON parse error, retrying ({attempt + 1}/{retries})...", end=" ")
                        time.sleep(2)
                    else:
                        print(f"\n    Failed to parse JSON for batch. Skipping.")
                        failed_words.extend(w["word"] for w in batch)

                except anthropic.RateLimitError:
                    wait = 30
                    print(f"\n    Rate limited. Waiting {wait}s...", end=" ")
                    time.sleep(wait)

                except Exception as e:
                    if attempt < retries - 1:
                        print(f"\n    Error: {e}. Retrying...", end=" ")
                        time.sleep(3)
                    else:
                        print(f"\n    Failed batch: {e}")
                        failed_words.extend(w["word"] for w in batch)

            # Small delay between batches to avoid rate limits
            time.sleep(0.5)

        print()

    # Save updated vocabulary
    print(f"Saving updated vocabulary.json...")
    with open(VOCAB_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("Done!")

    if failed_words:
        print(f"\nWarning: {len(failed_words)} words failed to get new sentences:")
        for w in failed_words:
            print(f"  - {w}")
        print("These words retain their original sentences.")

    print(f"\nSummary: {processed - len(failed_words)}/{total_words} words updated successfully.")


if __name__ == "__main__":
    process_vocabulary()
