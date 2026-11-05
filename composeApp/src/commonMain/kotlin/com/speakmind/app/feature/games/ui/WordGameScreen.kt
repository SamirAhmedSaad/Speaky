package com.speakmind.app.feature.games.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.games.data.GameKind
import com.speakmind.app.feature.games.data.GameRepository
import com.speakmind.app.feature.games.data.OddItem
import com.speakmind.app.feature.games.data.PhrasalItem
import com.speakmind.app.feature.games.data.SayItem
import com.speakmind.app.feature.games.data.SituationItem
import com.speakmind.app.feature.games.data.SpellItem
import com.speakmind.app.feature.voice.platform.SpeechRecognizerEngine
import com.speakmind.app.feature.voice.platform.SpeechResult
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.TintStatusBar
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One level of Listen & Spell, Say It!, What Would You Say?, Phrasal Pop or Odd One Out. */
@Serializable
data class WordGameDestination(val game: String, val tier: String, val level: Int)

sealed interface Question {
    /** [tiles]: the word's letters plus a few extra, shuffled; [typed]: indexes of tapped tiles. */
    data class Spell(val item: SpellItem, val tiles: List<Char>, val typed: List<Int> = emptyList(), val correct: Boolean? = null, val heard: Boolean = false) : Question

    /** [matched]: per word of the phrase, whether it was heard; null before the first try. */
    data class Say(
        val item: SayItem,
        val words: List<String>,
        val matched: List<Boolean>? = null,
        val heard: String = "",
        val tries: Int = 0,
        val listening: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
        val passed: Boolean = false,
    ) : Question {
        val score get() = matched?.let { m -> if (m.isEmpty()) 0 else m.count { it } * 100 / m.size } ?: 0
    }

    data class Situation(val item: SituationItem, val order: List<Int>, val picked: Int? = null) : Question
    data class Phrasal(val item: PhrasalItem, val options: List<String>, val picked: String? = null) : Question
    data class Odd(val item: OddItem, val cards: List<String>, val picked: String? = null) : Question
}

data class WordGameUiState(
    val kind: GameKind,
    val tier: String,
    val level: Int,
    val loading: Boolean = true,
    val levelCount: Int = 0,
    val index: Int = 0,
    val total: Int = 0,
    val right: Int = 0,
    val question: Question? = null,
    val finished: Boolean = false,
    val passed: Boolean = false,
    val shakeKey: Int = 0,
    val popKey: Int = 0,
) {
    val answered: Boolean
        get() = when (val q = question) {
            is Question.Spell -> q.correct != null
            is Question.Say -> q.done
            is Question.Situation -> q.picked != null
            is Question.Phrasal -> q.picked != null
            is Question.Odd -> q.picked != null
            null -> false
        }
}

