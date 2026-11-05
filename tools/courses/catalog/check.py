#!/usr/bin/env python3
"""Validate lesson JSON files: python3 check.py FILE..."""
import json
import sys

VOCAB = {"word", "pos", "meaning_en", "meaning_ar", "example"}  # note_ar / note_en optional
FINE = {"title", "words", "explanation_ar", "explanation_en", "examples"}
EXPR = {"phrase", "meaning_ar", "meaning_en", "example"}
GRAM = {"title", "explanation_ar", "explanation_en", "examples"}
QUES = {"question", "choices", "answer", "explanation_ar", "explanation_en"}


def has_arabic(text):
    return any("؀" <= c <= "ۿ" for c in text)


def check(path):
    errors = []
    d = json.load(open(path, encoding="utf-8"))
    c = d.get("content", {})
    if not d.get("title"):
        errors.append("missing title")
    for name, keys, items in [("vocabulary", VOCAB, c.get("vocabulary", [])), ("fine_lines", FINE, c.get("fine_lines", [])),
                              ("expressions", EXPR, c.get("expressions", [])), ("grammar", GRAM, c.get("grammar", []))]:
        for i, item in enumerate(items):
            missing = keys - item.keys()
            if missing:
                errors.append(f"{name}[{i}] missing {sorted(missing)}")
            for k in keys:
                if k.endswith("_ar") and k != "note_ar" and not has_arabic(str(item.get(k, ""))):
                    errors.append(f"{name}[{i}].{k} has no Arabic")
                if k.endswith("_en") and k != "note_en" and not str(item.get(k, "")).strip():
                    errors.append(f"{name}[{i}].{k} empty")
    if not c.get("vocabulary") and not c.get("expressions"):
        errors.append("no vocabulary and no expressions")
    qs = d.get("questions", [])
    if len(qs) != 10:
        errors.append(f"{len(qs)} questions, need 10")
    answers = set()
    for i, q in enumerate(qs):
        missing = QUES - q.keys()
        if missing:
            errors.append(f"q{i} missing {sorted(missing)}")
            continue
        if len(q["choices"]) != 4 or len(set(q["choices"])) != 4:
            errors.append(f"q{i} needs 4 different choices")
        if not isinstance(q["answer"], int) or not 0 <= q["answer"] <= 3:
            errors.append(f"q{i} bad answer index")
        answers.add(q["answer"])
        if not has_arabic(q["explanation_ar"]):
            errors.append(f"q{i} explanation_ar has no Arabic")
    if len(qs) == 10 and len(answers) < 3:
        errors.append("correct answers not spread across positions")
    return errors


ok = True
for path in sys.argv[1:]:
    errs = check(path)
    if errs:
        ok = False
        print(f"{path}: FAIL")
        for e in errs[:20]:
            print("  -", e)
    else:
        print(f"{path}: OK")
sys.exit(0 if ok else 1)
