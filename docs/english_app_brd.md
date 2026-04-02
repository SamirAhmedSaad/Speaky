# Business Requirements Document (BRD)
## Offline AI English Learning App — "SpeakMind"

**Version:** 3.0
**Date:** March 2026
**Status:** Draft

---

## 1. Executive Summary

SpeakMind is a fully offline, AI-powered English learning mobile application for iOS and Android. It enables learners to practice English through natural voice and text conversations with an on-device AI tutor. No login, no subscription, no internet connection required after the model downloads on first launch. Open the app and start learning immediately.

The entire product philosophy is: **get out of the user's way and let them learn.**

---

## 2. Problem Statement

Most English learning apps are:
- Cloud-dependent (no offline support)
- Full of paywalls and login screens before any value is delivered
- Scripted and repetitive (not adaptive)
- Lacking real conversational practice
- Requiring manual effort to save and review vocabulary

SpeakMind removes every barrier. No account. No subscription. No internet after setup. Just open and talk.

---

## 3. Goals & Objectives

| # | Goal | Success Metric |
|---|------|---------------|
| G1 | Zero friction to first conversation | User starts talking within 60 seconds of first launch |
| G2 | Run fully offline after model download | 100% features available without internet |
| G3 | Natural AI conversation | User satisfaction ≥ 4.5/5 in conversation quality |
| G4 | Daily engagement | Users return daily without being pushed |
| G5 | Voice-first experience | ≥ 70% of interactions via voice |
| G6 | Fast vocabulary retention | Users retain ≥ 80% of saved words after 30 days |
| G7 | Zero friction saving | Users save vocabulary without leaving conversation flow |

---

## 4. Target Users

| Segment | Description |
|---------|-------------|
| **Beginner** | Little English knowledge, needs guided conversations |
| **Intermediate** | Can communicate but wants fluency and confidence |
| **Advanced** | Wants accent refinement, idioms, natural expression |
| **Travelers** | Need offline English with no connectivity |
| **Students** | Exam prep (IELTS, TOEFL, B1/B2) with no tutor budget |
| **Anyone** | No account required — anyone can pick it up and use it |

---

## 5. App Structure

