# Daily Words Notification Feature

## Context
Users need a passive vocabulary learning mechanism. A daily notification at a user-chosen time delivers a random word (based on CEFR level or one level above). Tapping the notification opens the word for review + TTS listening. All sent words are saved locally, never repeated, and listed in a new "Daily Words" section on the home screen. The Free Talk button is kept as a smaller non-floating button.

---

## Phase 1: Data Foundation

### 1.1 Create vocabulary word bank
- **New file:** `composeApp/src/commonMain/composeResources/files/vocabulary.json`
- Structure matches existing `VocabularyData` / `VocabLevel` / `VocabWord` models at [VocabModels.kt](../composeApp/src/commonMain/kotlin/com/speakmind/app/feature/vocabulary/domain/model/VocabModels.kt)
- ~50-100 words per CEFR level (A1-C1), each with: word, partOfSpeech, frequencyRank, meaning, sentences (2-3 examples)

### 1.2 Add database tables
- **Modify:** [SpeakMind.sq](../composeApp/src/commonMain/sqldelight/com/speakmind/app/db/SpeakMind.sq)

**`daily_words` table** - stores every word sent via notification:
```sql
CREATE TABLE daily_words (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    word TEXT NOT NULL,
    level TEXT NOT NULL,
    part_of_speech TEXT NOT NULL DEFAULT '',
    meaning TEXT NOT NULL DEFAULT '',
    sentences_json TEXT NOT NULL DEFAULT '[]',
    sent_date TEXT NOT NULL,
    is_read INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);
```
Queries: selectAllDailyWords, selectTodayWord, selectRecentDailyWords (LIMIT 30), insertDailyWord, selectAllSentWords (just word column), markDailyWordRead, countDailyWords

**`user_settings` table** - notification time preference:
```sql
CREATE TABLE user_settings (
    id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
    notification_hour INTEGER NOT NULL DEFAULT 9,
    notification_minute INTEGER NOT NULL DEFAULT 0,
    notifications_enabled INTEGER NOT NULL DEFAULT 1
);
```
Queries: insertDefaultSettings (INSERT OR IGNORE), selectSettings, updateNotificationTime, updateNotificationsEnabled

### 1.3 Add migrations
- **Modify:** [AppModule.android.kt](../composeApp/src/androidMain/kotlin/com/speakmind/app/di/AppModule.android.kt) - add `CREATE TABLE IF NOT EXISTS` for both tables
- **Modify:** [AppModule.ios.kt](../composeApp/src/iosMain/kotlin/com/speakmind/app/di/AppModule.ios.kt) - same migrations

---

## Phase 2: Domain + Repository (dailyword feature)

**New directory:** `composeApp/src/commonMain/kotlin/com/speakmind/app/feature/dailyword/`

### 2.1 Model
- **New:** `domain/model/DailyWordData.kt` - data class with id, word, level, partOfSpeech, meaning, sentences, sentDate, isRead

### 2.2 Repository
- **New:** `data/DailyWordRepository.kt` - wraps DB queries (getAllDailyWords, getRecentWords, markAsRead, getTodayWord)

### 2.3 Picker
- **New:** `domain/DailyWordPicker.kt`
  - Gets all sent words from DB (to exclude)
  - Loads vocabulary for user's level + one level above
  - Returns a random unsent word
  - Fallback: any level with unsent words if current levels exhausted

### 2.4 Service
- **New:** `domain/DailyWordService.kt`
  - `getOrCreateTodayWord()`: checks if today already has a word, if not picks + saves one
  - `prepareNextDayWord()`: pre-selects tomorrow's word and saves with tomorrow's date (for iOS notification content + Android notification content)

### 2.5 DI
- **New:** `di/DailyWordModule.kt` - provides DailyWordPicker, DailyWordService, DailyWordRepository, WordDetailViewModel
- **Modify:** [KoinInit.kt](../composeApp/src/commonMain/kotlin/com/speakmind/app/di/KoinInit.kt) - add `dailyWordModule`

---

## Phase 3: Word Detail Screen

### 3.1 Navigation
- **Modify:** [Destinations.kt](../composeApp/src/commonMain/kotlin/com/speakmind/app/navigation/Destinations.kt) - add `WordDetailDestination(wordId: Long = -1, word: String = "")`
- **Modify:** [AppRoot.kt](../composeApp/src/commonMain/kotlin/com/speakmind/app/AppRoot.kt) - add `wordDetailScreen()` to NavHost

### 3.2 Screen
- **New:** `feature/dailyword/ui/WordDetailScreen.kt`
  - Top bar with back button
  - Word (large, cyan) + level badge
  - Part of speech label
  - Meaning text
  - "Listen" button (TTS for the word itself)
  - Divider
  - Example sentences list, each with a TTS play button (reuse pattern from existing `VocabWordListScreen`)
  - Mark word as read on open

