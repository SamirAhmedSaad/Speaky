package com.speakmind.app.feature.games.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.games.data.GameKind
import com.speakmind.app.feature.games.data.GameRepository
import com.speakmind.app.feature.games.data.GamesBadge
import com.speakmind.app.feature.games.data.IdiomGame
import com.speakmind.app.feature.games.data.IdiomRepository
import com.speakmind.app.navigation.FlashcardReviewDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.premium.Paywall
import com.speakmind.app.premium.Premium
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPill
import com.speakmind.app.ui.components.SpeakyProgress
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.components.speakyLevelTone
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The Games arcade: every game as a picture card. */
@Serializable
data object GamesDestination

/** One game's levels for the chosen CEFR level. */
@Serializable
data class GameLevelsDestination(val game: String)

data class GameProgress(val kind: GameKind, val done: Int, val count: Int)

data class GamesUiState(
    val loaded: Boolean = false,
    val tier: String = "A1",
    val appLevel: String = "A1",
    val games: List<GameProgress> = emptyList(),
    val category: String = "All",
    val showTierSheet: Boolean = false,
)

class GamesViewModel(
    private val repository: GameRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val tier = repository.tier()
            val games = GameKind.entries.map { GameProgress(it, repository.completed(it, tier), repository.levelCount(it, tier)) }
            _uiState.update { it.copy(loaded = true, tier = tier, appLevel = repository.appLevel(), games = games) }
        }
    }

    fun onCategory(category: String) = _uiState.update { it.copy(category = category) }
    fun onTierClick() = _uiState.update { it.copy(showTierSheet = true) }
    fun onTierSheetDismiss() = _uiState.update { it.copy(showTierSheet = false) }

    fun onTierPicked(tier: String) {
        _uiState.update { it.copy(showTierSheet = false, tier = tier) }
        viewModelScope.launch {
            repository.setTier(tier)
            refresh()
        }
    }

    fun onOpen(kind: GameKind) = navigationManager.navigate(GameLevelsDestination(kind.key))

    fun onBack() = navigationManager.back()
}

data class GameLevelsUiState(
    val loaded: Boolean = false,
    val kind: GameKind,
    val tier: String = "A1",
    val appLevel: String = "A1",
    val done: Int = 0,
    val count: Int = 0,
    val showTierSheet: Boolean = false,
)

class GameLevelsViewModel(
    destination: GameLevelsDestination,
    private val repository: GameRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {
    private val kind = GameKind.of(destination.game)
    private val _uiState = MutableStateFlow(GameLevelsUiState(kind = kind))
    val uiState: StateFlow<GameLevelsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val tier = repository.tier()
            _uiState.update {
                it.copy(
                    loaded = true, tier = tier, appLevel = repository.appLevel(),
                    done = repository.completed(kind, tier), count = repository.levelCount(kind, tier),
                )
            }
        }
    }

    fun onTierClick() = _uiState.update { it.copy(showTierSheet = true) }
    fun onTierSheetDismiss() = _uiState.update { it.copy(showTierSheet = false) }

    fun onTierPicked(tier: String) {
        _uiState.update { it.copy(showTierSheet = false, tier = tier) }
        viewModelScope.launch {
            repository.setTier(tier)
            refresh()
        }
    }

    /** Plays the next unfinished level (the last one again once all are done). */
    fun onPlay() {
        viewModelScope.launch {
            val tier = repository.tier()
            if (_uiState.value.tier != tier) return@launch
            val last = repository.levelCount(kind, tier) - 1
            openGameLevel(repository, navigationManager, kind, tier, repository.completed(kind, tier).coerceAtMost(last))
        }
    }

    /** Finished levels replay, the next one plays; locked ones do nothing. */
    fun onPlayLevel(level: Int) {
        viewModelScope.launch {
            val tier = repository.tier()
            if (_uiState.value.tier != tier || level > repository.completed(kind, tier)) return@launch
            openGameLevel(repository, navigationManager, kind, tier, level)
        }
    }

    fun onFlashcards() = navigationManager.navigate(FlashcardReviewDestination())

    fun onBack() = navigationManager.back()
}