### 5.1 Screens (MVP — 3 screens only)

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│             │     │             │     │             │
│   Splash    │────▶│    Home     │────▶│    Chat     │
│             │     │             │     │             │
└─────────────┘     └─────────────┘     └─────────────┘
```

No login screen. No signup screen. No paywall. Three screens and the user is learning.

### 5.2 Splash Screen
- App logo and name
- Shows model download progress on first launch only
- One-time download: ~2GB over WiFi (~3–5 minutes)
- After download: never shown again — app opens directly to Home
- Simple message: *"Downloading your AI tutor… this happens once."*

### 5.3 Home Screen
The home screen has one job — get the user into a conversation fast.

**What it shows:**
- A warm greeting: *"What do you want to talk about today?"*
- 20 daily conversation cards (scrollable) — each shows topic, category, and difficulty badge
- A "Free Talk" button — start talking about anything
- A "Review Flashcards" button with a badge showing cards due today
- Daily streak counter (simple, no account needed — stored locally)
- No news feed, no ads, no social features, no distractions

**The 20 daily conversation topics rotate every day automatically based on:**
- User's current level (detected from previous sessions)
- Categories not practiced recently
- Weak vocabulary areas from the Mistakes deck

### 5.4 Chat Screen
The main screen where all learning happens. Clean and minimal.

**Layout:**
- Chat bubbles (AI on left, user on right)
- Large microphone button at bottom center
- Text input field for users who prefer typing
- Subtle correction cards that appear inline after mistakes
- A small "saved" toast notification when a flashcard is created

**What the AI does every turn:**
- Responds naturally in plain conversational English
- Pitches language just above the user's level (i+1 principle)
- Gently corrects mistakes at the end of its reply
- Always ends with a follow-up question to keep conversation going
- Listens for voice trigger phrases at all times

---

## 6. Core Features

### 6.1 Daily Conversations (20/day)
- 20 pre-designed conversation scenarios presented daily
- Each scenario has a topic, difficulty level, and AI opening line
- AI initiates with a natural question to start the dialogue
- User responds via voice or text
- Scenarios are designed with mild emotional stakes to boost memory

| Category | Example Topics |
|----------|---------------|
| Daily Life | Morning routines, shopping, cooking |
| Travel | Airports, hotels, asking for directions |
| Work | Meetings, emails, job interviews |
| Social | Making friends, small talk, opinions |
| News & Opinions | Current events, debates |
| Emergency | Doctors, police, urgent situations |
| Business English | Negotiations, presentations, reports |
| Idioms & Slang | Casual expressions, pop culture |

Emotional stakes example: *"You're at the airport and your flight is boarding in 10 minutes — ask for directions to Gate 22."* Real stakes cause the brain to encode vocabulary faster than neutral drills.

### 6.2 Free Talk Mode
- User starts a conversation about any topic with no script
- AI responds naturally and adapts to whatever direction the user takes
- No time limit
- Ideal for intermediate and advanced users

### 6.3 Voice Interaction (Fully Offline)

| Component | Technology | Platform |
|-----------|-----------|---------|
| Speech-to-Text | `SFSpeechRecognizer` on-device | iOS |
| Speech-to-Text | Android `SpeechRecognizer` + offline pack | Android |
| Text-to-Speech | `AVSpeechSynthesizer` | iOS |
| Text-to-Speech | Android `TextToSpeech` API | Android |
| Voice Activity Detection | WebRTC VAD local library | Both |
| Trigger Detection | Keyword match on STT transcript | Both |

- Tap-to-speak or always-listening mode (user toggle)
- AI response read aloud with natural pacing
- Adjustable AI voice speed (0.5x — 1.5x)
- Accent options: American, British, Australian

### 6.4 Natural Language Voice Triggers

The app listens for natural phrases within any conversation. Detection happens by keyword matching on the STT transcript — zero extra latency, fully offline, no button tapping needed.

| What the user says | What the app does |
|---|---|
| "save this" / "add to my cards" | Creates flashcard: word + sentence + audio |
| "I want to remember this" | Same as above |
| "repeat that slowly" | Re-speaks last AI sentence at 0.7x speed |
| "what does [word] mean?" | Explains in simple English, auto-saves word |
| "how do you spell [word]?" | Spells letter by letter, shows on screen |
| "give me an example" | Generates 3 example sentences, speaks them |
| "was that correct?" | Gives grammar + naturalness score 1–10 |
| "too fast" / "slow down" | Drops AI speech rate for rest of session |
| "too slow" / "speed up" | Increases AI speech rate for rest of session |
| "I don't know" / silence > 4s | Triggers Conversation Rescue |

### 6.5 Intelligent Flashcard System

#### How cards are created — three ways, two require no action:

1. **Voice trigger** — user says "save this" or "add to my cards"
2. **Auto-detection** — AI silently saves words when user spells a word aloud, asks for it twice, or pauses more than 4 seconds mid-sentence
3. **Post-correction save** — every word the AI corrects is automatically added to the My Mistakes deck

#### What each flashcard contains

| Field | Content |
|-------|---------|
| Word or phrase | The exact word or expression |
| Example sentence | The sentence from the real conversation it appeared in |
| Emotional context | e.g. "learned during airport scenario" |
| AI audio clip | Native speed and slow speed (on-device TTS) |
| Grammar note | Part of speech, common mistakes, usage tip |
| Review schedule | 1 day → 3 days → 7 days → 30 days → 90 days |

#### Flashcard review — fully voice-controlled

| User says | App does |
|-----------|----------|
| "got it" / "I know this" | Marks correct, advances to next interval |
| "too hard" / "again" | Resets interval, shows again tomorrow |
| "one more time" | Replays the audio clip |
| "use it in a sentence" | AI generates a fresh example sentence |

#### My Mistakes deck
Auto-generated deck of every word or structure the AI has ever corrected. Tagged by error type: grammar, pronunciation, word choice, word order. Reviewing this daily is the fastest way to fix recurring mistakes.

### 6.6 Adaptive Difficulty
After every user response the AI silently evaluates vocabulary used, sentence length, grammar accuracy, and response time. It adjusts the next reply's complexity without the user noticing. Always slightly above the user's current level — never overwhelming, never boring.

Level scale: A1 → A2 → B1 → B2 → C1

### 6.7 Conversation Rescue
When a user goes silent or says "I don't know" the AI escalates through three steps:

1. Rephrases the question in simpler vocabulary
2. Gives a hint: *"You could say something like 'I think that…'"*
3. Offers two multiple-choice options the user can pick and repeat

No learner ever hits a dead end.

### 6.8 Shadowing Mode
- Tap the shadowing button on any AI message
- AI speaks the sentence at normal speed, then again at 0.75x
- User records themselves saying the same sentence
- App gives a pronunciation match score (0–100)
- Weak sounds are highlighted so the user knows exactly what to improve
- 5 minutes of daily shadowing tracked in the streak system

### 6.9 Spaced Repetition Engine
All flashcards feed into a single SM-2 spaced repetition queue:
- New card → review tomorrow
- Correct → double the interval
- Incorrect → reset to 1 day
- Maximum interval: 90 days
- Daily review capped at 20 cards
- Due count shown as a badge on the Home screen

### 6.10 Micro-Lessons (Inline Grammar Tips)
When the AI corrects a mistake it delivers a 60-second inline micro-lesson directly in the chat:
- One clear rule in plain English
- One example of the wrong way
- One example of the right way
- One memory tip

Appears as a card in the chat — not a separate screen. Learning in context sticks 3–4× better than studying grammar out of context.

### 6.11 Progress (Local, No Account Needed)
All progress is stored on the device — no account, no sync, no cloud:
- Daily streak (days in a row of at least one conversation)
- Total vocabulary saved
- Top 5 recurring mistakes
- Shadowing pronunciation score over time
- Level progression A1 → C1
- Conversation history (on-device only, never uploaded)

---

## 7. AI Model

### Confirmed Model

```
Model:        Meta Llama 3.2 3B Instruct
Quantization: Q4_K_M
File size:    ~2.0 GB
Format:       .gguf
Runtime:      MLC LLM (phone GPU — fast)
              llama.cpp fallback (CPU — compatible)
