#!/usr/bin/env python3
"""
Comprehensive vocabulary scraper for SpeakMind.

Scrapes all 2,075 words from TalkEnglish/englishcollocation.com, extracts
meanings and all example sentences (grouped by collocation category when
available), saves failed words for later retry, then runs a quality audit.

Requirements:
    pip install requests beautifulsoup4

Usage:
    python3 scrape_vocabulary.py                    # full run
    python3 scrape_vocabulary.py --inspect good     # preview one word
    python3 scrape_vocabulary.py --retry            # retry failed_words.json

Outputs:
    vocabulary_updated.json   clean updated vocabulary
    failed_words.json         words whose pages could not be scraped
    quality_report.txt        quality audit of every word
"""

import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import quote

try:
    import requests
    from bs4 import BeautifulSoup
except ImportError:
    print("Missing packages. Run: pip install requests beautifulsoup4")
    sys.exit(1)

# ── Paths ──────────────────────────────────────────────────────────────────
SCRIPTS_DIR  = Path(__file__).parent
VOCAB_FILE   = SCRIPTS_DIR.parent / "composeApp/src/commonMain/composeResources/files/vocabulary.json"
OUTPUT_FILE  = VOCAB_FILE.parent / "vocabulary_updated.json"
FAILED_FILE  = SCRIPTS_DIR / "failed_words.json"
REPORT_FILE  = SCRIPTS_DIR / "quality_report.txt"
CACHE_FILE   = SCRIPTS_DIR / ".scrape_cache.json"

BASE_URL = "http://www.englishcollocation.com/how-to-use/{}"
DELAY    = 0.35   # seconds between requests
TIMEOUT  = 12     # request timeout in seconds

POS_MAP = {
    "noun": "n", "verb": "v", "adjective": "adj", "adverb": "adv",
    "preposition": "prep", "conjunction": "conj", "pronoun": "pron",
    "interjection": "interj", "determiner": "det", "article": "art",
    "modal": "modal", "auxiliary": "aux",
}

BAD_MEANING_PATTERNS = [
    r"^a common english word",
    r"^common english word",
    r"^used in everyday",
    r"^a word used",
    r"^an english word",
]


# ── HTML Parsing ───────────────────────────────────────────────────────────