/**
 * Opens [level] of [kind]: free users get one new level a day per game (replaying is free), otherwise
 * the subscribe screen with a countdown. [replace]: from a game's results, swap that screen.
 */
internal suspend fun openGameLevel(
    repository: GameRepository,
    navigationManager: NavigationManager,
    kind: GameKind,
    tier: String,
    level: Int,
    replace: Boolean = false,
) {
    val open = {
        val destination: Any = when (kind) {
            GameKind.RUSH -> IdiomRushDestination(tier, level)
            GameKind.BUILDER -> IdiomBuilderDestination(tier, level)
            else -> WordGameDestination(kind.key, tier, level)
        }
        // One command: the nav channel keeps only one, so "back, then open" would lose the open.
        if (replace) navigationManager.navigate(destination) { popUpTo(GameLevelsDestination(kind.key)) } else navigationManager.navigate(destination)
    }
    if (level < repository.completed(kind, tier)) return open()
    val waitMs = repository.useFreeLevel(kind, tier, level)
    GamesBadge.waiting.value = repository.freeLevelWaiting()
    if (waitMs == null) return open()
    Premium.showPaywall(
        Paywall(
            title = "Today's free level is done",
            message = "Free players get 1 new ${kind.title} level a day. Subscribe to play every level now, or come back when the timer ends.",
            waitMs = waitMs,
            freeThing = "free level",
            emoji = "🎮",
            onUnlocked = open,
        ),
    )
}

/** The idiom games' results still call this. */
internal suspend fun openIdiomLevel(
    @Suppress("UNUSED_PARAMETER") idioms: IdiomRepository,
    navigationManager: NavigationManager,
    game: IdiomGame,
    tier: String,
    level: Int,
    replace: Boolean = false,
) = openGameLevel(org.koin.mp.KoinPlatform.getKoin().get<GameRepository>(), navigationManager, if (game == IdiomGame.RUSH) GameKind.RUSH else GameKind.BUILDER, tier, level, replace)

@Composable
private fun RefreshOnResume(refresh: () -> Unit) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

