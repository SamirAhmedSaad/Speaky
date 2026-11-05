# Reviewing Speaky lessons (quality check before users see them)

Each lesson JSON (format in `BRIEF.md`) is shown to Arabic-speaking English learners. Check every field and
fix mistakes directly in the file:

1. **Vocabulary:** `meaning_ar` is the correct Arabic meaning of `word` in this lesson's sense; `meaning_en`
   is correct simple English; `pos` is right; `example` is natural, correct English using the word.
   Notes (`note_ar`/`note_en`) are true (pronunciation/IPA, spelling, US/UK).
2. **Fine lines, expressions, grammar:** the Arabic and English explanations are correct, say the same
   thing, and are clear Modern Standard Arabic / simple English. Grammar rules are accurate. Examples are
   grammatical and match the rule.
3. **Exam:** for every question, `choices[answer]` is really the one correct answer, no other choice is also
   correct, the question is unambiguous, and both explanations are right. Fix the question, choices or
   `answer` index if needed (keep 4 different choices).
4. Spelling and grammar in all English; spelling and grammar in all Arabic (correct letters, no broken text).
5. Modest content only (no romance/dating, alcohol, gambling, music/singing in examples).

Keep the JSON shape. After editing a file run `python3 tools/courses/catalog/check.py <file>` until OK.
Do not touch files outside your list. Reply with: files reviewed, and for each file you changed one line
saying what you fixed (e.g. "b1-l7: q4 answer was 2, should be 1; vocab 'bank' meaning_ar fixed").
