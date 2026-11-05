#!/usr/bin/env python3
"""Main article text of a VOA / BBC Learning English page: python3 webtext.py URL"""
import html
import re
import sys
import urllib.request


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=30).read().decode("utf-8", "replace")


def article_text(page):
    page = re.sub(r"(?is)<(script|style|noscript)[^>]*>.*?</\1>", " ", page)
    m = re.search(r'(?is)<div[^>]+(?:id="article-content"|class="[^"]*\bwsw\b[^"]*")[^>]*>(.*)', page)  # VOA
    if not m:
        m = re.search(r'(?is)<div[^>]+class="[^"]*widget-richtext[^"]*"[^>]*>(.*)', page)  # BBC
    body = m.group(1) if m else page
    body = re.sub(r"(?i)<(br|/p|/h\d|/li|/tr)[^>]*>", "\n", body)
    text = html.unescape(re.sub(r"<[^>]+>", " ", body))
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n\s*\n+", "\n", text).strip()
    for stop in ("Comments", "Related", "Share this"):
        i = text.find("\n" + stop + "\n")
        if i > 2000:
            text = text[:i]
    return text


if __name__ == "__main__":
    print(article_text(fetch(sys.argv[1])))
