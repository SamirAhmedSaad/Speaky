package com.speakmind.app.feature.games.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.games.data.Idiom
import com.speakmind.app.feature.games.data.IdiomGame
import com.speakmind.app.feature.games.data.IdiomRepository
import com.speakmind.app.feature.voice.platform.TextToSpeechEngine
import com.speakmind.app.navigation.FlashcardReviewDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.brandFill
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.components.speakyLevelTone
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class IdiomBuilderDestination(val tier: String, val level: Int)

enum class BuildStage { BUILD, MEANING, SOLVED }

data class BuilderUiState(
    val loading: Boolean = true,
    val tier: String = "A1",
    val level: Int = 0,
    val levelCount: Int = 20,
    val puzzles: List<Idiom> = emptyList(),
    val current: Int = 0,
    /** The idiom's words; [bank] is their shuffled order on the tiles, [placed] the tiles put in so far. */
    val words: List<String> = emptyList(),
    val bank: List<Int> = emptyList(),
    val placed: List<Int> = emptyList(),
    val stage: BuildStage = BuildStage.BUILD,
    val meanings: List<String> = emptyList(),
    val answer: Int = 0,
    val picked: Int? = null,
    val shakeKey: Int = 0,
    /** The last Check found the wrong order; cleared when a tile moves. */
    val wrong: Boolean = false,
    val solvedKey: Int = 0,
    val mistakes: Int = 0,
    val hints: Int = 0,
    val missed: List<Idiom> = emptyList(),
    val finished: Boolean = false,
) {
    val idiom: Idiom? get() = puzzles.getOrNull(current)
    val full: Boolean get() = words.isNotEmpty() && placed.size == words.size

    /** The example with the idiom blanked out, as the clue. */
    val clue: String
        get() {
            val idiom = idiom ?: return ""
            val at = idiom.example.indexOf(idiom.text, ignoreCase = true)
            return if (at < 0) idiom.example else idiom.example.replaceRange(at, at + idiom.text.length, "_____")
        }

    val stars: Int get() = when {
        mistakes == 0 && hints == 0 -> 3
        mistakes + hints <= 3 -> 2
        else -> 1
    }
}

