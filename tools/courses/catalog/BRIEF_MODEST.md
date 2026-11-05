# Modest courses (Singsing, BBC Storytellers, English Together, James, Harry): extra instructions

Read `BRIEF.md` (lesson JSON format, rules) and `REVIEW.md` (quality checklist) first. `check.py` is the same.
Copy the JSON shape of a finished lesson, e.g. `tools/courses/lessons/bbc-english-at-work/b1-l1.json`.

## Your lessons
Your todo file lists your lessons in order: `course`, `book`, `lesson`, `id` (YouTube id), `title`, `out`.
Singsing entries also have `also_id` / `also_title`: the lesson's second video (part B). Build ONE lesson
from both transcripts (A and B); the lesson's `id` stays the A video.

## Source text: the YouTube transcript panel (built-in browser)
YouTube blocks caption downloads from this machine (yt-dlp, curl). Do NOT try to get around that.
Read the transcript the normal way, in the built-in browser (tools `mcp__Claude_Browser__*`):

1. Once, at the start: `mcp__Claude_Browser__tabs_create` and keep its `tabId`. Pass that `tabId` on EVERY
   browser call. Other agents use other tabs at the same time: never touch, select or close their tabs.
2. Per video: `mcp__Claude_Browser__navigate` (with your tabId) to `https://www.youtube.com/watch?v=<id>`, then
   `mcp__Claude_Browser__javascript_tool` (with your tabId) with:

```js
await new Promise(r=>setTimeout(r,3000));
document.querySelector('video')?.pause();
const find=()=>[...document.querySelectorAll('button')].find(b=>/show transcript/i.test(b.innerText||b.getAttribute('aria-label')||''));
if(!find()){document.querySelector('#expand')?.click(); await new Promise(r=>setTimeout(r,800));}
find()?.click(); await new Promise(r=>setTimeout(r,3500));
const segs=[...document.querySelectorAll('ytd-transcript-segment-renderer .segment-text, transcript-segment-view-model span')].map(s=>s.innerText.trim()).filter(Boolean);
const desc=document.querySelector('#description-inline-expander, #description')?.innerText?.slice(0,1500)||'';
({title:document.title, n:segs.length, text:segs.join('\n'), desc})
```

   If `n` is 0, wait and run it once more. If still 0, use the description, and for James lessons the engVid
   page (search https://www.engvid.com for the title; read it with WebFetch). If nothing usable, skip the
   lesson and say so in your report.
3. Save the transcript text to `tools/courses/catalog/transcripts/<id>.txt` (Write tool).
Do not sign in, click ads, like, subscribe or comment. Dismiss nothing except what blocks the transcript.

## Content rules for these courses
- Arabic (`*_ar`) for Arabic-speaking learners; English (`*_en`) simple.
- Beginner courses (Singsing, BBC Storytellers, English Together book 1): very simple English in explanations
  and examples; vocabulary = the words in the dialogue/story (8-25); expressions = the dialogue's useful
  sentences; grammar = the pattern the dialogue practises (e.g. "How old are you? I'm seven." -> asking age,
  verb to be). Exams test those words and sentences.
- James / Harry: vocabulary, expressions and grammar that the teacher actually teaches in the video.
- The lesson `title`: short and clear English, e.g. "How old are you?", "At the Airport", "Articles: a, an, the".
- Modest only: no romance/dating, alcohol, gambling, music/singing, pork, immodest clothing talk, LGBT themes.
  If a video is mainly about such a topic, SKIP it (write nothing) and list it in your report. If only a line
  is unsuitable, just leave that part out.
- Never invent dialogue the video doesn't have; your own example sentences are fine.

## When a lesson file is done
Run `python3 tools/courses/catalog/check.py <file>` until OK, then self-review it with `REVIEW.md`.

## Finish
Write `tools/courses/catalog/plan-<your-part>.json` (part name given in your task): the list of todo entries for
the lessons you actually wrote (same fields, drop `also_*`), in todo order. Reply with: lessons written, lessons
skipped (with reason), and anything odd.
