#!/usr/bin/env python3
"""Print a video's captions (English, else Arabic): python transcript.py VIDEO_ID"""
import sys
from youtube_transcript_api import YouTubeTranscriptApi

fetched = YouTubeTranscriptApi().fetch(sys.argv[1], languages=["en", "ar"])
print(" ".join(s.text for s in fetched))