class WordGameViewModel(
    private val destination: WordGameDestination,
    private val repository: GameRepository,
    private val navigationManager: NavigationManager,
    private val tts: TextToSpeechEngine,
    private val speech: SpeechRecognizerEngine,
) : ViewModel() {
    private val kind = GameKind.of(destination.game)
    private val _uiState = MutableStateFlow(WordGameUiState(kind, destination.tier, destination.level))
    val uiState: StateFlow<WordGameUiState> = _uiState.asStateFlow()
    private var questions: List<Question> = emptyList()
    private var speechJob: Job? = null

    init {
        viewModelScope.launch { start() }
        if (kind == GameKind.SAY) observeSpeech()
    }

    private suspend fun start() {
        val tier = destination.tier
        val level = destination.level
        questions = when (kind) {
            GameKind.SPELL -> repository.levelItems(kind, repository.spell(tier), level).map { Question.Spell(it, tilesFor(it.word)) }
            GameKind.SAY -> repository.levelItems(kind, repository.say(tier), level).map { Question.Say(it, words(it.phrase)) }
            GameKind.SITUATION -> repository.levelItems(kind, repository.situations(tier), level).map { Question.Situation(it, it.options.indices.shuffled()) }
            GameKind.PHRASAL -> repository.levelItems(kind, repository.phrasal(tier), level).map { Question.Phrasal(it, (it.distractors + it.answer).shuffled()) }
            GameKind.ODD -> repository.levelItems(kind, repository.odd(tier), level).map { Question.Odd(it, (it.group + it.odd).shuffled()) }
            else -> emptyList()
        }.shuffled()
        _uiState.value = WordGameUiState(
            kind, tier, level, loading = false, levelCount = repository.levelCount(kind, tier),
            total = questions.size, question = questions.firstOrNull(),
        )
        onQuestionShown()
    }

    private fun onQuestionShown() {
        val q = _uiState.value.question
        if (q is Question.Spell) speakSpell(slow = false)
    }

    private fun tilesFor(word: String): List<Char> {
        val extra = ('a'..'z').filter { it !in word }.shuffled().take(if (word.length > 9) 2 else 3)
        return (word.toList() + extra).shuffled()
    }

    private fun update(question: Question) = _uiState.update { it.copy(question = question) }

    private fun scored(correct: Boolean) = _uiState.update {
        it.copy(right = it.right + if (correct) 1 else 0, shakeKey = it.shakeKey + if (correct) 0 else 1, popKey = it.popKey + if (correct) 1 else 0)
    }

    // ---------- Listen & Spell ----------

    fun onSpellListen(slow: Boolean) = speakSpell(slow)

    private fun speakSpell(slow: Boolean) {
        val q = _uiState.value.question as? Question.Spell ?: return
        update(q.copy(heard = true))
        viewModelScope.launch { tts.speak(q.item.word, rate = if (slow) 0.55f else 0.85f) }
    }

    fun onSpellTile(tile: Int) {
        val q = _uiState.value.question as? Question.Spell ?: return
        if (q.correct != null || tile in q.typed || q.typed.size >= q.item.word.length) return
        val typed = q.typed + tile
        if (typed.size < q.item.word.length) return update(q.copy(typed = typed))
        val ok = typed.map { q.tiles[it] }.joinToString("") == q.item.word
        update(q.copy(typed = typed, correct = ok))
        scored(ok)
        if (!ok) viewModelScope.launch { repository.saveToFlashcards(q.item.word, q.item.sentence, "Spelling", q.item.meaning) }
    }

    fun onSpellDelete() {
        val q = _uiState.value.question as? Question.Spell ?: return
        if (q.correct != null || q.typed.isEmpty()) return
        update(q.copy(typed = q.typed.dropLast(1)))
    }

    // ---------- Say It! ----------

    private fun observeSpeech() {
        speechJob = viewModelScope.launch {
            speech.results.collect { result ->
                val q = _uiState.value.question as? Question.Say ?: return@collect
                if (!q.listening) return@collect
                when (result) {
                    is SpeechResult.Partial -> update(q.copy(heard = result.text))
                    is SpeechResult.Final -> judgeSay(q, result.text)
                    is SpeechResult.Error -> update(
                        q.copy(
                            listening = false,
                            error = when {
                                result.message.contains("permission", true) -> "Allow the microphone to play Say It!"
                                result.message.contains("network", true) -> "Speech needs internet. Try another Wi-Fi or mobile data."
                                else -> "I couldn't hear you. Tap the mic and try again."
                            },
                        ),
                    )
                    SpeechResult.Idle -> Unit
                }
            }
        }
    }

    private fun judgeSay(q: Question.Say, text: String) {
        val matched = match(q.words, text)
        val tries = q.tries + 1
        val score = matched.count { it } * 100 / matched.size.coerceAtLeast(1)
        val ok = score >= SAY_PASS
        val done = ok || tries >= SAY_TRIES
        update(q.copy(matched = matched, heard = text, tries = tries, listening = false, error = null, done = done, passed = ok))
        if (done) scored(ok)
    }

    fun onSayMic() {
        val q = _uiState.value.question as? Question.Say ?: return
        if (q.done) return
        if (q.listening) {
            speech.stopListening()
            update(q.copy(listening = false))
            return
        }
        tts.stop()
        update(q.copy(listening = true, heard = "", error = null))
        speech.startListening("en-US")
    }

    fun onSayListen(slow: Boolean) {
        val q = _uiState.value.question as? Question.Say ?: return
        viewModelScope.launch { tts.speak(q.item.phrase, rate = if (slow) 0.6f else 0.9f) }
    }

    /** Gives up on a phrase (e.g. no microphone): counts as missed. */
    fun onSaySkip() {
        val q = _uiState.value.question as? Question.Say ?: return
        if (q.done) return
        if (q.listening) speech.stopListening()
        update(q.copy(listening = false, done = true, passed = false, tries = SAY_TRIES))
        scored(false)
    }

    // ---------- Choice games ----------

    fun onPick(option: Int) {
        when (val q = _uiState.value.question) {
            is Question.Situation -> if (q.picked == null) {
                update(q.copy(picked = option))
                scored(q.order[option] == 0)
            }
            is Question.Phrasal -> if (q.picked == null) {
                val pick = q.options[option]
                update(q.copy(picked = pick))
                val ok = pick == q.item.answer
                scored(ok)
                if (!ok) viewModelScope.launch { repository.saveToFlashcards(q.item.verb, q.item.sentence.replace("___", q.item.answer), "Phrasal verb", q.item.meaning) }
            }
            is Question.Odd -> if (q.picked == null) {
                val pick = q.cards[option]
                update(q.copy(picked = pick))
                val ok = pick == q.item.odd
                scored(ok)
                if (!ok) viewModelScope.launch { repository.saveToFlashcards(q.item.odd, q.item.why, "Odd One Out", q.item.why) }
            }
            else -> Unit
        }
    }

    // ---------- Flow ----------

    fun onNext() {
        val state = _uiState.value
        if (!state.answered) return
        tts.stop()
        val next = state.index + 1
        if (next >= questions.size) return finish()
        _uiState.update { it.copy(index = next, question = questions[next]) }
        onQuestionShown()
    }

    private fun finish() {
        val state = _uiState.value
        val passed = state.right >= kind.pass.coerceAtMost(state.total)
        _uiState.update { it.copy(finished = true, passed = passed) }
        if (passed) viewModelScope.launch { repository.markCompleted(kind, destination.tier, destination.level) }
    }

    fun onRetry() {
        viewModelScope.launch {
            openGameLevel(repository, navigationManager, kind, destination.tier, destination.level, replace = true)
        }
    }

    fun onNextLevel() {
        val next = destination.level + 1
        if (next >= _uiState.value.levelCount) return onBack()
        viewModelScope.launch { openGameLevel(repository, navigationManager, kind, destination.tier, next, replace = true) }
    }

    fun onBack() {
        tts.stop()
        navigationManager.back()
    }

    override fun onCleared() {
        tts.stop()
        if ((_uiState.value.question as? Question.Say)?.listening == true) speech.stopListening()
        speechJob?.cancel()
    }

    companion object {
        /** Percent of a phrase's words that must be heard. */
        const val SAY_PASS = 75
        const val SAY_TRIES = 3

        private val CONTRACTIONS = mapOf(
            "i'm" to "i am", "you're" to "you are", "we're" to "we are", "they're" to "they are", "it's" to "it is",
            "don't" to "do not", "doesn't" to "does not", "didn't" to "did not", "can't" to "cannot", "won't" to "will not",
            "i'd" to "i would", "i'll" to "i will", "let's" to "let us", "isn't" to "is not", "aren't" to "are not",
            "wasn't" to "was not", "i've" to "i have", "that's" to "that is", "what's" to "what is", "there's" to "there is",
        )

        /** Per word of the phrase: heard, in order (each after the previous match). */
        fun match(target: List<String>, spoken: String): List<Boolean> {
            val heard = words(spoken)
            var from = 0
            return target.map { w ->
                val at = heard.subList(from.coerceAtMost(heard.size), heard.size).indexOf(w)
                if (at >= 0) { from += at + 1; true } else false
            }
        }

        fun words(text: String): List<String> =
            text.lowercase().replace('’', '\'').split(Regex("\\s+")).flatMap { raw ->
                val w = raw.trim { !it.isLetter() && it != '\'' }
                (CONTRACTIONS[w] ?: w).split(' ')
            }.map { w -> w.filter { it.isLetter() } }.map { if (it == "cannot") "can not" else it }
                .flatMap { it.split(' ') }.filter { it.isNotEmpty() }
    }
}