### 3.3 ViewModel
- **New:** `feature/dailyword/ui/WordDetailViewModel.kt`
  - Dependencies: NavigationManager, DailyWordRepository, TextToSpeechEngine
  - Loads word by ID or by word string (for notification deeplink)
  - Handles TTS playback for word and sentences

---

## Phase 4: Home Screen Integration

### 4.1 UI State changes
- **Modify:** [HomeViewModel.kt](../composeApp/src/commonMain/kotlin/com/speakmind/app/feature/home/ui/HomeViewModel.kt)
  - Add to `HomeUiState`: `todayWord: DailyWordData?`, `recentDailyWords: List<DailyWordData>`, `notificationHour: Int`, `notificationMinute: Int`, `notificationsEnabled: Boolean`, `showTimePicker: Boolean`
  - In `loadHomeData()`: call `dailyWordService.getOrCreateTodayWord()` + `dailyWordRepository.getRecentWords()`
  - Load notification settings from `user_settings` table
  - Add actions: `onDailyWordClicked(id)`, `onNotificationTimeChanged(hour, minute)`, `onNotificationToggled(enabled)`, `onTimePickerDismissed()`

### 4.2 Home screen UI
- **Modify:** [HomeScreen.kt](../composeApp/src/commonMain/kotlin/com/speakmind/app/feature/home/ui/HomeScreen.kt)
  - **Replace FreeTalkButton FAB** with a smaller non-floating "Free Talk" button (compact row, similar style to FlashcardReviewButton)
  - **Add `DailyWordsSection` composable** after the flashcard button:
    - Section header: "Daily Words" + bell icon button (opens time picker dialog)
    - Today's word card (highlighted/larger): word, part of speech, meaning preview, level badge - taps to WordDetailDestination
    - If no word today: prompt to enable notifications
    - "Previous Words" sub-header
    - Recent words list (last 7-10), compact rows: word, level badge, date, read/unread dot
    - Each taps to WordDetailDestination
  - **Add TimePickerDialog** composable (AlertDialog with hour/minute selection, similar to existing LevelPickerDialog pattern)

### 4.3 DI update
- **Modify:** [HomeModule.kt](../composeApp/src/commonMain/kotlin/com/speakmind/app/feature/home/di/HomeModule.kt) - add DailyWordService and DailyWordRepository to HomeViewModel dependencies

---

## Phase 5: Notification Scheduling (Platform-Specific)

### 5.1 Common expect class
- **New:** `feature/dailyword/platform/DailyWordNotificationScheduler.kt`
```kotlin
expect class DailyWordNotificationScheduler {
    fun schedule(hour: Int, minute: Int, word: String, meaning: String)
    fun cancel()
}
```

### 5.2 Android implementation
- **New:** `composeApp/src/androidMain/.../feature/dailyword/platform/DailyWordNotificationScheduler.android.kt`
  - Uses `AlarmManager.setExactAndAllowWhileIdle()` for exact timing
  - Creates PendingIntent to DailyWordAlarmReceiver

- **New:** `composeApp/src/androidMain/.../feature/dailyword/platform/DailyWordAlarmReceiver.kt`
  - BroadcastReceiver triggered by AlarmManager
  - Gets word info from intent extras (pre-selected word)
  - Shows notification on channel `"daily_word_channel"` (IMPORTANCE_DEFAULT)
  - Notification click PendingIntent: opens MainActivity with `EXTRA_DAILY_WORD` extras
  - Calls `DailyWordService.prepareNextDayWord()` and reschedules alarm for tomorrow with the new word

- **New:** `composeApp/src/androidMain/.../feature/dailyword/platform/BootReceiver.kt`
  - BroadcastReceiver for BOOT_COMPLETED
  - Re-registers alarm after device reboot using saved settings

- **Modify:** [AndroidManifest.xml](../androidApp/src/main/AndroidManifest.xml)
  - Add `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` permissions
  - Register DailyWordAlarmReceiver and BootReceiver

- **Modify:** [MainActivity.kt](../androidApp/src/main/java/com/speakmind/app/MainActivity.kt)
  - In `onCreate` and `onNewIntent`: check for `EXTRA_DAILY_WORD` intent extra
  - Navigate to `WordDetailDestination` with the word info

- **Modify:** [AppModule.android.kt](../composeApp/src/androidMain/kotlin/com/speakmind/app/di/AppModule.android.kt)
  - Provide `DailyWordNotificationScheduler(androidContext())`

