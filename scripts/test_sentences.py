#!/usr/bin/env python3
"""Quick test: generate sentences for 5 sample words to verify quality before running the full script."""

import json
import os
import sys

try:
    import anthropic
except ImportError:
    print("Error: anthropic package not found.")
    sys.exit(1)

api_key = os.environ.get("ANTHROPIC_API_KEY")
if not api_key:
    print("Error: ANTHROPIC_API_KEY environment variable not set.")
    print("Usage: ANTHROPIC_API_KEY=sk-ant-... python3 test_sentences.py")
    sys.exit(1)

client = anthropic.Anthropic(api_key=api_key)

test_words = [
    {"word": "people", "partOfSpeech": "noun", "meaning": "human beings in general"},
    {"word": "film", "partOfSpeech": "noun", "meaning": "a movie or motion picture"},
    {"word": "excited", "partOfSpeech": "adjective", "meaning": "feeling or showing enthusiasm"},
    {"word": "suggest", "partOfSpeech": "verb", "meaning": "to propose an idea or plan"},
    {"word": "probably", "partOfSpeech": "adverb", "meaning": "almost certainly; very likely"},
]

word_list = "\n".join(f'- "{w["word"]}" ({w["partOfSpeech"]}): {w["meaning"]}' for w in test_words)

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

print("Generating test sentences...\n")
message = client.messages.create(
    model="claude-haiku-4-5-20251001",
    max_tokens=1024,
    messages=[{"role": "user", "content": prompt}],
)

response = message.content[0].text.strip()
if response.startswith("```"):
    lines = response.split("\n")
    response = "\n".join(lines[1:-1])

result = json.loads(response)
for word, sentences in result.items():
    print(f'"{word}":')
    for i, s in enumerate(sentences, 1):
        print(f"  {i}. {s}")
    print()

print("Test passed! Run generate_sentences.py to process all 2075 words.")
