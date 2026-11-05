package com.speakmind.app.feature.games.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.TintStatusBar
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class IdiomRushDestination(val tier: String, val level: Int)

data class RushUiState(
    val loading: Boolean = true,
    val tier: String = "A1",
    val level: Int = 0,
    val levelCount: Int = 20,
    val seconds: Int = 60,
    val timeLeft: Int = 60,
    val idiom: Idiom? = null,
    val options: List<String> = emptyList(),
    val answer: Int = 0,
    val picked: Int? = null,
    val score: Int = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val lastGain: Int = 0,
    val right: Int = 0,
    val answered: Int = 0,
    val shakeKey: Int = 0,
    val popKey: Int = 0,
    val finished: Boolean = false,
    val passed: Boolean = false,
    val best: Int = 0,
    val newBest: Boolean = false,
    val missed: List<Idiom> = emptyList(),
    /** A wrong answer stops the clock until the player taps Next. */
    val paused: Boolean = false,
) {
    val multiplier: Int get() = multiplierFor(combo)
}

private fun multiplierFor(combo: Int) = when {
    combo >= 4 -> 3
    combo >= 2 -> 2
    else -> 1
}

class IdiomRushViewModel(
    private val destination: IdiomRushDestination,
    private val repository: IdiomRepository,
    private val navigationManager: NavigationManager,
    private val ttsEngine: TextToSpeechEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RushUiState(tier = destination.tier, level = destination.level))
    val uiState: StateFlow<RushUiState> = _uiState.asStateFlow()

    private var queue: List<Idiom> = emptyList()
    private var pool: List<Idiom> = emptyList()
    private var index = 0
    private var timer: Job? = null

    init {
        viewModelScope.launch {
            pool = repository.idioms(destination.tier)
            start()
        }
    }

    private suspend fun start() {
        val levelIdioms = repository.levelIdioms(destination.tier, destination.level)
        // This level's idioms first, then earlier ones to review, then this level's again.
        val earlier = pool.take(destination.level * IdiomRepository.LEVEL_SIZE).shuffled()
        queue = levelIdioms.shuffled() + earlier + levelIdioms.shuffled()
        index = 0
        val seconds = IdiomRepository.rushSeconds(destination.tier)
        _uiState.value = RushUiState(
            loading = false,
            tier = destination.tier,
            level = destination.level,
            levelCount = repository.levelCount(destination.tier),
            seconds = seconds,
            timeLeft = seconds,
            best = repository.bestScore(destination.tier),
        )
        showQuestion()
        timer?.cancel()
        timer = viewModelScope.launch {
            while (_uiState.value.timeLeft > 0) {
                delay(1000)
                if (!_uiState.value.paused) _uiState.value = _uiState.value.copy(timeLeft = _uiState.value.timeLeft - 1)
            }
            finish()
        }
    }

    private fun showQuestion() {
        if (queue.isEmpty()) return
        val idiom = queue[index % queue.size]
        index++
        val wrong = pool.asSequence().filter { it.meaning != idiom.meaning }.shuffled().take(2).map { it.meaning }.toList()
        val options = (wrong + idiom.meaning).shuffled()
        _uiState.value = _uiState.value.copy(idiom = idiom, options = options, answer = options.indexOf(idiom.meaning), picked = null)
    }

    fun onPick(option: Int) {
        val state = _uiState.value
        val idiom = state.idiom ?: return
        if (state.picked != null || state.finished) return
        val ok = option == state.answer
        val combo = if (ok) state.combo + 1 else 0
        val gain = if (ok) multiplierFor(combo) else 0
        _uiState.value = state.copy(
            picked = option,
            combo = combo,
            bestCombo = maxOf(state.bestCombo, multiplierFor(combo)),
            score = state.score + gain,
            lastGain = gain,
            right = state.right + if (ok) 1 else 0,
            answered = state.answered + 1,
            shakeKey = state.shakeKey + if (ok) 0 else 1,
            popKey = state.popKey + if (ok) 1 else 0,
            missed = if (ok || idiom in state.missed) state.missed else state.missed + idiom,
            paused = !ok,
        )
        viewModelScope.launch {
            if (!ok) return@launch repository.saveToFlashcards(idiom)
            delay(650)
            if (!_uiState.value.finished) showQuestion()
        }
    }

    /** After a wrong answer: the idiom and its example, read slowly. */
    fun onListen() {
        val idiom = _uiState.value.idiom ?: return
        viewModelScope.launch { ttsEngine.speak("${idiom.text}. ${idiom.example}", rate = 0.7f) }
    }

    /** After a wrong answer: starts the clock again with the next idiom. */
    fun onNext() {
        if (!_uiState.value.paused) return
        ttsEngine.stop()
        _uiState.value = _uiState.value.copy(paused = false)
        showQuestion()
    }

    private suspend fun finish() {
        val state = _uiState.value
        val passed = state.right >= IdiomRepository.RUSH_PASS
        if (passed) repository.markCompleted(IdiomGame.RUSH, destination.tier, destination.level)
        val newBest = state.score > state.best && state.score > 0
        repository.saveBestScore(destination.tier, state.score)
        _uiState.value = state.copy(finished = true, passed = passed, newBest = newBest, best = maxOf(state.best, state.score))
    }

    fun onPlayAgain() {
        viewModelScope.launch { start() }
    }

    fun onNextLevel() {
        val next = destination.level + 1
        if (next >= _uiState.value.levelCount) return onBack()
        viewModelScope.launch {
            openIdiomLevel(repository, navigationManager, IdiomGame.RUSH, destination.tier, next, replace = true)
        }
    }

    fun onFlashcards() = navigationManager.navigate(FlashcardReviewDestination())

    fun onBack() = navigationManager.back()
}