def _clean(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _parse_div(div, word_lower: str) -> tuple[str | None, dict[str, list[str]], list[str]]:
    """
    Parse one sm2-playlist-bd div.

    Confirmed link order inside the div:
      [0] the word itself
      [1] definition text
      [2] "Listen to all"
      [3] "All sentences (with pause)"
      [4+] example sentences

    Group headers sit between sentences as: <b><u>Used with adjectives:</u></b>
    Note: BeautifulSoup places all <a> tags as in_table=True due to malformed
    HTML, so we filter by content rather than DOM position.
    """
    SKIP = {"listen to all", "all sentences (with pause)"}

    all_links = div.find_all("a")
    if len(all_links) < 2:
        return None, {}, []

    meaning = _clean(all_links[1].get_text(" "))
    meaning = meaning if len(meaning) >= 2 else None

    current_group = "General"
    grouped: dict[str, list[str]] = {}
    flat: list[str] = []
    link_count = 0

    for node in div.descendants:
        if not hasattr(node, "name"):
            continue

        # Group header: <b><u>Used with adjectives:</u></b>
        if node.name == "u" and node.parent and node.parent.name == "b":
            header = _clean(node.get_text())
            if "used with" in header.lower():
                current_group = header.rstrip(":")
                grouped.setdefault(current_group, [])

        elif node.name == "a":
            link_count += 1
            if link_count <= 4:         # skip: word, definition, listen-all, all-sentences
                continue
            text = _clean(node.get_text(" "))
            if not text or text.lower() in SKIP:
                continue
            if 15 < len(text) < 250 and text not in flat:
                flat.append(text)
                grouped.setdefault(current_group, [])
                grouped[current_group].append(text)

    return meaning, grouped, flat


def scrape_word(session: requests.Session, word: str, pos: str = "") -> dict | None:
    """
    Scrape one word page. Returns:
        {meaning, sentences (ALL found), grouped}
    or None if the page could not be scraped.
    """
    slug = quote(word.lower().replace(" ", "-"))
    try:
        resp = session.get(BASE_URL.format(slug), timeout=TIMEOUT)
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
    pos_short  = POS_MAP.get(pos.lower(), pos.lower()[:3]) if pos else ""

    # Match the div to the word's part of speech when possible
    target_div = playlist_divs[0]
    if pos_short and len(playlist_divs) > 1:
        for div in playlist_divs:
            if f"({pos_short})" in div.get_text():
                target_div = div
                break

    meaning, grouped, flat = _parse_div(target_div, word_lower)

    # Always collect sentences from all other POS divs too
    for div in playlist_divs:
        if div is target_div:
            continue
        m, g, f = _parse_div(div, word_lower)
        if not meaning and m:
            meaning = m
        for s in f:
            if s not in flat:
                flat.append(s)
        for grp, sents in g.items():
            grouped.setdefault(grp, [])
            for s in sents:
                if s not in grouped[grp]:
                    grouped[grp].append(s)

    if not meaning:
        return None

    return {
        "meaning" : meaning,
        "sentences": flat,        # all sentences, no cap
        "grouped" : grouped,
    }


# ── Scrape Loop ────────────────────────────────────────────────────────────

def run_scrape(data: dict, words_to_process: list | None = None) -> tuple[int, int, list]:
    """
    Main scrape loop. Updates data in-place.
    Returns (succeeded, failed_count, failed_list).
    failed_list items: (level_idx, word_idx, entry, level_name)
    """
    cache: dict = {}
    if CACHE_FILE.exists():
        try:
            cache = json.loads(CACHE_FILE.read_text())
            hits = sum(1 for v in cache.values() if v)
            print(f"Cache loaded: {len(cache)} entries, {hits} successful.")
        except Exception:
            pass

    session = requests.Session()
    session.headers["User-Agent"] = "Mozilla/5.0 (compatible; VocabBot/1.0)"

    if words_to_process is None:
        words_to_process = [
            (li, wi, entry, level["level"])
            for li, level in enumerate(data["levels"])
            for wi, entry in enumerate(level["words"])
        ]

    total      = len(words_to_process)
    succeeded  = 0
    failed_list: list = []

    print(f"\nScraping {total} words...\n")

    for i, (li, wi, entry, level_name) in enumerate(words_to_process, 1):
        word = entry["word"]
        pct  = i / total * 100

        print(f"  [{i:4}/{total}  {pct:5.1f}%]  {level_name}  {word:<25}", end="", flush=True)

        # Use cache if available
        if word in cache:
            result = cache[word]
            if result:
                data["levels"][li]["words"][wi]["meaning"]   = result["meaning"]
                data["levels"][li]["words"][wi]["sentences"] = result["sentences"]
                succeeded += 1
                count = len(result["sentences"])
                print(f"cached ✓  ({count} sentences)")
            else:
                failed_list.append((li, wi, entry, level_name))
                print("cached ✗")
            continue

        result = scrape_word(session, word, entry.get("partOfSpeech", ""))
        cache[word] = result

        if result:
            data["levels"][li]["words"][wi]["meaning"]   = result["meaning"]
            data["levels"][li]["words"][wi]["sentences"] = result["sentences"]
            succeeded += 1
            grp_count  = len(result["grouped"])
            sent_count = len(result["sentences"])
            print(f"✓  {sent_count} sentences across {grp_count} group(s)")
        else:
            failed_list.append((li, wi, entry, level_name))
            print("✗  page not found / no content")

        if i % 50 == 0:
            CACHE_FILE.write_text(json.dumps(cache, ensure_ascii=False, indent=2))

        time.sleep(DELAY)

    CACHE_FILE.write_text(json.dumps(cache, ensure_ascii=False, indent=2))
    return succeeded, len(failed_list), failed_list


# ── Quality Audit ──────────────────────────────────────────────────────────

def quality_audit(data: dict) -> list[dict]:
    issues = []
    for level in data["levels"]:
        for entry in level["words"]:
            word      = entry["word"]
            lvl       = level["level"]
            meaning   = entry.get("meaning", "")
            sentences = entry.get("sentences", [])

            if not meaning:
                issues.append(dict(word=word, level=lvl, issue="no_meaning", detail=""))
            elif len(meaning.strip()) < 3:
                issues.append(dict(word=word, level=lvl, issue="meaning_too_short", detail=repr(meaning)))
            else:
                for pat in BAD_MEANING_PATTERNS:
                    if re.match(pat, meaning.strip().lower()):
                        issues.append(dict(word=word, level=lvl, issue="placeholder_meaning", detail=repr(meaning)))
                        break

            if not sentences:
                issues.append(dict(word=word, level=lvl, issue="no_sentences", detail=""))
            elif len(sentences) < 3:
                issues.append(dict(word=word, level=lvl, issue="few_sentences", detail=f"{len(sentences)} sentence(s)"))

    return issues


def write_report(issues: list[dict], total: int, succeeded: int, failed_count: int) -> None:
    lines = []
    sep = "=" * 65

    lines += [
        sep,
        "  SPEAKMIND VOCABULARY — QUALITY REPORT",
        sep,
        f"  Total words   : {total}",
        f"  Scraped OK    : {succeeded}",
        f"  Scrape failed : {failed_count}",
        f"  Quality issues: {len(issues)}",
        "",
    ]

    by_type: dict[str, list] = {}
    for issue in issues:
        by_type.setdefault(issue["issue"], []).append(issue)

    issue_labels = {
        "no_meaning"        : "NO MEANING",
        "meaning_too_short" : "MEANING TOO SHORT",
        "placeholder_meaning": "PLACEHOLDER MEANING",
        "no_sentences"      : "NO SENTENCES",
        "few_sentences"     : "FEWER THAN 3 SENTENCES",
    }

    for key, label in issue_labels.items():
        if key not in by_type:
            continue
        items = by_type[key]
        lines.append(f"\n── {label} ({len(items)}) " + "─" * (50 - len(label)))
        for item in items:
            detail = f"  ← {item['detail']}" if item["detail"] else ""
            lines.append(f"  {item['level']:3}  {item['word']:<28}{detail}")

    lines += ["", sep]

    text = "\n".join(lines)
    REPORT_FILE.write_text(text, encoding="utf-8")
    print(text)


# ── Inspect Mode ───────────────────────────────────────────────────────────

def inspect(word: str) -> None:
    session = requests.Session()
    session.headers["User-Agent"] = "Mozilla/5.0 (compatible; VocabBot/1.0)"
    url = BASE_URL.format(quote(word.lower()))
    print(f"URL: {url}\n")
    result = scrape_word(session, word)
    if not result:
        print("No result — page not found or no content.")
        return
    print(f"Meaning  : {result['meaning']}")
    print(f"Sentences: {len(result['sentences'])} total across {len(result['grouped'])} group(s)")
    print()
    for grp, sents in result["grouped"].items():
        print(f"  [{grp}]")
        for s in sents:
            print(f"    • {s}")


# ── Entry Point ────────────────────────────────────────────────────────────

def main():
    args = sys.argv[1:]

    # --inspect WORD
    if "--inspect" in args:
        idx  = args.index("--inspect")
        word = args[idx + 1] if idx + 1 < len(args) else "good"
        inspect(word)
        return

    # In retry mode, load the already-scraped output so the quality audit is accurate
    source = OUTPUT_FILE if ("--retry" in args and OUTPUT_FILE.exists()) else VOCAB_FILE
    print(f"Loading: {source}")
    with open(source, encoding="utf-8") as f:
        data = json.load(f)

    total  = sum(len(l["words"]) for l in data["levels"])
    counts = {l["level"]: len(l["words"]) for l in data["levels"]}
    print(f"Total: {total} words  |  {counts}")

    words_to_process = None

    # --retry: re-scrape only previously failed words
    if "--retry" in args:
        if not FAILED_FILE.exists():
            print("No failed_words.json found. Nothing to retry.")
            return
        failed_data = json.loads(FAILED_FILE.read_text())
        words_to_process = []
        for item in failed_data:
            li    = item["level_idx"]
            wi    = item["word_idx"]
            entry = data["levels"][li]["words"][wi]
            words_to_process.append((li, wi, entry, item["level"]))
        # Clear cache for these words so they get re-fetched
        if CACHE_FILE.exists():
            cache = json.loads(CACHE_FILE.read_text())
            for (_, _, entry, _) in words_to_process:
                cache.pop(entry["word"], None)
            CACHE_FILE.write_text(json.dumps(cache, ensure_ascii=False, indent=2))
        print(f"Retrying {len(words_to_process)} failed words...")

    # ── Phase 1: Scrape ──────────────────────────────────────────────────
    succeeded, failed_count, failed_list = run_scrape(data, words_to_process)

    # Scraping summary
    print("\n" + "=" * 65)
    print("  SCRAPING COMPLETE")
    print("=" * 65)
    print(f"  Succeeded : {succeeded}")
    print(f"  Failed    : {failed_count}")

    if failed_list:
        print(f"\n  Words that could not be scraped:")
        for li, wi, entry, lvl in failed_list:
            print(f"    [{lvl}]  {entry['word']}")
        failed_json = [
            {
                "word"       : entry["word"],
                "level"      : lvl,
                "level_idx"  : li,
                "word_idx"   : wi,
                "partOfSpeech": entry.get("partOfSpeech", ""),
            }
            for li, wi, entry, lvl in failed_list
        ]
        FAILED_FILE.write_text(json.dumps(failed_json, ensure_ascii=False, indent=2))
        print(f"\n  Saved to: {FAILED_FILE}")
        print(f"  Fix or ignore, then run: python3 scrape_vocabulary.py --retry")

    # ── Save vocabulary ───────────────────────────────────────────────────
    print(f"\nSaving: {OUTPUT_FILE}")
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    # ── Phase 2: Quality Audit ────────────────────────────────────────────
    print("\n" + "=" * 65)
    print("  RUNNING QUALITY AUDIT ON ALL WORDS...")
    print("=" * 65 + "\n")

    issues = quality_audit(data)
    write_report(issues, total, succeeded, failed_count)
    print(f"\nReport saved: {REPORT_FILE}")

    # Clean cache only if everything succeeded
    if failed_count == 0:
        CACHE_FILE.unlink(missing_ok=True)

    print("\n" + "=" * 65)
    print("  DONE")
    print("=" * 65)
    if failed_count > 0:
        print(f"  → {failed_count} words still need attention.")
        print(f"    After fixing, run: python3 scrape_vocabulary.py --retry")
    if issues:
        print(f"  → {len(issues)} quality issues found. See: {REPORT_FILE}")
    print(f"\n  Review output, then apply:")
    print(f"    cp '{OUTPUT_FILE}' '{VOCAB_FILE}'")
    print()


if __name__ == "__main__":
    main()
