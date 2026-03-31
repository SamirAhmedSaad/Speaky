# SpeakMind Implementation Plan
## Offline AI English Learning App

### Context
SpeakMind is a fully offline, AI-powered English learning app built with KMP + Compose Multiplatform. Users practice English through conversations with an on-device AI tutor (Llama 3.2 via llama.cpp). No login, no subscription, no internet after model download.

### Figma Design References
- **Splash (Logo + Download):** https://www.figma.com/design/7Zl46cqw5o0xEGt1CrMe1a/Wassal-App--Copy-?node-id=3022-9795&t=7OogIGFjijdbezaO-4
- **Home Screen:** https://www.figma.com/design/7Zl46cqw5o0xEGt1CrMe1a/Wassal-App--Copy-?node-id=3022-9832&t=7OogIGFjijdbezaO-4
- **Chat Screen:** https://www.figma.com/design/7Zl46cqw5o0xEGt1CrMe1a/Wassal-App--Copy-?node-id=3022-10004&t=7OogIGFjijdbezaO-4

---

## Phase 1: Foundation — COMPLETED
- [x] Deleted all 21 HR feature modules, network layer, Firebase, Ktor, Coil, KSafe
- [x] Rebranded to `com.speakmind.app`, applicationId `com.speakmind.app`
- [x] New build configs: SQLDelight, WorkManager, llama.cpp native module
- [x] Fantasy dark theme (neon cyan/magenta/gold on deep purple gradients)
- [x] SQLDelight DB schema: flashcards, mistakes, progress, conversations
- [x] 5 navigation destinations: Splash, Home, Chat, FlashcardReview, ModelDownload
- [x] Updated entry points: MainActivity, SpeakMindApplication, iOSApp.swift

## Phase 2: AI Core — COMPLETED
- [x] llama.cpp cloned and built natively via CMake in `:llama` module (arm64-v8a)
- [x] JNI bridge (`llama-android.cpp`) from official llama.cpp Android example
- [x] `LLamaAndroid.kt` Kotlin JNI wrapper (load, send, unload)
- [x] `LlmEngine` expect/actual: Android uses real JNI, iOS stub for now
- [x] `PromptBuilder` — builds tutor persona prompt with conversation history, scenario, user level
- [x] `DifficultyEvaluator` — heuristic CEFR level assessment from user messages
- [x] `ResponseParser` — extracts corrections from LLM output
- [x] `SmartResponseEngine` — fallback keyword-based responses (used when no model loaded)
- [x] `findModelFile()` expect/actual — searches app internal storage + Downloads folder
- [x] Model: Llama 3.2 1B Instruct Q4_K_M (~0.68GB) for testing, 3B for production

## Phase 3: Splash Screen — COMPLETED
- [x] Animated logo with glow pulse and particle background
- [x] Brief splash then navigate to Home
- [x] Model download moved to separate ModelDownload screen (not splash)

## Phase 4: Home Screen — COMPLETED
- [x] 20 daily conversation scenario cards (horizontal scroll, equal height, glassmorphism)
- [x] Free Talk glowing FAB button
- [x] Streak badge with fire emoji
- [x] Stats row: Words learned, Chats, Minutes
- [x] Flashcard review button with due count badge
- [x] Level badge (tappable) → Level picker dialog (A1-C1 with descriptions)
- [x] Level change reloads scenarios sorted by new level
- [x] Tapping scenario/free talk checks if model exists → redirects to download if not

## Phase 5: Chat Screen — COMPLETED
- [x] Gradient chat bubbles (cyan user, purple AI)
- [x] Token-by-token streaming from LLM
- [x] Typing indicator (3 animated dots)
- [x] `imePadding()` — keyboard doesn't push layout up
- [x] Text input + send button + mic button (stub)
- [x] Back/End buttons, scenario title in header
- [x] Model status message when no model loaded
- [x] Auto-save corrections as flashcards
- [x] Manual flashcard save with toast notification

