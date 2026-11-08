package com.speakmind.app.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.feature.course.ui.CourseCard
import com.speakmind.app.feature.dailyword.domain.model.DailyWordData
import com.speakmind.app.feature.home.domain.model.DailyCard
import com.speakmind.app.ui.components.BottomBarClearance
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyCard
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.brandFill
import com.speakmind.app.ui.components.SpeakyPill
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.SpeakyProgress
import com.speakmind.app.ui.components.SpeakySectionHeader
import com.speakmind.app.ui.components.SpeakySectionLabel
import com.speakmind.app.ui.components.SpeakyTitle
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// The four tabs of the redesigned home (Home, Learn, Words; Chat is its own screen) and Settings.

/** Near-black hero cards (review, first course); a lifted violet in dark mode so they still stand out. */
@Composable
private fun inkColor(): Color {
    val colors = LocalSpeakMindColors.current
    return when {
        colors.isGold && colors.isDark -> Color(0xFF2A2312)
        colors.isGold -> Color(0xFF2A1A15)
        colors.surface.luminance() < 0.5f -> Color(0xFF2A2550)
        else -> Color(0xFF17142B)
    }
}

private val LEVEL_NAMES = mapOf(
    "A1" to "Beginner", "A2" to "Elementary", "B1" to "Intermediate", "B2" to "Upper intermediate", "C1" to "Advanced",
)

/** CEFR index (A1=0 … C1=4) a course's level label covers, e.g. "Beginner → Intermediate" = 0..2. */
private fun levelRange(label: String?): IntRange? {
    if (label.isNullOrBlank()) return null
    fun index(word: String, low: Boolean): Int? {
        val w = word.lowercase()
        return when {
            "advanced" in w -> 4
            "upper" in w -> 3
            "intermediate" in w -> 2
            "beginner" in w || "elementary" in w -> if (low) 0 else 1
            else -> null
        }
    }
    val parts = label.split("→", "->", " to ").map { it.trim() }
    val lo = index(parts.first(), low = true) ?: return null
    val hi = index(parts.last(), low = false) ?: return null
    return lo..maxOf(lo, hi)
}

/** Courses for this level first (in-progress ones before new), then the rest in their usual order. */
private fun List<CourseCard>.forLevel(level: String): List<CourseCard> {
    val target = ALL_LEVELS.indexOf(level).coerceAtLeast(0)
    val (match, other) = partition { levelRange(it.course.level)?.contains(target) ?: false }
    return match.sortedByDescending { it.done in 1 until it.total } + other
}

/** The started course with the most progress, else null (a new learner). */
private fun List<CourseCard>.inProgress(): CourseCard? =
    filter { it.done > 0 && it.done < it.total }.maxByOrNull { it.done.toFloat() / it.total }

// ---------------- Home ----------------

@Composable
internal fun HomeTab(
    state: HomeUiState,
    courses: List<CourseCard>,
    onlineCount: Int,
    onSettingsClick: () -> Unit,
    onLevelClick: () -> Unit,
    onStreakClick: () -> Unit = {},
    onCourseClick: (CourseCard) -> Unit,
    onAllCoursesClick: () -> Unit,
    onReviewClick: () -> Unit,
    onHubClick: () -> Unit,
    onWordClick: (Long) -> Unit,
    onSpeakWord: () -> Unit,
    onTopicClick: (String) -> Unit,
    onAllTopicsClick: () -> Unit,
) {
    val current = courses.inProgress()
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = BottomBarClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HomeTopBar(
                name = state.userName,
                greeting = if (current == null && state.streakDays <= 1) "Welcome to Speaky" else greeting(),
                streak = state.streakDays,
                level = state.userLevel,
                onSettingsClick = onSettingsClick,
                onLevelClick = onLevelClick,
                onStreakClick = onStreakClick,
            )
        }
        item {
            if (current != null) ContinueCourseCard(current) { onCourseClick(current) }
            else if (courses.isNotEmpty()) FirstCourseCard(state.userLevel, courses.forLevel(state.userLevel), onCourseClick, onAllCoursesClick)
        }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val colors = LocalSpeakMindColors.current
                StatTile(
                    icon = SpeakyIcons.Cards,
                    // Nothing due reads as a win, not a zero.
                    value = if (state.flashcardDueCount == 0L) "All done" else state.flashcardDueCount.toString(),
                    valueIsNumber = state.flashcardDueCount > 0,
                    label = when (state.flashcardDueCount) {
                        0L -> if (state.totalVocab == 0L) "Save some words" else "See you tomorrow"
                        1L -> "word to review"
                        else -> "words to review"
                    },
                    background = colors.mintSoft,
                    color = colors.onMintSoft,
                    onClick = onReviewClick,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatTile(
                    icon = SpeakyIcons.Globe,
                    value = "Speaky Hub",
                    valueIsNumber = false,
                    label = if (onlineCount > 0) "$onlineCount learner${if (onlineCount == 1) "" else "s"} online" else "Chat with learners",
                    background = colors.coralSoft,
                    color = colors.onCoralSoft,
                    onClick = onHubClick,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        state.todayWord?.let { word ->
            item { WordOfTheDayCard(word, onClick = { onWordClick(word.id) }, onSpeak = onSpeakWord) }
        }
        if (state.dailyCards.isNotEmpty()) {
            item { SpeakySectionHeader("Daily topics", action = "See all", onAction = onAllTopicsClick) }
            item { TopicRow(state.dailyCards.first()) { onTopicClick(state.dailyCards.first().scenario.id) } }
        }
    }
}