fun NavGraphBuilder.wordGameScreen() {
    animatedComposable<WordGameDestination> { backStackEntry ->
        val destination = backStackEntry.toRoute<WordGameDestination>()
        val viewModel = koinViewModel<WordGameViewModel>(key = "word-${destination.game}-${destination.tier}-${destination.level}") { parametersOf(destination) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val colors = LocalSpeakMindColors.current
        TintStatusBar(Color(state.kind.colorB))
        Box(Modifier.fillMaxSize().background(colors.backgroundDark)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(state.kind.colorB))
                state.finished -> LevelResult(state, viewModel)
                else -> WordGamePlay(state, viewModel)
            }
        }
    }
}

@Composable
private fun WordGamePlay(state: WordGameUiState, viewModel: WordGameViewModel) {
    val colors = LocalSpeakMindColors.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.shakeKey) { if (state.shakeKey > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val kind = state.kind
    Column(Modifier.fillMaxSize()) {
        // Coloured header with progress.
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(kind.colorB), Color(kind.colorA))))
                .windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
                        .clickable(role = Role.Button, onClick = viewModel::onBack).semantics { contentDescription = "Quit level" },
                    contentAlignment = Alignment.Center,
                ) { Icon(SpeakyIcons.Close, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(kind.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${state.tier} · Level ${state.level + 1} · ${state.index + 1} of ${state.total}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "✓ ${state.right}", Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(state.total) { i ->
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = if (i < state.index || (i == state.index && state.answered)) 1f else 0.3f)))
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).shake(state.shakeKey),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val q = state.question) {
                is Question.Spell -> SpellPlay(q, kind, viewModel)
                is Question.Say -> SayPlay(q, kind, viewModel)
                is Question.Situation -> SituationPlay(q, viewModel)
                is Question.Phrasal -> PhrasalPlay(q, kind, viewModel)
                is Question.Odd -> OddPlay(q, viewModel)
                null -> Unit
            }
        }
        if (state.answered) {
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                BigButton(if (state.index + 1 >= state.total) "See results" else "Next →", Color(0xFF17142B), viewModel::onNext)
            }
        }
    }
}