## Phase 6: Model Download — COMPLETED
- [x] ModelDownload screen: prompt → progress → complete/error states
- [x] WorkManager-based download with WiFi-only constraint
- [x] Resume support (HTTP Range header + temp file)
- [x] Foreground notification with progress bar (survives app close)
- [x] `foregroundServiceType="dataSync"` in manifest
- [x] Auto-detects if download already in progress → skips prompt, shows progress
- [x] On completion → auto-navigates to Chat
- [x] Model stored in app-internal filesDir (not deletable by user)
- [x] Download URL: HuggingFace (Llama 3.2 1B for testing, swap to 3B for production)

## Phase 7: Flashcard System — COMPLETED
- [x] SM-2 spaced repetition engine (ease factor, intervals, quality ratings)
- [x] Flashcard review screen with 3D card flip animation
- [x] Rating buttons: Again, Hard, Good, Easy
- [x] Completion screen when no cards due
- [x] Cards auto-created from AI corrections

## Phase 8: Advanced Features — COMPLETED (code written, not yet wired to UI)
- [x] `AdaptiveDifficultyManager` — tracks error rate, suggests level up/down
- [x] `ConversationRescuer` — detects stuck users, 3 rescue levels
- [x] `PronunciationScorer` — Levenshtein-based scoring 0-100
- [x] `ShadowingMode` composable — listen, record, compare, score display
- [x] `StreakTracker` — daily streak with consecutive day logic
- [x] `MistakeTracker` — aggregates corrections by type
- [x] `VoiceTriggerDetector` — 10 voice triggers (save, repeat, meaning, etc.)

## Phase 9: Voice System — COMPLETED (code written, not yet wired to UI)
- [x] `SpeechRecognizerEngine` expect/actual (Android: SpeechRecognizer, iOS: SFSpeechRecognizer)
- [x] `TextToSpeechEngine` expect/actual (Android: TextToSpeech, iOS: AVSpeechSynthesizer)
- [x] Platform implementations with StateFlow for results and speaking state

## Phase 10: UI Polish — COMPLETED
- [x] `GlassmorphismCard` and `GlowingCard` composables
- [x] Particle background animation (Canvas + InfiniteTransition)
- [x] Chat bubble entrance animations (slideIn + fadeIn)
- [x] Screen transitions with fade overlays
- [x] Modifier extensions (noRippleClick, glowBorder, gradientBackground)

---

## Remaining Work (Phase 2 — Future)

### Voice Integration (wire to UI)
- [ ] Connect SpeechRecognizerEngine to ChatViewModel mic button
- [ ] Auto-speak AI responses via TextToSpeechEngine
- [ ] Process voice triggers in chat flow
- [ ] Waveform animation during recording

### Advanced Features Integration (wire to UI)
- [ ] Wire AdaptiveDifficultyManager into ChatViewModel end-of-conversation
- [ ] Wire ConversationRescuer into prompt building
- [ ] Add shadowing button on AI messages
- [ ] Show pronunciation score overlay

### Production Readiness
- [ ] Switch model URL from 1B (testing) to 3B (production)
- [ ] RAM-based auto-detection: 6GB+ → 3B, <6GB → smaller model
- [ ] iOS llama.cpp integration via cinterop
- [ ] Unit tests: SM2Engine, PromptBuilder, ResponseParser, VoiceTriggerDetector
- [ ] Onboarding placement test
- [ ] Progress/stats screen
- [ ] App Store / Play Store submission assets

---

## Architecture