private fun greeting(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun HomeTopBar(
    name: String,
    greeting: String,
    streak: Int,
    level: String,
    onSettingsClick: () -> Unit,
    onLevelClick: () -> Unit,
    onStreakClick: () -> Unit = {},
) {
    val colors = LocalSpeakMindColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button, onClickLabel = "Settings", onClick = onSettingsClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        InitialAvatar(name, size = 44, fontSize = 18)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(greeting, color = colors.textMuted, fontSize = 13.sp)
            Text(
                name.ifBlank { "Learner" },
                style = displayStyle(22.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        }
        SpeakyPill(
            // A new learner sees an invitation instead of a zero streak.
            text = if (streak > 0) streak.toString() else "Start",
            background = colors.surface,
            color = if (streak > 0) Color(0xFFC2410C) else colors.textMuted,
            onClick = onStreakClick,
            leading = {
                Icon(
                    SpeakyIcons.Flame, null,
                    tint = if (streak > 0) colors.coral else colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        SpeakyPill(text = level, background = colors.primarySoft, color = colors.neonCyanDark, onClick = onLevelClick)
    }
}

@Composable
private fun InitialAvatar(name: String, size: Int, fontSize: Int, onClick: (() -> Unit)? = null) {
    val colors = LocalSpeakMindColors.current
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(colors.primarySoft)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Settings", onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "S",
            color = colors.neonCyan,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ContinueCourseCard(card: CourseCard, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    SpeakyCard(onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(colors.coralSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpeakyIcons.Play, null, tint = colors.coral, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SpeakySectionLabel("Continue · ${card.course.title}")
                Text(
                    "Lesson ${card.done + 1} of ${card.total}",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                SpeakyProgress(card.done.toFloat() / card.total, colors.coral)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(inkColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpeakyIcons.ArrowRight, "Resume lesson", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FirstCourseCard(
    level: String,
    courses: List<CourseCard>,
    onCourseClick: (CourseCard) -> Unit,
    onAllCoursesClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val first = courses.first()
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(inkColor()),
    ) {
        // Decorative circles from the design
        Box(Modifier.align(Alignment.TopEnd).offset(30.dp, (-30).dp).size(130.dp).clip(CircleShape).background(colors.brandFill))
        Box(Modifier.align(Alignment.TopEnd).offset((-40).dp, 50.dp).size(26.dp).clip(CircleShape).background(Color(0xFFF5B301)))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "RECOMMENDED FOR $level",
                color = Color(0xFFC9C5E0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Text("Start your first course", style = displayStyle(24.sp, 28.sp), color = Color.White)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF5BC2F5)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SpeakyIcons.Video, null, tint = Color(0xFF0B3A55), modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(first.course.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(first.course.subtitle?.takeIf { it.isNotBlank() }, if (first.total > 0) "${first.total} lessons" else null)
                            .joinToString(" · "),
                        color = Color(0xFFC9C5E0),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SpeakyPrimaryButton(
                text = "Start lesson 1",
                onClick = { onCourseClick(first) },
                background = Color.White,
                color = Color(0xFF17142B),
                height = 52.dp,
                radius = 16.dp,
                leading = SpeakyIcons.Play,
            )
            if (courses.size > 1) {
                Text(
                    "or browse all ${courses.size} courses",
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAllCoursesClick).padding(4.dp),
                    color = Color(0xFFC9C5E0),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    valueIsNumber: Boolean,
    label: String,
    background: Color,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpeakyCard(modifier = modifier, color = background, onClick = onClick, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Text(value, style = displayStyle(if (valueIsNumber) 30.sp else 20.sp, if (valueIsNumber) 30.sp else 22.sp), color = color, maxLines = 1)
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WordOfTheDayCard(word: DailyWordData, onClick: () -> Unit, onSpeak: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    SpeakyCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeakySectionLabel("Word of the day", Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(colors.primarySoft).clickable(role = Role.Button, onClick = onSpeak),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SpeakyIcons.Speaker, "Listen", tint = colors.neonCyan, modifier = Modifier.size(20.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(word.word, style = displayStyle(28.sp), color = colors.neonCyan)
            Text(
                listOf(word.partOfSpeech, word.level).filter { it.isNotBlank() }.joinToString(" · "),
                modifier = Modifier.padding(bottom = 5.dp),
                color = colors.textMuted,
                fontSize = 14.sp,
            )
        }
        Text(word.meaning, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

/** Topic row of the Daily topics list (letter tile, title, level · category). */
@Composable
private fun TopicRow(card: DailyCard, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val tones = listOf(
        colors.mintSoft to colors.onMintSoft,
        colors.coralSoft to colors.onCoralSoft,
        colors.primarySoft to colors.onPrimarySoft,
        colors.goldSoft to colors.onGoldSoft,
    )
    val (bg, fg) = tones[(card.scenario.title.hashCode() and 0x7fffffff) % tones.size]
    SpeakyCard(onClick = onClick, radius = 22.dp, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(
                    card.categoryIcon.ifBlank { card.scenario.title.take(1) },
                    color = fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(card.scenario.title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(card.scenario.level, card.scenario.category).filter { it.isNotBlank() }.joinToString(" · "),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                )
            }
            Icon(SpeakyIcons.Chevron, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------- Learn ----------------

@Composable
internal fun LearnTab(
    state: HomeUiState,
    courses: List<CourseCard>,
    showStories: Boolean,
    showAiChat: Boolean,
    onLevelSelected: (String) -> Unit,
    onCourseClick: (CourseCard) -> Unit,
    onContinueClick: (CourseCard) -> Unit,
    onAllCoursesClick: () -> Unit,
    onTopicClick: (String) -> Unit,
    onStoriesClick: () -> Unit,
    onAiChatClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = BottomBarClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SpeakyTitle("Learn") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_LEVELS.forEach { level ->
                    val on = level == state.userLevel
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (on) inkColor() else colors.surface)
                            .clickable(role = Role.Tab) { if (!on) onLevelSelected(level) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(level, color = if (on) Color.White else colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            val current = courses.forLevel(state.userLevel).firstOrNull()
            SpeakyCard(radius = 28.dp, padding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SpeakySectionLabel("Level ${state.userLevel} · ${LEVEL_NAMES[state.userLevel] ?: ""}")
                if (current == null) {
                    Text("Courses are on their way", style = displayStyle(20.sp), color = colors.textPrimary)
                } else {
                    Text(current.course.title, style = displayStyle(20.sp), color = colors.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    LessonPath(current)
                    Spacer(Modifier.height(12.dp))
                    SpeakyPrimaryButton(
                        text = if (current.done == 0) "Start lesson 1" else "Continue lesson ${minOf(current.done + 1, current.total)}",
                        onClick = { onContinueClick(current) },
                        background = colors.coral,
                        height = 52.dp,
                        radius = 16.dp,
                        leading = SpeakyIcons.Play,
                    )
                }
            }
        }
        if (courses.isNotEmpty()) {
            item { SpeakySectionHeader("Video courses", action = "See all", onAction = onAllCoursesClick) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(courses.forLevel(state.userLevel), key = { it.course.id }) { card ->
                        VideoCourseCard(card, onClick = { onCourseClick(card) })
                    }
                }
            }
        }
        if (showStories || showAiChat) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showStories) PracticeTile("Read stories", SpeakyIcons.Book, colors.goldSoft, colors.onGoldSoft, onStoriesClick, Modifier.weight(1f))
                    if (showAiChat) PracticeTile("Speaky Chat", SpeakyIcons.Sparkle, colors.primarySoft, colors.onPrimarySoft, onAiChatClick, Modifier.weight(1f))
                }
            }
        }
        if (state.dailyCards.isNotEmpty()) {
            item { SpeakySectionHeader("Daily topics") }
            items(state.dailyCards, key = { it.scenario.id }) { card ->
                TopicRow(card) { onTopicClick(card.scenario.id) }
            }
        }
    }
}

/** Done lessons as green steps, the next one as a big coral button, the rest grey (like Learn.dc.html). */
@Composable
private fun LessonPath(card: CourseCard) {
    val colors = LocalSpeakMindColors.current
    val next = minOf(card.done, card.total - 1).coerceAtLeast(0)
    val first = (next - 2).coerceAtLeast(0)
    val last = minOf(first + 4, card.total - 1)
    val offsets = listOf(24, 120, 0, 40, 140)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        (first..last).forEachIndexed { i, index ->
            val done = index < card.done
            val current = index == next && !done
            Row(
                Modifier.padding(start = if (current) 60.dp else offsets[i % offsets.size].dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (current) {
                    Box(
                        Modifier.size(92.dp).clip(CircleShape).background(colors.coralSoft).padding(8.dp)
                            .clip(CircleShape).background(colors.coral),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SpeakyIcons.Play, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                } else {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(if (done) Color(0xFF19B88A) else colors.track),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) {
                            Icon(SpeakyIcons.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Text("${index + 1}", color = colors.textMuted, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Text(
                    "Lesson ${index + 1}",
                    color = if (done || current) colors.textPrimary else colors.textMuted,
                    fontSize = if (current) 15.sp else 14.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun VideoCourseCard(card: CourseCard, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val tones = listOf(colors.primarySoft to colors.neonCyan, colors.mintSoft to Color(0xFF19B88A), colors.coralSoft to colors.coral, colors.goldSoft to Color(0xFFD4A017))
    val (bg, fg) = tones[(card.course.sort.coerceAtLeast(0)) % tones.size]
    SpeakyCard(modifier = Modifier.width(165.dp), radius = 22.dp, padding = PaddingValues(12.dp), onClick = onClick) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(SpeakyIcons.Video, null, tint = fg, modifier = Modifier.size(28.dp))
        }
        Text(card.course.title, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        SpeakyProgress(if (card.total > 0) card.done.toFloat() / card.total else 0f, fg, height = 5.dp)
    }
}

@Composable
private fun PracticeTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(background).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(title, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------- Words ----------------

@Composable
internal fun WordsTab(
    state: HomeUiState,
    showGroups: Boolean,
    onLookupClick: () -> Unit,
    onReviewClick: () -> Unit,
    onWordBuilderClick: () -> Unit,
    onSavedClick: () -> Unit,
    onGroupsClick: () -> Unit,
    onWordClick: (Long) -> Unit,
    onAllWordsClick: () -> Unit,
    onReminderClick: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = BottomBarClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SpeakyTitle("Words") }
        item {
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(colors.surface)
                    .clickable(role = Role.Button, onClick = onLookupClick).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(SpeakyIcons.Search, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                Text("Look up any word or phrase", color = colors.textMuted, fontSize = 15.sp)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(inkColor()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val due = state.flashcardDueCount
                        when {
                            due > 0 -> {
                                Text("Due for review today", color = Color(0xFFC9C5E0), fontSize = 13.sp)
                                Text("$due word${if (due == 1L) "" else "s"}", style = displayStyle(40.sp, 40.sp), color = Color.White)
                                Text("Spaced repetition", color = Color(0xFFC9C5E0), fontSize = 13.sp)
                            }
                            state.totalVocab > 0 -> {
                                Text("Nothing due today", color = Color(0xFFC9C5E0), fontSize = 13.sp)
                                Text("All caught up!", style = displayStyle(32.sp, 36.sp), color = Color.White)
                                Text("Your words will come back when it's time to review", color = Color(0xFFC9C5E0), fontSize = 13.sp)
                            }
                            else -> {
                                Text("Start your word bank", color = Color(0xFFC9C5E0), fontSize = 13.sp)
                                Text("Save your first word", style = displayStyle(28.sp, 32.sp), color = Color.White)
                                Text("Tap any word in a lesson or article to save it", color = Color(0xFFC9C5E0), fontSize = 13.sp)
                            }
                        }
                    }
                    Box(Modifier.size(54.dp, 70.dp).rotate(8f).clip(RoundedCornerShape(12.dp)).background(colors.brandFill))
                }
                SpeakyPrimaryButton(
                    text = when {
                        state.flashcardDueCount > 0 -> "Start review"
                        state.totalVocab > 0 -> "Practise your words"
                        else -> "Look up a word"
                    },
                    onClick = when {
                        state.flashcardDueCount > 0 -> onReviewClick
                        state.totalVocab > 0 -> onSavedClick
                        else -> onLookupClick
                    },
                    background = Color.White,
                    color = Color(0xFF17142B),
                    height = 52.dp,
                    radius = 16.dp,
                    trailing = SpeakyIcons.ArrowRight,
                )
            }
        }
        item { SpeakySectionHeader("Practice your way") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PracticeTile("Flashcards", SpeakyIcons.Cards, colors.primarySoft, colors.onPrimarySoft, onReviewClick, Modifier.weight(1f))
                    PracticeTile("Word builder", SpeakyIcons.Translate, colors.coralSoft, colors.onCoralSoft, onWordBuilderClick, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PracticeTile("Look up", SpeakyIcons.Search, colors.mintSoft, colors.onMintSoft, onLookupClick, Modifier.weight(1f))
                    if (showGroups) PracticeTile("My groups", SpeakyIcons.Bookmark, colors.goldSoft, colors.onGoldSoft, onGroupsClick, Modifier.weight(1f))
                    else PracticeTile("Saved words", SpeakyIcons.Bookmark, colors.goldSoft, colors.onGoldSoft, onSavedClick, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily words", Modifier.weight(1f), style = displayStyle(18.sp), color = colors.textPrimary)
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(colors.surface).clickable(onClick = onReminderClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(SpeakyIcons.Bell, "Daily word reminder", tint = colors.neonCyan, modifier = Modifier.size(16.dp))
                    Text(
                        if (state.notificationsEnabled) formatTime(state.notificationHour, state.notificationMinute) else "Off",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        val words = listOfNotNull(state.todayWord) + state.recentDailyWords.filter { it.id != state.todayWord?.id }.take(4)
        items(words, key = { it.id }) { word ->
            SpeakyCard(onClick = { onWordClick(word.id) }, radius = 18.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(word.word, color = if (word.id == state.todayWord?.id) colors.neonCyan else colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(word.meaning, color = colors.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (word.id == state.todayWord?.id) {
                        SpeakyPill("Today", colors.primarySoft, colors.onPrimarySoft, fontSize = 12.sp, padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp))
                    } else if (word.sentDate.isNotBlank()) {
                        Text(word.sentDate, color = colors.faint, fontSize = 12.sp)
                    }
                }
            }
        }
        if (state.recentDailyWords.isNotEmpty()) {
            item {
                Text(
                    "See all daily words",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onAllWordsClick).padding(8.dp),
                    color = colors.neonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

// ---------------- Settings ----------------

@Composable
internal fun SettingsLayer(
    state: HomeUiState,
    theme: com.speakmind.app.ui.theme.AppTheme,
    ttsSpeed: Float,
    isPremium: Boolean,
    appVersion: String,
    onBack: () -> Unit,
    onNameClick: () -> Unit,
    onLevelClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onThemeSelected: (com.speakmind.app.ui.theme.AppTheme) -> Unit,
    onReminderClick: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onPrivacyClick: () -> Unit,
    onContactClick: () -> Unit,
    /** Chat profile, Hub alerts, admin tools, sign out (from the community screen). */
    communitySection: @Composable () -> Unit = {},
) {
    val colors = LocalSpeakMindColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.backgroundDark).windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeakyBackButton(onBack)
                SpeakyTitle("Settings", fontSize = 26.sp)
            }
        }
        item {
            SpeakyCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    InitialAvatar(state.userName, size = 56, fontSize = 22, onClick = onNameClick)
                    Column(Modifier.weight(1f).clickable(onClick = onNameClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(state.userName.ifBlank { "Add your name" }, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${state.userLevel} · ${LEVEL_NAMES[state.userLevel] ?: ""}", color = colors.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primarySoft)
                            .clickable(role = Role.Button, onClick = onLevelClick).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Change level", color = colors.onPrimarySoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(inkColor()).clickable(onClick = onPremiumClick).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF5B301)), contentAlignment = Alignment.Center) {
                    Icon(SpeakyIcons.Crown, null, tint = Color(0xFF4A2F00), modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (isPremium) "You're Premium" else "Get Speaky Premium", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (isPremium) "Thank you for supporting Speaky" else "No ads · unlimited chat · gold badge",
                        color = Color(0xFFC9C5E0),
                        fontSize = 13.sp,
                    )
                }
                Icon(SpeakyIcons.Chevron, null, tint = Color(0xFFC9C5E0), modifier = Modifier.size(20.dp))
            }
        }
        item {
            SpeakyCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    ThemeGrid(theme, isPremium, onThemeSelected)
                }
                Divider()
                SettingsRow("Daily word reminder", if (state.notificationsEnabled) formatTime(state.notificationHour, state.notificationMinute) else "Off", onReminderClick)
                Divider()
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Listening speed", Modifier.weight(1f), color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.75f to "0.75×", 1f to "1×", 1.25f to "1.25×").forEach { (speed, label) ->
                            val on = kotlin.math.abs(ttsSpeed - speed) < 0.01f
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) colors.neonCyan else colors.surfaceVariant)
                                    .clickable(role = Role.RadioButton) { onSpeedSelected(speed) }.padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(label, color = if (on) colors.onPrimary else colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item { communitySection() }
        item {
            SpeakyCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                SettingsRow("Privacy policy", null, onPrivacyClick)
                Divider()
                SettingsRow("Contact us", "samir.hayan@gmail.com", onContactClick, chevron = false)
            }
        }
        item {
            Text(
                if (appVersion.isBlank()) "Speaky" else "Speaky $appVersion",
                Modifier.fillMaxWidth().padding(top = 8.dp),
                color = colors.textMuted,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) colors.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) colors.textPrimary else colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, value: String?, onClick: () -> Unit, chevron: Boolean = true) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, Modifier.weight(1f), color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (value != null) Text(value, color = colors.textMuted, fontSize = if (chevron) 14.sp else 13.sp, fontWeight = if (chevron) FontWeight.SemiBold else FontWeight.Normal)
        if (chevron) Icon(SpeakyIcons.Chevron, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalSpeakMindColors.current.track))
}

// ---------------- Daily word reminder ----------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderSheet(
    hour: Int,
    minute: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    var hour12 by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(if (hour % 12 == 0) 12 else hour % 12) }
    var minuteValue by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(minute) }
    var pm by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(hour >= 12) }
    val hour24 = (hour12 % 12) + if (pm) 12 else 0

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFFDAD6E8)))
        },
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(colors.primarySoft), contentAlignment = Alignment.Center) {
                    Icon(SpeakyIcons.Bell, null, tint = colors.neonCyan, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Daily word reminder", style = displayStyle(20.sp), color = colors.textPrimary)
                    Text("A new word every day at your time", color = colors.textMuted, fontSize = 13.sp)
                }
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = colors.neonCyan,
                        checkedThumbColor = colors.onPrimary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent,
                        uncheckedTrackColor = colors.track,
                        uncheckedThumbColor = Color.White,
                    ),
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.backgroundDark).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelPicker(values = (1..12).toList(), selected = hour12, onSelected = { hour12 = it }, label = { it.toString().padStart(2, '0') })
                Text(":", Modifier.padding(horizontal = 10.dp), style = displayStyle(44.sp), color = colors.neonCyan)
                WheelPicker(values = (0..59).toList(), selected = minuteValue, onSelected = { minuteValue = it }, label = { it.toString().padStart(2, '0') })
                Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(false to "AM", true to "PM").forEach { (isPm, text) ->
                        val on = pm == isPm
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) colors.neonCyan else Color.Transparent)
                                .clickable(role = Role.RadioButton) { pm = isPm }.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(text, color = if (on) colors.onPrimary else colors.textMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            SpeakyPrimaryButton(
                text = "Save · ${formatTime(hour24, minuteValue)}",
                onClick = { onSave(hour24, minuteValue) },
                height = 54.dp,
                radius = 16.dp,
            )
        }
    }
}

/** Three visible rows; the middle one is the value. Scroll or tap a neighbour to change it. */
@Composable
private fun <T> WheelPicker(values: List<T>, selected: T, onSelected: (T) -> Unit, label: (T) -> String) {
    val colors = LocalSpeakMindColors.current
    val rowHeight = 52.dp
    val startIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(startIndex)
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(listState)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val centered by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val sizePx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            (listState.firstVisibleItemIndex + if (offset > sizePx / 2) 1 else 0).coerceIn(0, values.lastIndex)
        }
    }
    androidx.compose.runtime.LaunchedEffect(centered) { onSelected(values[centered]) }
    LazyColumn(
        state = listState,
        flingBehavior = fling,
        modifier = Modifier.height(rowHeight * 3).width(72.dp),
        contentPadding = PaddingValues(vertical = rowHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(values.size) { i ->
            val on = i == centered
            Box(
                Modifier.height(rowHeight).fillMaxWidth().clickable(onClickLabel = label(values[i])) {
                    scope.launch { listState.animateScrollToItem(i) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(values[i]),
                    style = if (on) displayStyle(44.sp) else androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = if (on) colors.neonCyan else Color(0xFFB8B4CC),
                )
            }
        }
    }
}

// ---------------- Level picker ----------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun LevelSheet(currentLevel: String, onLevelSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val descriptions = mapOf(
        "A1" to "Basic words and phrases",
        "A2" to "Simple conversations",
        "B1" to "Everyday topics",
        "B2" to "Complex discussions",
        "C1" to "Fluent and natural",
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFFDAD6E8)))
        },
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Choose your level", style = displayStyle(22.sp), color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            ALL_LEVELS.forEach { level ->
                val on = level == currentLevel
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (on) colors.primarySoft else colors.backgroundDark)
                        .clickable(role = Role.RadioButton) { onLevelSelected(level) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) colors.neonCyan else colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(level, color = if (on) colors.onPrimary else colors.neonCyanDark, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(LEVEL_NAMES[level] ?: level, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(descriptions[level] ?: "", color = colors.textMuted, fontSize = 13.sp)
                    }
                    if (on) Icon(SpeakyIcons.Check, null, tint = colors.neonCyan, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ---------------- Theme picker ----------------

private class ThemePreview(val theme: com.speakmind.app.ui.theme.AppTheme, val bg: Color, val accent: Color, val card: Color)

private val THEME_PREVIEWS = listOf(
    ThemePreview(com.speakmind.app.ui.theme.AppTheme.Violet, Color(0xFFF6F5FB), Color(0xFF5B3DF5), Color(0xFFFFFFFF)),
    ThemePreview(com.speakmind.app.ui.theme.AppTheme.Dark, Color(0xFF0F0D1F), Color(0xFF7B61FF), Color(0xFF1C1934)),
    ThemePreview(com.speakmind.app.ui.theme.AppTheme.GoldLuxe, Color(0xFF121008), Color(0xFFD4A017), Color(0xFF1E1A10)),
    ThemePreview(com.speakmind.app.ui.theme.AppTheme.RoseGold, Color(0xFFFFF5F1), Color(0xFFC27A63), Color(0xFFFFFFFF)),
)

/** Four mini previews (two per row); gold ones carry a crown and need Premium. */
@Composable
private fun ThemeGrid(
    selected: com.speakmind.app.ui.theme.AppTheme,
    isPremium: Boolean,
    onSelect: (com.speakmind.app.ui.theme.AppTheme) -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        THEME_PREVIEWS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    val on = p.theme == selected
                    val locked = p.theme.premium && !isPremium
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                            .background(colors.backgroundDark)
                            .then(if (on) Modifier.border(3.dp, p.accent, RoundedCornerShape(18.dp)) else Modifier)
                            .clickable(role = Role.RadioButton) { onSelect(p.theme) }
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).background(p.bg).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(7.dp)).background(p.accent))
                            Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(7.dp)).background(p.card))
                            Box(Modifier.fillMaxWidth(0.7f).height(18.dp).clip(RoundedCornerShape(7.dp)).background(p.card))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(p.theme.label, Modifier.weight(1f), color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            when {
                                locked -> Icon(SpeakyIcons.Lock, "Premium", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                p.theme.premium -> Icon(SpeakyIcons.Crown, null, tint = Color(0xFFD4A017), modifier = Modifier.size(16.dp))
                                on -> Icon(SpeakyIcons.Check, null, tint = colors.neonCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        val system = selected == com.speakmind.app.ui.theme.AppTheme.System
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (system) colors.primarySoft else colors.backgroundDark)
                .clickable(role = Role.RadioButton) { onSelect(com.speakmind.app.ui.theme.AppTheme.System) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Match my phone (light or dark)", Modifier.weight(1f), color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (system) Icon(SpeakyIcons.Check, null, tint = colors.neonCyan, modifier = Modifier.size(18.dp))
        }
    }
}