Delivery:     Google Play Asset Delivery (Android)
              Apple On-Demand Resources (iOS)
RAM usage:    ~2.5 GB during inference
Min device:   6 GB RAM
Response:     < 3 seconds on Snapdragon 778G / Apple A15
License:      Meta Llama 3 Community License (free, commercial use allowed)
```

### LLM Integration in KMP
The LLM runtime (MLC LLM / llama.cpp) is a native C++ library. In KMP it is integrated via platform-specific `expect/actual` declarations:
- Android: JNI bridge to llama.cpp `.so` library
- iOS: Swift/Obj-C interop to llama.cpp `.a` static library
- Shared business logic (prompt builder, response parser, difficulty evaluator) lives in `commonMain`

### Why this model
- Fits inside the 2GB app store asset delivery limit — no CDN needed
- Runs comfortably on 6GB RAM leaving headroom for OS and voice engine
- Fast enough for real-time conversation on mid-range phones
- Smart enough to correct grammar, adapt difficulty, and hold natural dialogue
- Free to use commercially — no API fees ever

### AI Persona System Prompt
```
You are an English language tutor named "Sage".
You are warm, encouraging, and patient.
Adapt vocabulary and sentence complexity to user level: {user_level}.
Keep responses conversational — 2–4 sentences unless asked for more.
If the user makes a grammar or vocabulary mistake, gently correct it
at the end of your reply with a one-line explanation.
Always end your turn with a follow-up question to keep conversation going.
Current scenario: {scenario_title} — {scenario_description}
User's known weak points: {mistake_tags}
```

---

## 8. Technical Architecture

```
┌─────────────────────────────────────────────┐
│              SpeakMind App                   │
├─────────────────────────────────────────────┤
│  UI Layer (Compose Multiplatform — KMP)      │
│  ├── Splash screen                           │
│  ├── Home screen                             │
│  └── Chat screen                            │
├─────────────────────────────────────────────┤
│  Voice Layer                                 │
│  ├── STT engine (on-device)                  │
│  ├── TTS engine (on-device)                  │
│  ├── Voice activity detector                 │
│  └── Trigger keyword detector               │
├─────────────────────────────────────────────┤
│  AI Layer                                    │
│  ├── LLM runtime (MLC LLM / llama.cpp)       │
│  ├── Prompt builder                          │
│  ├── Difficulty evaluator (silent, per turn) │
│  └── Micro-lesson generator                 │
├─────────────────────────────────────────────┤
│  Learning Layer                              │
│  ├── Flashcard engine (auto + manual save)   │
│  ├── SM-2 spaced repetition scheduler        │
│  ├── Mistake tracker and tagger              │
│  └── Streak and progress tracker            │
├─────────────────────────────────────────────┤
│  Data Layer (100% on-device)                 │
│  ├── SQLite — flashcards, progress, mistakes │
│  ├── Model file — llama-3.2-3b-q4.gguf       │
│  ├── Audio clips — TTS cached on-device      │
│  └── Scenario JSON — 20 conversations/day   │
└─────────────────────────────────────────────┘
         ← No internet ever required →
    (except one-time model download at install)