```
┌─────────────────────────────────────────────┐
│              SpeakMind App                   │
├─────────────────────────────────────────────┤
│  UI Layer (Compose Multiplatform — KMP)      │
│  ├── Splash screen                           │
│  ├── Home screen (20 daily topics)           │
│  ├── Chat screen (streaming AI)              │
│  ├── Flashcard review (SM-2)                 │
│  └── Model download screen                  │
├─────────────────────────────────────────────┤
│  AI Layer                                    │
│  ├── llama.cpp native (JNI on Android)       │
│  ├── LLamaAndroid JNI wrapper                │
│  ├── Prompt builder (tutor persona)          │
│  ├── Response parser (corrections)           │
│  └── Difficulty evaluator (CEFR)            │
├─────────────────────────────────────────────┤
│  Voice Layer (code ready, not yet wired)     │
│  ├── STT engine (on-device)                  │
│  ├── TTS engine (on-device)                  │
│  └── Voice trigger detector                 │
├─────────────────────────────────────────────┤
│  Learning Layer                              │
│  ├── Flashcard engine (auto + manual save)   │
│  ├── SM-2 spaced repetition scheduler        │
│  ├── Adaptive difficulty manager             │
│  ├── Conversation rescuer                    │
│  ├── Pronunciation scorer                    │
│  └── Streak & mistake tracker               │
├─────────────────────────────────────────────┤
│  Data Layer (100% on-device)                 │
│  ├── SQLite via SQLDelight                   │
│  ├── Model file (.gguf in app filesDir)      │
│  ├── Scenario JSON (20 bundled)              │
│  └── WorkManager (model download)           │
└─────────────────────────────────────────────┘
```

## File Structure

```
composeApp/src/
  commonMain/kotlin/com/speakmind/app/
    AppRoot.kt
    di/ (AppModule, KoinInit, DatabaseModule)
    navigation/ (Destinations, NavigationManager, NavigationModule)
    ui/theme/Theme.kt
    ui/components/ (AnimatedComposable, GlassmorphismCard, Modifier)
    feature/
      splash/  (ui/SplashScreen, SplashViewModel, di/)
      home/    (data/ScenarioRepository, domain/model/, ui/HomeScreen, HomeViewModel, di/)
      chat/    (domain/model/, ui/ChatScreen, ChatViewModel, ui/shadowing/, di/)
      download/ (ui/ModelDownloadScreen, ModelDownloadViewModel, di/)
      flashcard/ (domain/SM2Engine, ui/FlashcardReviewScreen, FlashcardReviewViewModel, di/)
      ai/      (platform/LlmEngine, ModelFileManager, ModelDownloader, FindModel,
                domain/PromptBuilder, DifficultyEvaluator, ResponseParser, SmartResponseEngine, di/)
      voice/   (platform/SpeechRecognizer, TextToSpeechEngine, domain/VoiceTriggerDetector, di/)
      learning/ (domain/AdaptiveDifficultyManager, ConversationRescuer, PronunciationScorer,
                 StreakTracker, data/MistakeTracker, di/)
  commonMain/sqldelight/ (SpeakMind.sq)
  commonMain/composeResources/ (scenarios.json, fonts, strings)
  androidMain/ (AppModule, DebugConfig, PlatformName, LlmEngine, ModelFileManager,
               AndroidModelDownloader, ModelDownloadWorker, FindModel,
               SpeechRecognizerEngine, TextToSpeechEngine)
  iosMain/ (MainViewController, AppModule, DebugConfig, PlatformName,
           LlmEngine, ModelFileManager, FindModel,
           SpeechRecognizerEngine, TextToSpeechEngine)
llama/ (Android library module — CMake builds llama.cpp natively)
libs/llama.cpp/ (cloned source)
androidApp/ (MainActivity, SpeakMindApplication, AndroidManifest)
iosApp/ (iOSApp.swift, ContentView.swift)
```

## Tech Stack
- **Language:** Kotlin 2.3.0
- **UI:** Compose Multiplatform 1.10.0
- **Architecture:** Clean Architecture + MVVM, Koin DI
- **Database:** SQLDelight 2.0.2
- **LLM:** llama.cpp (native C++, JNI bridge)
- **Model:** Llama 3.2 3B Instruct Q4_K_M (Meta, free commercial license)
- **Download:** WorkManager (WiFi-only, foreground notification, resume)
- **Navigation:** Type-safe Compose Navigation
- **Build:** Gradle KTS, AGP 9.0.0-beta02
- **Platforms:** Android 7.0+ (SDK 24), iOS 16+