fun NavGraphBuilder.gamesScreen() {
    animatedComposable<GamesDestination> {
        val viewModel = koinViewModel<GamesViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val premium by Premium.active.collectAsStateWithLifecycle()
        RefreshOnResume(viewModel::refresh)
        if (!uiState.loaded) {
            Box(Modifier.fillMaxSize().background(LocalSpeakMindColors.current.backgroundDark))
            return@animatedComposable
        }
        ArcadeContent(uiState, premium, viewModel)
        if (uiState.showTierSheet) TierSheet(uiState.tier, uiState.appLevel, viewModel::onTierPicked, viewModel::onTierSheetDismiss)
    }
    animatedComposable<GameLevelsDestination> { backStackEntry ->
        val destination = backStackEntry.toRoute<GameLevelsDestination>()
        val viewModel = koinViewModel<GameLevelsViewModel>(key = "levels-${destination.game}") { parametersOf(destination) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val premium by Premium.active.collectAsStateWithLifecycle()
        RefreshOnResume(viewModel::refresh)
        if (!uiState.loaded) {
            Box(Modifier.fillMaxSize().background(LocalSpeakMindColors.current.backgroundDark))
            return@animatedComposable
        }
        LevelsContent(uiState, premium, viewModel)
        if (uiState.showTierSheet) TierSheet(uiState.tier, uiState.appLevel, viewModel::onTierPicked, viewModel::onTierSheetDismiss)
    }
}

private val CATEGORIES = listOf("All", "Speaking", "Listening", "Vocabulary", "Idioms")

@Composable
private fun ArcadeContent(state: GamesUiState, premium: Boolean, viewModel: GamesViewModel) {
    val colors = LocalSpeakMindColors.current
    val games = state.games.filter { state.category == "All" || it.kind.category == state.category }
    Column(
        Modifier.fillMaxSize().background(colors.backgroundDark).windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeakyBackButton(viewModel::onBack)
            Column(Modifier.weight(1f)) {
                Text("Games", style = displayStyle(28.sp), color = colors.textPrimary)
                Text("Play a few minutes a day, speak better English", color = colors.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (premium) SpeakyPill("Unlimited", colors.goldSoft, colors.onGoldSoft, fontSize = 12.sp)
        }

        TierRow(state.tier, viewModel::onTierClick)

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.forEach { c ->
                val on = c == state.category
                Text(
                    c,
                    Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) colors.textPrimary else colors.surface)
                        .clickable(role = Role.Tab) { viewModel.onCategory(c) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (on) colors.surface else colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        games.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { game -> GameTile(game, Modifier.weight(1f)) { viewModel.onOpen(game.kind) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (!premium) Text("Free: 1 new level a day in each game. Replays are always free.", color = colors.textMuted, fontSize = 13.sp)
    }
}

@Composable
private fun TierRow(tier: String, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val tone = speakyLevelTone(tier)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tone.soft), contentAlignment = Alignment.Center) {
            Text(tier, color = tone.onSoft, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f)) {
            Text("Game level", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(IdiomRepository.TIER_NAMES[tier] ?: tier, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("Change", color = colors.neonCyan, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun GameTile(game: GameProgress, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val kind = game.kind
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(colors.surface).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${kind.title}, level ${(game.done + 1).coerceAtMost(game.count)} of ${game.count}" },
    ) {
        Box {
            GameArt(kind, Modifier.fillMaxWidth().height(116.dp))
            if (!kind.isIdiom && game.done == 0) {
                Text(
                    "NEW", Modifier.padding(10.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF17142B)).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp,
                )
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(kind.title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kind.tagline, color = colors.textMuted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            SpeakyProgress(if (game.count == 0) 0f else game.done.toFloat() / game.count, colors.neonCyan, height = 5.dp)
            Text(
                if (game.done >= game.count) "All ${game.count} levels done" else "Level ${game.done + 1} of ${game.count}",
                color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** A game's picture: its colours, floating bubbles and a big emoji, gently moving. */
@Composable
fun GameArt(kind: GameKind, modifier: Modifier = Modifier, emojiSize: Int = 46) {
    val a = Color(kind.colorA)
    val b = Color(kind.colorB)
    val transition = rememberInfiniteTransition(label = "art")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "t")
    Box(modifier.background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Color.White.copy(alpha = 0.14f), radius = h * 0.75f, center = Offset(w * 0.92f, h * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = h * 0.42f, center = Offset(w * 0.05f, h * 1.0f))
            drawCircle(Color.White.copy(alpha = 0.55f), radius = 6f, center = Offset(w * 0.18f, h * (0.28f - 0.06f * t)))
            drawCircle(Color.White.copy(alpha = 0.45f), radius = 9f, center = Offset(w * 0.82f, h * (0.72f + 0.06f * t)))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = 4f, center = Offset(w * 0.70f, h * (0.22f + 0.05f * t)))
        }
        Box(
            Modifier.size((emojiSize * 1.55f).dp).graphicsLayer { translationY = -8f * t; rotationZ = -4f + 8f * t }
                .clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(kind.emoji, fontSize = emojiSize.sp)
        }
    }
}

@Composable
private fun LevelsContent(state: GameLevelsUiState, premium: Boolean, viewModel: GameLevelsViewModel) {
    val colors = LocalSpeakMindColors.current
    val kind = state.kind
    val allDone = state.done >= state.count
    Column(
        Modifier.fillMaxSize().background(colors.backgroundDark).verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box {
            GameArt(kind, Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)), emojiSize = 70)
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) { SpeakyBackButton(viewModel::onBack) }
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(kind.title, style = displayStyle(28.sp), color = colors.textPrimary)
                Text(kind.tagline + ". ${kind.levelSize} per level, get ${kind.pass} right to unlock the next.", color = colors.textMuted, fontSize = 14.sp, lineHeight = 20.sp)
            }
            TierRow(state.tier, viewModel::onTierClick)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (allDone) "All ${state.count} levels done 🎉" else "Level ${state.done + 1} of ${state.count}",
                        Modifier.weight(1f), color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    if (premium) SpeakyPill("Unlimited", colors.goldSoft, colors.onGoldSoft, fontSize = 12.sp)
                }
                SpeakyProgress(if (state.count == 0) 0f else state.done.toFloat() / state.count, colors.neonCyan, height = 8.dp)
                LevelGrid(state.done, state.count, viewModel::onPlayLevel)
            }
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(kind.colorA), Color(kind.colorB))))
                    .clickable(role = Role.Button, onClick = viewModel::onPlay),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(SpeakyIcons.Play, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (allDone) "Replay last level" else "Play level ${state.done + 1}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (!premium) Text("Free: 1 new level a day. Finished levels can be replayed any time.", color = colors.textMuted, fontSize = 13.sp)
            if (kind != GameKind.SAY && kind != GameKind.SITUATION) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.primarySoft)
                        .clickable(role = Role.Button, onClick = viewModel::onFlashcards).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(SpeakyIcons.Cards, null, tint = colors.onPrimarySoft, modifier = Modifier.size(20.dp))
                    Text("Missed words go to your flashcards", Modifier.weight(1f), color = colors.onPrimarySoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Icon(SpeakyIcons.Chevron, null, tint = colors.onPrimarySoft, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Every level as a bubble: done (tap to replay), the next one, and locked ones. */
@Composable
private fun LevelGrid(done: Int, count: Int, onLevel: (Int) -> Unit) {
    val colors = LocalSpeakMindColors.current
    val green = Color(0xFF19B88A)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until count).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { level ->
                    val finished = level < done
                    val current = level == done
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                            .background(if (finished) green else if (current) colors.neonCyan else colors.track)
                            .clickable(enabled = finished || current, role = Role.Button) { onLevel(level) }
                            .semantics { contentDescription = "Level ${level + 1}" + if (finished) ", done" else if (current) ", next" else ", locked" },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            current -> Text("${level + 1}", color = colors.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            finished -> Icon(SpeakyIcons.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            else -> Icon(SpeakyIcons.Lock, null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierSheet(selected: String, appLevel: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val samples = mapOf(
        "A1" to "Simple words and phrases",
        "A2" to "Everyday situations",
        "B1" to "Travel, work and opinions",
        "B2" to "Natural, fluent English",
        "C1" to "Advanced and subtle",
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFFDAD6E8))) },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Game level", style = displayStyle(22.sp), color = colors.textPrimary)
            Text("Every game uses words and phrases from this level, easiest first.", color = colors.textMuted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            IdiomRepository.TIERS.forEach { tier ->
                val on = tier == selected
                val tone = speakyLevelTone(tier)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) colors.primarySoft else colors.backgroundDark)
                        .clickable(role = Role.RadioButton) { onPick(tier) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tone.soft), contentAlignment = Alignment.Center) {
                        Text(tier, color = tone.onSoft, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(IdiomRepository.TIER_NAMES[tier] ?: tier, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (tier == appLevel) SpeakyPill("Your level", colors.mintSoft, colors.onMintSoft, fontSize = 10.sp, padding = PaddingValues(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Text(samples[tier] ?: "", color = colors.textMuted, fontSize = 13.sp)
                    }
                    if (on) Icon(SpeakyIcons.Check, null, tint = colors.neonCyan, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