class IdiomBuilderViewModel(
    private val destination: IdiomBuilderDestination,
    private val repository: IdiomRepository,
    private val navigationManager: NavigationManager,
    private val ttsEngine: TextToSpeechEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BuilderUiState(tier = destination.tier, level = destination.level))
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()
    private var pool: List<Idiom> = emptyList()
    /** Idioms already saved to flashcards in this round. */
    private val saved = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            pool = repository.idioms(destination.tier)
            _uiState.value = BuilderUiState(
                loading = false,
                tier = destination.tier,
                level = destination.level,
                levelCount = repository.levelCount(destination.tier),
                puzzles = repository.levelIdioms(destination.tier, destination.level),
            )
            setUp(0)
        }
    }

    private fun setUp(index: Int) {
        val idiom = _uiState.value.puzzles.getOrNull(index) ?: return
        val words = idiom.text.split(' ').filter { it.isNotBlank() }
        var bank = words.indices.shuffled()
        // Never hand out the tiles already in order.
        while (words.size > 1 && bank.map { words[it] } == words) bank = words.indices.shuffled()
        val wrong = pool.asSequence().filter { it.meaning != idiom.meaning }.shuffled().take(2).map { it.meaning }.toList()
        val meanings = (wrong + idiom.meaning).shuffled()
        _uiState.value = _uiState.value.copy(
            current = index,
            words = words,
            bank = bank,
            placed = emptyList(),
            stage = BuildStage.BUILD,
            meanings = meanings,
            answer = meanings.indexOf(idiom.meaning),
            picked = null,
        )
    }

    fun onTile(wordIndex: Int) {
        val state = _uiState.value
        if (state.stage != BuildStage.BUILD || wordIndex in state.placed) return
        _uiState.value = state.copy(placed = state.placed + wordIndex, wrong = false)
    }

    fun onPlacedTile(position: Int) {
        val state = _uiState.value
        if (state.stage != BuildStage.BUILD) return
        _uiState.value = state.copy(placed = state.placed.filterIndexed { i, _ -> i != position }, wrong = false)
    }

    /** Puts the next right word in place, keeping the correct start. */
    fun onHint() {
        val state = _uiState.value
        if (state.stage != BuildStage.BUILD) return
        val placedWords = state.placed.map { state.words[it] }
        val goodPrefix = placedWords.zip(state.words).takeWhile { (a, b) -> a == b }.size
        if (goodPrefix >= state.words.size) return
        val keep = state.placed.take(goodPrefix)
        val next = state.words.indices.first { it !in keep && state.words[it] == state.words[goodPrefix] }
        _uiState.value = state.copy(placed = keep + next, hints = state.hints + 1, wrong = false)
    }

    fun onCheck() {
        val state = _uiState.value
        if (!state.full || state.stage != BuildStage.BUILD) return
        // Compare words, so repeated words ("an eye for an eye") may go in either slot.
        if (state.placed.map { state.words[it] } == state.words) {
            _uiState.value = state.copy(stage = BuildStage.MEANING)
        } else {
            _uiState.value = state.copy(shakeKey = state.shakeKey + 1, mistakes = state.mistakes + 1, wrong = true)
            missed()
        }
    }

    fun onMeaning(option: Int) {
        val state = _uiState.value
        if (state.stage != BuildStage.MEANING || state.picked != null) return
        val ok = option == state.answer
        _uiState.value = state.copy(picked = option, mistakes = state.mistakes + if (ok) 0 else 1)
        if (!ok) missed()
        viewModelScope.launch {
            delay(if (ok) 450 else 1100)
            _uiState.value = _uiState.value.copy(stage = BuildStage.SOLVED, solvedKey = _uiState.value.solvedKey + 1)
            state.idiom?.let { ttsEngine.speak(it.text) }
        }
    }

    /** The idiom and its example, read slowly. */
    fun onListen() {
        val idiom = _uiState.value.idiom ?: return
        viewModelScope.launch { ttsEngine.speak("${idiom.text}. ${idiom.example}", rate = 0.7f) }
    }

    private fun missed() {
        val idiom = _uiState.value.idiom ?: return
        if (!saved.add(idiom.text)) return
        _uiState.value = _uiState.value.copy(missed = _uiState.value.missed + idiom)
        viewModelScope.launch { repository.saveToFlashcards(idiom) }
    }

    fun onNext() {
        val state = _uiState.value
        if (state.current + 1 < state.puzzles.size) return setUp(state.current + 1)
        viewModelScope.launch {
            repository.markCompleted(IdiomGame.BUILDER, destination.tier, destination.level)
            _uiState.value = _uiState.value.copy(finished = true)
        }
    }

    fun onNextLevel() {
        val next = destination.level + 1
        if (next >= _uiState.value.levelCount) return onBack()
        viewModelScope.launch {
            openIdiomLevel(repository, navigationManager, IdiomGame.BUILDER, destination.tier, next, replace = true)
        }
    }

    fun onFlashcards() = navigationManager.navigate(FlashcardReviewDestination())

    fun onBack() = navigationManager.back()
}

