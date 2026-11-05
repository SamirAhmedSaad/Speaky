# Building Speaky course lessons from YouTube transcripts

Speaky is an app where Arabic speakers learn English. Each YouTube lesson becomes one lesson page
in the app: word cards, fine lines (similar words compared), expressions, grammar, and a 10-question exam.

## Inputs
- `tools/courses/catalog/plan.json`: one entry per lesson: `course`, `book`, `lesson`, `id` (YouTube id),
  `title` (video title), `out` (output path, relative to `tools/courses/`).
- `tools/courses/catalog/transcripts/<id>.txt`: the video's captions (Arabic and/or English, auto-generated,
  with spelling mistakes - work out what the teacher meant).
- `tools/courses/lessons/b1-l1.json`: a finished lesson from another course. Copy its exact JSON shape.

## Output: one JSON file per lesson at `tools/courses/<out>`

```json
{
  "title": "Short English lesson title, e.g. \"Verb to be\"",
  "content": {
    "vocabulary": [{"word": "", "pos": "noun|verb|adjective|adverb|phrase|...", "meaning_en": "", "meaning_ar": "",
                    "note_ar": "", "note_en": "", "example": ""}],
    "fine_lines": [{"title": "a vs an", "words": ["a", "an"], "explanation_ar": "", "explanation_en": "",
                    "examples": [{"word": "a", "sentence": ""}]}],
    "expressions": [{"phrase": "", "meaning_ar": "", "meaning_en": "", "example": ""}],
    "grammar": [{"title": "", "explanation_ar": "", "explanation_en": "", "examples": [""]}]
  },
  "questions": [{"question": "", "choices": ["", "", "", ""], "answer": 0, "explanation_ar": "", "explanation_en": ""}]
}
```

## Rules
- Teach what THIS video teaches. Read the whole transcript first.
  - Vocabulary: every English word or short phrase the lesson teaches. Word-list videos (e.g. "100 most
    important verbs") get all of them. Normal lessons usually have 15-40.
  - Grammar: each grammar point the teacher explains (verb to be, pronouns, tenses...), explained step by step.
    Empty list only if the video really has no grammar.
  - Fine lines: words/forms learners confuse that this lesson touches (a/an, this/that, have/has, he/she/it,
    in/on/at...). Include every comparison the teacher makes. 0-5.
  - Expressions: useful sentences/phrases from the lesson (greetings, polite requests...). Phrase videos
    get all of their sentences here.
- Every explanation in two versions: Arabic (`*_ar`, clear Modern Standard Arabic) and simple English (`*_en`).
  `note_ar`/`note_en`: pronunciation tips, US/UK, word family; empty strings if nothing useful.
- Write YOUR OWN example sentences and explanations. Do not copy long passages from the video.
- Keep it short, correct and clear for beginners. English must be correct, natural and modest
  (no dating/romance, alcohol, gambling, music-industry topics in examples).
- Exam: exactly 10 questions, exactly 4 choices each, one correct `answer` index (0-3). Mix question types
  (meaning, fill the gap, grammar, choose the correct sentence, fine lines) and spread the correct index
  across 0-3. Only test what the lesson teaches. Each question has both explanations.
- If a transcript is only an intro/summary/motivation video, still build the lesson from what it teaches
  (review lessons: review the earlier material it mentions).
- Valid UTF-8 JSON, `ensure_ascii=False` style (real Arabic letters, not \u escapes).

## Check every file
`python3 tools/courses/catalog/check.py tools/courses/<out>` must print OK. Fix and re-run until it does.