```

---

## 9. Infrastructure (Minimal — No Server)

| Service | Purpose | Cost |
|---|---|---|
| Google Play Asset Delivery | Delivers model file on Android install | $0 |
| Apple On-Demand Resources | Delivers model file on iOS install | $0 |
| Google Play Console | Android app distribution | $25 one-time |
| Apple Developer Program | iOS app distribution | $99/year |
| **Total monthly running cost** | | **$0** |

No backend server. No database server. No CDN. No API server. Everything runs on the user's phone. The only recurring cost is the Apple Developer Program annual fee.

---

## 10. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| First launch | Model downloads once, app usable within 5 minutes |
| Performance | AI response ≤ 3s on Snapdragon 778G / Apple A15 |
| Storage | App + model ≤ 2.5 GB total on device |
| RAM | Minimum 6 GB RAM device |
| Battery | < 15% battery drain per 30-min session |
| Privacy | Zero telemetry, zero data transmission, ever |
| Offline | 100% features after first model download |
| Accessibility | Large text mode, screen reader compatible |
| Framework | Kotlin Multiplatform (KMP) + Compose Multiplatform UI |
| Platforms | iOS 16+ / Android 12+ |

---

## 11. Conversation Scenario Structure (JSON)

```json
{
  "id": "conv_007",
  "title": "Job Interview",
  "category": "Work",
  "level": "B1",
  "emotional_stakes": "You really want this job. The interviewer looks serious.",
  "ai_opening": "Hello! I am the hiring manager at TechCorp. Thank you for coming in today. Could you start by telling me a little about yourself?",
  "suggested_vocab": ["experience", "responsible for", "strength", "challenge"],
  "learning_goal": "Practice formal self-introduction and work vocabulary",
  "rescue_hint": "You could start with: I have worked in... for... years",
  "duration_minutes": 5
}
```

---

## 12. Development Phases

### Phase 1 — MVP (3 months)
- [ ] Splash screen with one-time model download
- [ ] Home screen with 20 daily conversation cards
- [ ] Chat screen with text input
- [ ] LLM integration via llama.cpp
- [ ] Basic voice STT/TTS
- [ ] "Save this" voice trigger → flashcard
- [ ] Basic spaced repetition review
- [ ] Local streak counter
- [ ] iOS first

### Phase 2 — Voice Intelligence (2 months)
- [ ] All 10 voice triggers
- [ ] Auto-detection flashcard saving
- [ ] Shadowing mode + pronunciation score
- [ ] Conversation rescue
- [ ] Adaptive difficulty engine
- [ ] My Mistakes deck
- [ ] Android port

### Phase 3 — Polish (1 month)
- [ ] Micro-lesson inline cards
- [ ] Onboarding placement test
- [ ] Progress screen
- [ ] App Store and Play Store submission

### Phase 4 — Growth (ongoing)
- [ ] 100+ scenarios across all levels
- [ ] Accent training module
- [ ] Exam prep mode (IELTS / TOEFL)

---

## 13. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Users abandon on 2GB download | High | High | Show clear progress bar, allow background download, let user explore UI while downloading |
| LLM too slow on some 6GB phones | Medium | High | MLC LLM uses GPU — 3–5× faster than CPU. llama.cpp as fallback |
| STT accuracy varies by accent | Medium | Medium | Manual text input always available as fallback |
| Voice trigger false positives | Medium | Low | Require trigger at sentence start, show dismissible toast so user can undo |
| Device storage full | Low | Medium | Warn user if < 3GB free before download begins |

---

## 14. Success Metrics

| Metric | Target (3 months post-launch) |
|--------|-------------------------------|
| Downloads | 10,000+ |
| D1 retention | ≥ 60% (no login friction helps this) |
| D7 retention | ≥ 40% |
| Avg. session length | ≥ 8 minutes |
| Flashcards saved per user/week | ≥ 25 |
| App Store rating | ≥ 4.6 ⭐ |
| 30-day vocabulary retention | ≥ 80% of saved words |

---

*Document Owner: Product Team | Version: 3.0 | Next Review: April 2026*