@Composable
private fun BigButton(text: String, background: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(background).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun SoftChip(text: String, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Text(
        text,
        Modifier.clip(RoundedCornerShape(999.dp)).background(colors.surface).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun Feedback(ok: Boolean, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (ok) Color(0xFFDDF6EC) else Color(0xFFFDE2E2)).padding(14.dp).bounceIn(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = if (ok) Color(0xFF0B5E45) else Color(0xFFB42318), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text(body, color = if (ok) Color(0xFF0B5E45) else Color(0xFF8A1C12), fontSize = 14.sp, lineHeight = 20.sp)
    }
}

// ---------- Listen & Spell ----------

@Composable
private fun SpellPlay(q: Question.Spell, kind: GameKind, viewModel: WordGameViewModel) {
    val colors = LocalSpeakMindColors.current
    val word = q.item.word
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(kind.colorA), Color(kind.colorB))))
                .clickable(role = Role.Button) { viewModel.onSpellListen(false) }.semantics { contentDescription = "Hear the word" },
            contentAlignment = Alignment.Center,
        ) { Text("🔊", fontSize = 44.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftChip("🐢 Slower") { viewModel.onSpellListen(true) }
        }
        val hint = q.item.sentence.replace(Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE), "_____")
        Text("“$hint”", color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
        Text(q.item.meaning, color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
    // Slots: one line, narrower for long words.
    val slotWidth = minOf(30f, 330f / word.length - 5f).dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
        word.forEachIndexed { i, ch ->
            val t = q.typed.getOrNull(i)?.let { q.tiles[it] }
            val bg = when {
                q.correct == true -> Color(0xFF19B88A)
                q.correct == false && t != ch -> Color(0xFFFDE2E2)
                t != null -> colors.surface
                else -> colors.track
            }
            Box(Modifier.size(width = slotWidth, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text((t ?: ' ').uppercase(), color = if (q.correct == true) Color.White else if (q.correct == false && t != ch) Color(0xFFB42318) else colors.textPrimary, fontSize = if (word.length > 10) 15.sp else 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    if (q.correct == null) {
        FlowRowCentered {
            q.tiles.forEachIndexed { i, ch ->
                val used = i in q.typed
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(if (used) colors.track else colors.surface)
                        .border(1.5.dp, if (used) Color.Transparent else Color(kind.colorA), RoundedCornerShape(13.dp))
                        .clickable(enabled = !used, role = Role.Button) { viewModel.onSpellTile(i) }
                        .semantics { contentDescription = "Letter $ch" },
                    contentAlignment = Alignment.Center,
                ) { Text(ch.uppercase(), color = if (used) colors.textMuted else Color(kind.colorB), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
            }
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(colors.surface).clickable(role = Role.Button, onClick = viewModel::onSpellDelete)
                    .semantics { contentDescription = "Delete letter" },
                contentAlignment = Alignment.Center,
            ) { Text("⌫", color = colors.textPrimary, fontSize = 18.sp) }
        }
    } else if (q.correct) {
        Feedback(true, "✓ Perfect spelling!", "${q.item.word} · ${q.item.sentence}")
    } else {
        Feedback(false, "Correct spelling: ${q.item.word}", "Saved to your flashcards. ${q.item.sentence}")
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCentered(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

// ---------- Say It! ----------

@Composable
private fun SayPlay(q: Question.Say, kind: GameKind, viewModel: WordGameViewModel) {
    val colors = LocalSpeakMindColors.current
    val shown = q.item.phrase.split(' ')
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("SAY THIS", color = Color(kind.colorB), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        val styled = buildAnnotatedString {
            var wi = 0
            shown.forEachIndexed { i, token ->
                val count = WordGameViewModel.words(token).size
                val ok = q.matched?.let { m -> (wi until wi + count).all { m.getOrElse(it) { true } } }
                wi += count
                withStyle(
                    SpanStyle(
                        color = when (ok) { null -> colors.textPrimary; true -> Color(0xFF0E8F6A); false -> Color(0xFFD97706) },
                        textDecoration = if (ok == false) TextDecoration.Underline else null,
                    ),
                ) { append(token) }
                if (i < shown.lastIndex) append(' ')
            }
        }
        Text(styled, style = displayStyle(26.sp, 32.sp))
        Text(q.item.context, color = colors.textMuted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftChip("🔊 Listen") { viewModel.onSayListen(false) }
            SoftChip("🐢 Slow") { viewModel.onSayListen(true) }
        }
    }
    if (q.matched != null) {
        Feedback(
            q.passed,
            if (q.passed) "✓ ${q.score}% · Great!" else if (q.done) "${q.score}% · Keep practising" else "${q.score}% · Try again (${WordGameViewModel.SAY_TRIES - q.tries} left)",
            if (q.heard.isBlank()) "Orange words weren't heard." else "I heard: “${q.heard}”",
        )
    }
    if (q.done && q.matched == null) Feedback(false, "Skipped", "Tap 🔊 Listen to hear it, then try it next time.")
    q.error?.let { if (!q.done) Feedback(false, "Couldn't hear you", it) }
    if (!q.done) {
        val transition = rememberInfiniteTransition(label = "mic")
        val pulse by transition.animateFloat(1f, 1.12f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(100.dp).graphicsLayer { val s = if (q.listening) pulse else 1f; scaleX = s; scaleY = s }
                    .clip(CircleShape).background(Brush.linearGradient(listOf(Color(kind.colorA), Color(kind.colorB))))
                    .clickable(role = Role.Button, onClick = viewModel::onSayMic).semantics { contentDescription = if (q.listening) "Stop" else "Speak" },
                contentAlignment = Alignment.Center,
            ) { Text(if (q.listening) "⏹" else "🎤", fontSize = 40.sp) }
            Text(if (q.listening) "Listening… say the phrase" else "Tap the mic and say it", color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (q.listening && q.heard.isNotBlank()) Text("“${q.heard}”", color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text("Skip", Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = viewModel::onSaySkip).padding(8.dp), color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- What Would You Say? ----------

@Composable
private fun SituationPlay(q: Question.Situation, viewModel: WordGameViewModel) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFF5A524)))).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { Text(q.item.emoji, fontSize = 34.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(q.item.place.uppercase(), color = Color(0xFF8A4B08), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(q.item.situation, color = Color(0xFF3D2200), fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
        }
    }
    Text("What would you say?", style = displayStyle(22.sp), color = colors.textPrimary)
    q.order.forEachIndexed { slot, original ->
        val picked = q.picked != null
        val best = original == 0
        val reveal = picked && (best || q.picked == slot)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(if (reveal) (if (best) Color(0xFFDDF6EC) else Color(0xFFFDE2E2)) else colors.surface)
                .border(2.dp, if (reveal) (if (best) Color(0xFF19B88A) else Color(0xFFF87171)) else Color.Transparent, RoundedCornerShape(20.dp))
                .clickable(enabled = !picked, role = Role.Button) { viewModel.onPick(slot) }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(q.item.options[original], color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
            if (picked) Text(q.item.notes.getOrElse(original) { "" }, color = if (best) Color(0xFF0B5E45) else if (reveal) Color(0xFFB42318) else colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- Phrasal Pop ----------

@Composable
private fun PhrasalPlay(q: Question.Phrasal, kind: GameKind, viewModel: WordGameViewModel) {
    val colors = LocalSpeakMindColors.current
    val answered = q.picked != null
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("POP THE MISSING WORD", color = Color(kind.colorB), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        val parts = q.item.sentence.split("___")
        Text(
            buildAnnotatedString {
                append(parts.first())
                withStyle(SpanStyle(color = if (answered) Color(0xFF0E8F6A) else Color(kind.colorB), textDecoration = TextDecoration.Underline)) {
                    append(if (answered) q.item.answer else " ? ")
                }
                append(parts.drop(1).joinToString("___"))
            },
            style = displayStyle(24.sp, 32.sp), color = colors.textPrimary,
        )
    }
    val transition = rememberInfiniteTransition(label = "bubbles")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "float")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        q.options.chunked(2).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEachIndexed { c, option ->
                    val i = r * 2 + c
                    val isAnswer = option == q.item.answer
                    val bg = when {
                        answered && isAnswer -> Brush.radialGradient(listOf(Color(0xFFB7F0D8), Color(0xFF19B88A)))
                        answered && option == q.picked -> Brush.radialGradient(listOf(Color(0xFFFFC4C4), Color(0xFFF87171)))
                        else -> Brush.radialGradient(listOf(Color.White, Color(kind.colorA)))
                    }
                    val phase = if ((i % 2) == 0) t else 1f - t
                    Box(
                        Modifier.size(104.dp).graphicsLayer { translationY = -14f * phase }
                            .clip(CircleShape).background(bg).border(2.dp, Color.White, CircleShape)
                            .clickable(enabled = !answered, role = Role.Button) { viewModel.onPick(i) },
                        contentAlignment = Alignment.Center,
                    ) { Text(option, color = Color(0xFF2E1E9E), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
    if (answered) {
        val ok = q.picked == q.item.answer
        Feedback(ok, if (ok) "✓ ${q.item.verb}" else "It's “${q.item.verb}”", "${q.item.verb} = ${q.item.meaning}")
    }
}

// ---------- Odd One Out ----------

@Composable
private fun OddPlay(q: Question.Odd, viewModel: WordGameViewModel) {
    val colors = LocalSpeakMindColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${q.item.emoji}  ${q.item.topic}", color = colors.textMuted, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Text("Which word doesn't belong?", style = displayStyle(24.sp), color = colors.textPrimary)
    }
    val answered = q.picked != null
    q.cards.chunked(2).forEachIndexed { r, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEachIndexed { c, word ->
                val i = r * 2 + c
                val isOdd = word == q.item.odd
                val reveal = answered && (isOdd || word == q.picked)
                Box(
                    Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (reveal) (if (isOdd) Color(0xFFDDF6EC) else Color(0xFFFDE2E2)) else colors.surface)
                        .border(2.dp, if (reveal) (if (isOdd) Color(0xFF19B88A) else Color(0xFFF87171)) else Color.Transparent, RoundedCornerShape(22.dp))
                        .clickable(enabled = !answered, role = Role.Button) { viewModel.onPick(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(word, color = colors.textPrimary, style = displayStyle(21.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) }
            }
        }
    }
    if (answered) {
        val ok = q.picked == q.item.odd
        Feedback(ok, if (ok) "✓ Correct!" else "The odd one is “${q.item.odd}”", q.item.why)
    }
}

// ---------- Results ----------

@Composable
private fun LevelResult(state: WordGameUiState, viewModel: WordGameViewModel) {
    val colors = LocalSpeakMindColors.current
    val kind = state.kind
    val hasNext = state.level + 1 < state.levelCount
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(kind.colorA).copy(alpha = 0.35f), colors.backgroundDark)))
                .windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text(if (state.passed) "🏆" else "💪", fontSize = 76.sp, modifier = Modifier.bounceIn())
            Text(
                if (state.passed) "Level ${state.level + 1} complete!" else "So close!",
                style = displayStyle(30.sp), color = colors.textPrimary, textAlign = TextAlign.Center,
            )
            Text("${state.right} / ${state.total} right", color = Color(kind.colorB), style = displayStyle(24.sp))
            Text(
                if (state.passed) (if (hasNext) "Level ${state.level + 2} is unlocked." else "You finished every ${state.tier} level. Try the next CEFR level!")
                else "Get ${kind.pass} right to unlock level ${state.level + 2}. Replaying is free.",
                color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (state.passed && hasNext) BigButton("Next level →", Brush.linearGradient(listOf(Color(kind.colorA), Color(kind.colorB))), viewModel::onNextLevel)
            BigButton(if (state.passed) "Play again" else "Try again", if (state.passed) colors.textSecondary else Color(0xFF17142B), viewModel::onRetry)
            Text(
                "Back to levels", Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = viewModel::onBack).padding(12.dp),
                color = colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (state.passed) Confetti(key = state.level)
    }
}

@Composable
private fun BigButton(text: String, brush: Brush, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(brush).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
}