fun NavGraphBuilder.idiomBuilderScreen() {
    animatedComposable<IdiomBuilderDestination> { backStackEntry ->
        val destination = backStackEntry.toRoute<IdiomBuilderDestination>()
        val viewModel = koinViewModel<IdiomBuilderViewModel>(key = "builder-${destination.tier}-${destination.level}") { parametersOf(destination) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val colors = LocalSpeakMindColors.current
        Box(Modifier.fillMaxSize().background(colors.backgroundDark)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.neonCyan)
                state.finished -> BuilderResult(state, viewModel::onNextLevel, viewModel::onFlashcards, viewModel::onBack)
                else -> BuilderPlay(state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuilderPlay(state: BuilderUiState, viewModel: IdiomBuilderViewModel) {
    val colors = LocalSpeakMindColors.current
    val haptics = LocalHapticFeedback.current
    val tone = speakyLevelTone(state.tier)
    val idiom = state.idiom ?: return
    LaunchedEffect(state.shakeKey) { if (state.shakeKey > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val ink = colors.textPrimary
    val green = Color(0xFF19B88A)
    val red = Color(0xFFF04438)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeakyIconButton(SpeakyIcons.Close, "Close game", viewModel::onBack, iconSize = 18.dp)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.puzzles.indices.forEach { i ->
                        val done = i < state.current || (i == state.current && state.stage == BuildStage.SOLVED)
                        Box(
                            Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(999.dp))
                                .background(if (done) green else if (i == state.current) colors.neonCyan else colors.track),
                        )
                    }
                }
                Text(
                    "${state.tier} · ${state.level + 1}",
                    Modifier.clip(RoundedCornerShape(999.dp)).background(tone.soft).padding(horizontal = 12.dp, vertical = 8.dp),
                    color = tone.onSoft, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (state.stage) {
                        BuildStage.BUILD -> "STEP 1 OF 2 · BUILD THE IDIOM"
                        BuildStage.MEANING -> "STEP 2 OF 2 · WHAT DOES IT MEAN?"
                        BuildStage.SOLVED -> "SOLVED · ${state.current + 1} OF ${state.puzzles.size}"
                    },
                    color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
                )
                Text(
                    if (state.stage == BuildStage.BUILD) "Put the words in the right order" else "“${idiom.text}”",
                    style = displayStyle(26.sp, 31.sp), color = ink,
                )
            }

            // Answer board.
            val solvedBoard = state.stage != BuildStage.BUILD
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 120.dp).shake(state.shakeKey).clip(RoundedCornerShape(24.dp)).background(colors.surface)
                    .border(2.dp, if (solvedBoard) green else if (state.wrong) red else colors.track, RoundedCornerShape(24.dp)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                if (state.placed.isEmpty()) {
                    Text("Tap the words below in order", Modifier.padding(vertical = 30.dp), color = colors.textMuted, fontSize = 15.sp)
                }
                state.placed.forEachIndexed { position, wordIndex ->
                    Tile(
                        state.words[wordIndex],
                        background = if (solvedBoard) green else if (state.wrong) Color(0xFFFDE2E2) else colors.brandFill,
                        color = if (state.wrong) Color(0xFF9B1C1C) else Color.White,
                        onClick = { viewModel.onPlacedTile(position) },
                    )
                }
            }

            if (state.wrong) {
                Text("Not quite. Tap a word to move it back.", color = red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.goldSoft), contentAlignment = Alignment.Center) {
                    Icon(SpeakyIcons.Sparkle, null, tint = colors.onGoldSoft, modifier = Modifier.size(20.dp))
                }
                Text(state.clue, Modifier.weight(1f), color = colors.textSecondary, fontSize = 14.sp, fontStyle = FontStyle.Italic, lineHeight = 20.sp)
            }

            when (state.stage) {
                BuildStage.BUILD -> FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.bank.forEach { wordIndex ->
                        val used = wordIndex in state.placed
                        Tile(
                            state.words[wordIndex],
                            background = if (used) Color.Transparent else colors.surface,
                            color = if (used) Color.Transparent else ink,
                            dashed = used,
                            onClick = { viewModel.onTile(wordIndex) },
                        )
                    }
                }
                BuildStage.MEANING -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.meanings.forEachIndexed { i, meaning ->
                        val answered = state.picked != null
                        val (bg, border, fg) = when {
                            answered && i == state.answer -> Triple(colors.mintSoft, green, colors.onMintSoft)
                            answered && i == state.picked -> Triple(Color(0xFFFDE2E2), Color(0xFFF04438), Color(0xFF9B1C1C))
                            else -> Triple(colors.surface, colors.track, ink)
                        }
                        Text(
                            meaning.replaceFirstChar { it.uppercase() },
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                                .border(2.dp, border, RoundedCornerShape(18.dp))
                                .clickable(enabled = !answered, role = Role.Button) { viewModel.onMeaning(i) }
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
                BuildStage.SOLVED -> Column(
                    Modifier.fillMaxWidth().bounceIn().clip(RoundedCornerShape(22.dp)).background(colors.mintSoft).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(SpeakyIcons.Check, null, tint = colors.onMintSoft, modifier = Modifier.size(20.dp))
                        Text(if (idiom in state.missed) "Got it! Saved to flashcards" else "Perfect!", color = colors.onMintSoft, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Means: ${idiom.meaning}", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(idiom.example, color = colors.textSecondary, fontSize = 15.sp, fontStyle = FontStyle.Italic)
                    Row(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(14.dp)).background(colors.surface)
                            .clickable(role = Role.Button, onClick = viewModel::onListen).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(SpeakyIcons.Speaker, null, tint = colors.onMintSoft, modifier = Modifier.size(18.dp))
                        Text("Listen slowly", color = colors.onMintSoft, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            when (state.stage) {
                BuildStage.BUILD -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpeakyIconButton(SpeakyIcons.Sparkle, "Show a hint", viewModel::onHint, size = 58.dp, iconSize = 22.dp)
                    SpeakyPrimaryButton(
                        "Check",
                        viewModel::onCheck,
                        Modifier.weight(1f),
                        enabled = state.full,
                        background = ink,
                        color = colors.backgroundDark,
                        height = 58.dp,
                    )
                }
                BuildStage.MEANING -> Unit
                BuildStage.SOLVED -> SpeakyPrimaryButton(
                    if (state.current + 1 < state.puzzles.size) "Next idiom" else "Finish level",
                    viewModel::onNext,
                    background = green,
                    color = Color.White,
                    height = 58.dp,
                )
            }
        }
        if (state.solvedKey > 0 && state.stage == BuildStage.SOLVED) Confetti(state.solvedKey, count = 40)
    }
}

@Composable
private fun Tile(word: String, background: Color, color: Color, dashed: Boolean = false, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Text(
        word,
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (dashed) colors.track.copy(alpha = 0.6f) else background)
            .clickable(enabled = !dashed, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun BuilderResult(state: BuilderUiState, onNextLevel: () -> Unit, onFlashcards: () -> Unit, onClose: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val green = Color(0xFF19B88A)
    val lastLevel = state.level + 1 >= state.levelCount
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row { Spacer(Modifier.weight(1f)); SpeakyIconButton(SpeakyIcons.Close, "Close", onClose, iconSize = 18.dp) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.bounceIn().size(110.dp).clip(RoundedCornerShape(36.dp)).background(green), contentAlignment = Alignment.Center) {
                    Icon(SpeakyIcons.Trophy, null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
                Text(
                    "${IdiomRepository.TIER_NAMES[state.tier]} · Level ${state.level + 1} done!",
                    style = displayStyle(28.sp, 32.sp), color = colors.textPrimary, textAlign = TextAlign.Center,
                )
                Text(
                    "${state.puzzles.size} idioms built · ${state.hints} hint${if (state.hints == 1) "" else "s"} · ${state.mistakes} mistake${if (state.mistakes == 1) "" else "s"}",
                    color = colors.textSecondary, fontSize = 15.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { i ->
                        Icon(SpeakyIcons.Crown, null, tint = if (i < state.stars) Color(0xFFFFC53D) else colors.track, modifier = Modifier.size(30.dp))
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Idioms you built", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                state.puzzles.forEach { idiom ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(if (idiom in state.missed) Color(0xFFF04438) else green))
                        Text(idiom.text, Modifier.weight(1f), color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(idiom.meaning, Modifier.weight(1f), color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.End)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.primarySoft).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row {
                    Text(IdiomRepository.TIER_NAMES[state.tier] ?: state.tier, Modifier.weight(1f), color = colors.onPrimarySoft, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${state.level + 1} / ${state.levelCount} levels", color = colors.onPrimarySoft, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                com.speakmind.app.ui.components.SpeakyProgress((state.level + 1f) / state.levelCount, colors.neonCyan, height = 8.dp)
            }
            Spacer(Modifier.weight(1f))
            SpeakyPrimaryButton(if (lastLevel) "Back to games" else "Next level", if (lastLevel) onClose else onNextLevel, height = 58.dp)
            if (state.missed.isNotEmpty()) {
                SpeakyPrimaryButton(
                    "Review ${state.missed.size} missed idioms",
                    onFlashcards,
                    background = colors.surface,
                    color = colors.textPrimary,
                    height = 52.dp,
                    leading = SpeakyIcons.Cards,
                )
            }
        }
        Confetti(Unit)
    }
}