private val RushBg = Color(0xFF1E1450)
private val RushSoft = Color(0x1FFFFFFF)
private val RushMuted = Color(0xFFD9D3FF)
private val RushOrange = Color(0xFFFF7A45)
private val RushGreen = Color(0xFF19B88A)
private val RushRed = Color(0xFFF04438)

fun NavGraphBuilder.idiomRushScreen() {
    animatedComposable<IdiomRushDestination> { backStackEntry ->
        val destination = backStackEntry.toRoute<IdiomRushDestination>()
        val viewModel = koinViewModel<IdiomRushViewModel>(key = "rush-${destination.tier}-${destination.level}") { parametersOf(destination) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        TintStatusBar(RushBg)
        Box(Modifier.fillMaxSize().background(RushBg)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                state.finished -> RushResult(state, viewModel::onPlayAgain, viewModel::onNextLevel, viewModel::onFlashcards, viewModel::onBack)
                else -> RushPlay(state, viewModel::onPick, viewModel::onListen, viewModel::onNext, viewModel::onBack)
            }
        }
    }
}

@Composable
private fun RushPlay(state: RushUiState, onPick: (Int) -> Unit, onListen: () -> Unit, onNext: () -> Unit, onQuit: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.shakeKey) { if (state.shakeKey > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    LaunchedEffect(state.popKey) { if (state.popKey > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val timeFraction by animateFloatAsState(state.timeLeft.toFloat() / state.seconds, tween(1000), label = "timer")
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIcon(SpeakyIcons.Close, "Quit round", onQuit)
            Column(Modifier.weight(1f)) {
                Text("SCORE", color = Color(0xFFB8AFF0), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                Text("${state.score}", style = displayStyle(28.sp, 30.sp), color = Color.White)
            }
            val hot = state.multiplier > 1
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(if (hot) RushOrange else RushSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(SpeakyIcons.Flame, null, tint = if (hot) Color.White else RushMuted, modifier = Modifier.size(16.dp))
                Text("Combo ×${state.multiplier}", color = if (hot) Color.White else RushMuted, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(999.dp)).background(RushSoft)) {
                Box(
                    Modifier.fillMaxWidth(timeFraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(999.dp))
                        .background(if (state.timeLeft <= 15) RushOrange else Color(0xFF7DE3BE)),
                )
            }
            Text("${state.timeLeft}s", Modifier.width(40.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
        }

        val idiom = state.idiom
        if (idiom != null) {
            Column(
                Modifier.fillMaxWidth().shake(state.shakeKey).pop(state.popKey).clip(RoundedCornerShape(28.dp)).background(Color.White)
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${state.tier} · LEVEL ${state.level + 1} · WHAT DOES IT MEAN?",
                    color = Color(0xFF6B6880), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, textAlign = TextAlign.Center,
                )
                Text("“${idiom.text}”", style = displayStyle(30.sp, 34.sp), color = Color(0xFF3A22C9), textAlign = TextAlign.Center)
                Text(idiom.example, color = Color(0xFF3B3852), fontSize = 15.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, lineHeight = 21.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.options.forEachIndexed { i, option ->
                val answered = state.picked != null
                val isRight = answered && i == state.answer
                val isWrong = answered && i == state.picked && i != state.answer
                val bg = when {
                    isRight -> RushGreen
                    isWrong -> RushRed
                    answered -> Color(0x0FFFFFFF)
                    else -> Color(0x1AFFFFFF)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                        .clickable(enabled = !answered, role = Role.Button) { onPick(i) }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option.replaceFirstChar { it.uppercase() },
                        Modifier.weight(1f),
                        color = if (answered && !isRight && !isWrong) Color(0x73FFFFFF) else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (isRight) Icon(SpeakyIcons.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    if (isWrong) Icon(SpeakyIcons.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        val picked = state.picked
        if (picked != null && picked == state.answer) {
            Text(
                "+${state.lastGain}  Nice!",
                Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(999.dp))
                    .background(RushGreen).padding(horizontal = 18.dp, vertical = 10.dp),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            )
        }
        if (state.paused && idiom != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(RushSoft).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Timer paused · saved to your flashcards", color = Color(0xFFB8AFF0), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text("“${idiom.text}” means ${idiom.meaning}.", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x26FFFFFF))
                            .clickable(role = Role.Button, onClick = onListen),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(SpeakyIcons.Speaker, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Listen slowly", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Row(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(RushOrange)
                            .clickable(role = Role.Button, onClick = onNext),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Next", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(SpeakyIcons.ArrowRight, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (state.answered == 0) "Answer fast. Two in a row doubles your points."
            else "${state.right} right · ${IdiomRepository.RUSH_PASS} needed to pass",
            Modifier.fillMaxWidth(), color = Color(0xFFB8AFF0), fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RushResult(state: RushUiState, onPlayAgain: () -> Unit, onNextLevel: () -> Unit, onFlashcards: () -> Unit, onClose: () -> Unit) {
    val lastLevel = state.level + 1 >= state.levelCount
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row { Spacer(Modifier.weight(1f)); RoundIcon(SpeakyIcons.Close, "Close", onClose) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        state.passed && state.newBest -> "LEVEL PASSED · NEW BEST"
                        state.passed -> "LEVEL ${state.level + 1} PASSED"
                        else -> "SO CLOSE!"
                    },
                    Modifier.clip(RoundedCornerShape(999.dp)).background(if (state.passed) Color(0xFFFFC53D) else RushSoft)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    color = if (state.passed) Color(0xFF4A2E00) else Color.White,
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
                )
                Text("${state.score}", Modifier.bounceIn().padding(top = 8.dp), style = displayStyle(96.sp, 100.sp), color = Color.White)
                Text(
                    if (state.passed) "points · best ${state.best}"
                    else "You got ${state.right} right. Get ${IdiomRepository.RUSH_PASS} to pass this level.",
                    color = RushMuted, fontSize = 16.sp, textAlign = TextAlign.Center,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("${state.right}/${state.answered}", "right", Color(0xFF7DE3BE), Modifier.weight(1f))
                Stat("×${state.bestCombo}", "best combo", Color(0xFFFFB38F), Modifier.weight(1f))
                Stat("${state.missed.size}", "to practise", Color.White, Modifier.weight(1f))
            }
            if (state.missed.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Practise these again", Modifier.weight(1f), color = Color(0xFF17142B), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "In flashcards ›",
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFECE8FF))
                                .clickable(role = Role.Button, onClick = onFlashcards).padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color(0xFF2E1E9E), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    state.missed.take(5).forEach { idiom ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(RushRed))
                            Column {
                                Text(idiom.text, color = Color(0xFF17142B), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text(idiom.meaning.replaceFirstChar { it.uppercase() }, color = Color(0xFF6B6880), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.passed && !lastLevel) {
                ResultButton("Next level", RushOrange, onNextLevel)
                ResultButton("Play again", RushSoft, onPlayAgain)
            } else {
                ResultButton(if (state.passed) "Play again" else "Try again", RushOrange, onPlayAgain)
                ResultButton("Back to games", RushSoft, onClose)
            }
        }
        if (state.passed) Confetti(state.score)
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x1AFFFFFF)).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = displayStyle(24.sp), color = color)
        Text(label, color = RushMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultButton(text: String, background: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp)).background(background).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).background(RushSoft).clickable(role = Role.Button, onClick = onClick)
            .border(0.dp, Color.Transparent, RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