### 5.3 iOS implementation
- **New:** `composeApp/src/iosMain/.../feature/dailyword/platform/DailyWordNotificationScheduler.ios.kt`
  - Uses `UNUserNotificationCenter`
  - Requests notification permission
  - Creates `UNMutableNotificationContent` with the pre-selected word as title and meaning as body
  - Creates `UNCalendarNotificationTrigger` with chosen hour/minute, repeats=false (re-scheduled daily when app opens)
  - Notification `userInfo` dictionary carries word identifier for deeplink
  - Cancel via `removePendingNotificationRequests(withIdentifiers:)`

- **Modify:** [AppModule.ios.kt](../composeApp/src/iosMain/kotlin/com/speakmind/app/di/AppModule.ios.kt)
  - Provide `DailyWordNotificationScheduler()`

### 5.4 Scheduling flow
1. User picks time in TimePickerDialog -> saves to `user_settings` DB
2. `DailyWordService.prepareNextDayWord()` picks tomorrow's word, saves to `daily_words` with tomorrow's date
3. `DailyWordNotificationScheduler.schedule(hour, minute, word, meaning)` sets the alarm/notification with the actual word content
4. When notification fires (Android): receiver shows notification with the word. On click -> opens WordDetailScreen
5. When notification tapped (iOS): app opens with word identifier in userInfo -> navigates to WordDetailScreen
6. On app open: `prepareNextDayWord()` is called again to keep the cycle going

---

## Phase 6: Polish & Edge Cases

- Handle "no words left" (all words in level + level above have been sent) -> fall back to any level, or show "You've learned all words!" message
- Handle notification permission denied -> show guidance in DailyWordsSection
- Boot receiver re-registers alarm with latest pre-selected word from DB
- Insert default user_settings row on first app launch (via migration or on first access)
- Ensure `prepareNextDayWord()` is called in `HomeViewModel.loadHomeData()` so the next notification always has fresh content

---

## Verification

1. **Database**: Run app, verify `daily_words` and `user_settings` tables created without crash
2. **Word picking**: Set level to A1, verify words come from A1/A2 pool. Change level, verify pool changes
3. **Home screen**: Verify "Daily Words" section shows today's word + recent history. Verify Free Talk button still works as compact button
4. **Word detail**: Tap a word, verify detail screen shows word/meaning/sentences. Tap TTS buttons, verify audio plays
5. **Notifications (Android)**: Set time to 1 min from now, verify notification appears with word content. Tap notification, verify WordDetailScreen opens with correct word
6. **Notifications (iOS)**: Same flow via UNUserNotificationCenter
7. **No repeats**: Check that sent words are excluded from future picks
8. **Reboot (Android)**: Reboot device, verify alarm re-registered and notification fires at scheduled time

---

## File Summary

### New files (~12)
| File | Purpose |
|------|---------|
| `composeResources/files/vocabulary.json` | Word bank (A1-C1) |
| `feature/dailyword/domain/model/DailyWordData.kt` | Data model |
| `feature/dailyword/data/DailyWordRepository.kt` | DB access layer |
| `feature/dailyword/domain/DailyWordPicker.kt` | Word selection logic |
| `feature/dailyword/domain/DailyWordService.kt` | Orchestration service |
| `feature/dailyword/di/DailyWordModule.kt` | Koin DI module |
| `feature/dailyword/ui/WordDetailScreen.kt` | Word review screen |
| `feature/dailyword/ui/WordDetailViewModel.kt` | Word detail VM |
| `feature/dailyword/platform/DailyWordNotificationScheduler.kt` | expect class |
| `androidMain/.../DailyWordNotificationScheduler.android.kt` | Android actual |
| `androidMain/.../DailyWordAlarmReceiver.kt` | Android alarm receiver |
| `androidMain/.../BootReceiver.kt` | Android boot receiver |
| `iosMain/.../DailyWordNotificationScheduler.ios.kt` | iOS actual |

### Modified files (~11)
| File | Change |
|------|--------|
| `SpeakMind.sq` | Add daily_words + user_settings tables & queries |
| `AppModule.android.kt` | Migrations + scheduler DI |
| `AppModule.ios.kt` | Migrations + scheduler DI |
| `KoinInit.kt` | Register dailyWordModule |
| `Destinations.kt` | Add WordDetailDestination |
| `AppRoot.kt` | Add wordDetailScreen() route |
| `HomeScreen.kt` | Replace FAB, add DailyWordsSection + TimePickerDialog |
| `HomeViewModel.kt` | Add daily word state/actions |
| `HomeModule.kt` | Update VM dependencies |
| `AndroidManifest.xml` | Permissions + receivers |
| `MainActivity.kt` | Notification deeplink handling |
