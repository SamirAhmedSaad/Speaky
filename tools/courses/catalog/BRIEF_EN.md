# English-taught courses (VOA / BBC): extra instructions

Read `BRIEF.md` first: the lesson JSON format, the rules and `check.py` are the same. Differences:

## Source text
The teachers speak English, so build each lesson from the official lesson page, not YouTube captions
(YouTube blocks caption downloads from this machine):
- Fetch a page's main text with `python3 tools/courses/catalog/webtext.py URL` (plain urllib, no browser).
- Save the text you used to `tools/courses/catalog/web/<youtube_id>.txt` (create the folder if needed).
- VOA Let's Learn English: lesson list pages on https://learningenglish.voanews.com (Level 1 list:
  https://learningenglish.voanews.com/p/5644.html; find the Level 2 list from the site). Each lesson page has
  the conversation script, often "New Words" and a grammar note.
- BBC Learning English: https://www.bbc.co.uk/learningenglish/ - feature pages such as
  `english/features/the-english-we-speak`, `english/features/english-at-work`, with full transcripts and
  vocabulary per episode. If a page can't be found, use the YouTube video description:
  `yt-dlp --skip-download --print "%(description)s" "https://www.youtube.com/watch?v=ID"`.
- Never invent dialogue the video doesn't have. If you truly can't find the content of an episode, skip it
  and say so in your report.

## Video ids
The YouTube playlists are already saved: `tools/courses/catalog/voa-lle.json`, `bbc-eec.json`,
`bbc-ews.json`, `bbc-work.json` (id, duration, title). Match each lesson to its YouTube video by title /
lesson number. Every lesson needs a YouTube id (the app plays the video on YouTube).

## Explanations for these courses
- `meaning_ar` / `*_ar`: Arabic for Arabic speakers (our users); `*_en`: simple English.
- Vocabulary: the words/phrases the episode teaches + key words from the script (15-40).
- Expressions: useful sentences from the dialogue (or the idioms the episode teaches).
- Grammar: the grammar point the lesson practises (VOA usually has one per lesson).
- Keep examples modest (no dating/romance, alcohol, gambling).

## Your plan file
Write `tools/courses/catalog/plan-<your-part>.json`: a list of
`{"course": "<slug>", "book": N, "lesson": N, "id": "<youtube id>", "title": "<video title>", "out": "lessons/<slug>/b<N>-l<N>.json"}`
for every lesson you wrote, in order. Lesson numbers start at 1 in each book, in the course's real order.
