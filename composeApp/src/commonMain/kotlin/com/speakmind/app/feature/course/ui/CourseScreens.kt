package com.speakmind.app.feature.course.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.speakmind.app.feature.course.data.CourseBook
import com.speakmind.app.feature.course.data.Lesson
import com.speakmind.app.feature.course.data.LessonResult
import com.speakmind.app.feature.course.data.LessonWord
import com.speakmind.app.feature.course.data.LessonSummary
import com.speakmind.app.navigation.CourseBookDestination
import com.speakmind.app.navigation.CourseDestination
import com.speakmind.app.navigation.LessonDestination
import com.speakmind.app.navigation.LessonExamDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.rememberInterstitialAdState
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Calls [onResume] whenever the screen comes back (e.g. after finishing an exam). */
@Composable
private fun OnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onResume() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

// ---------------- Home section ----------------

/** "Courses" section on Home (above Study Hub). Hidden until a course is published. */
@Composable
fun CoursesHomeSection(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<CoursesHomeViewModel>()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    OnResume(viewModel::refresh)
    if (cards.isEmpty()) return
    val colors = LocalSpeakMindColors.current
    Column(modifier) {
        Spacer(Modifier.height(24.sdp))
        Text(
            text = "Courses",
            style = MaterialTheme.typography.titleLarge.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.sdp),
        )
        Spacer(Modifier.height(12.sdp))
        cards.forEach { card ->
            HubCard(
                title = card.course.title,
                subtitle = if (card.total == 0) card.course.subtitle.orEmpty()
                           else "${card.done}/${card.total} lessons completed",
                icon = Icons.Default.School,
                accent = colors.gold,
                progress = if (card.total > 0) card.done.toFloat() / card.total else null,
                onClick = { viewModel.onCourseClicked(card.course) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.sdp, vertical = 6.sdp),
            )
        }
    }
}

/** Same look as the Home "Study Hub" boxes. */
@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    done: Boolean = false,
    locked: Boolean = false,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = modifier
            .alpha(if (locked) 0.55f else 1f)
            .clip(RoundedCornerShape(16.sdp))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.05f), colors.surfaceVariant.copy(alpha = 0.7f))))
            .border(1.sdp, accent.copy(alpha = 0.22f), RoundedCornerShape(16.sdp))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 16.sdp, vertical = 14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.sdp).clip(RoundedCornerShape(12.sdp)).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted))
            }
            if (progress != null) {
                Spacer(Modifier.height(6.sdp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = accent,
                    trackColor = accent.copy(alpha = 0.15f),
                    gapSize = 0.sdp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(4.sdp).clip(RoundedCornerShape(2.sdp)),
                )
            }
        }
        Spacer(Modifier.width(8.sdp))
        if (locked) {
            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = colors.textMuted, modifier = Modifier.size(20.sdp))
        } else if (done) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = colors.success, modifier = Modifier.size(22.sdp))
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(20.sdp),
            )
        }
    }
}

@Composable
private fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(Modifier.fillMaxSize().background(colors.backgroundGradient).navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 48.sdp, bottom = 8.sdp).padding(horizontal = 4.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }
        content(Modifier.weight(1f))
        BannerAdView()
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalSpeakMindColors.current.gold)
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.sdp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium.copy(color = LocalSpeakMindColors.current.textMuted))
    }
}

// ---------------- Course: list of books ----------------

fun NavGraphBuilder.courseScreen() {
    animatedComposable<CourseDestination> { entry ->
        val dest = entry.toRoute<CourseDestination>()
        val viewModel = koinViewModel<CourseOutlineViewModel>(key = "course-${dest.courseId}") { parametersOf(dest.courseId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        OnResume(viewModel::refresh)
        val colors = LocalSpeakMindColors.current
        ScreenScaffold(state.outline?.course?.title ?: dest.title, viewModel::onBack) { modifier ->
            val outline = state.outline
            when {
                state.isLoading && outline == null -> Loading(modifier)
                outline == null -> Message("Couldn't load the course. Check your connection.", modifier)
                else -> LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 8.sdp),
                    verticalArrangement = Arrangement.spacedBy(12.sdp),
                ) {
                    outline.course.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        item { Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary)) }
                    }
                    items(outline.books, key = { it.id }) { book: CourseBook ->
                        val lessons = outline.lessonsOf(book.id)
                        val done = lessons.count { it.id in state.results }
                        HubCard(
                            title = book.title,
                            subtitle = when {
                                lessons.isEmpty() -> "Locked · coming soon"
                                else -> "${lessons.size} lesson${if (lessons.size == 1) "" else "s"} · $done completed"
                            },
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            accent = colors.neonCyan,
                            progress = if (lessons.isEmpty()) null else done.toFloat() / lessons.size,
                            done = lessons.isNotEmpty() && done == lessons.size,
                            locked = lessons.isEmpty(),
                            onClick = { viewModel.onBookClicked(book.id, book.title) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Book: list of lessons ----------------

fun NavGraphBuilder.courseBookScreen() {
    animatedComposable<CourseBookDestination> { entry ->
        val dest = entry.toRoute<CourseBookDestination>()
        val viewModel = koinViewModel<CourseOutlineViewModel>(key = "book-${dest.bookId}") { parametersOf(dest.courseId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val lessonAd = rememberInterstitialAdState()
        OnResume(viewModel::refresh)
        val colors = LocalSpeakMindColors.current
        ScreenScaffold(state.outline?.books?.find { it.id == dest.bookId }?.title ?: dest.title, viewModel::onBack) { modifier ->
            val lessons = state.outline?.lessonsOf(dest.bookId)
            when {
                state.isLoading && lessons == null -> Loading(modifier)
                lessons.isNullOrEmpty() -> Message("Lessons for this book are coming soon.", modifier)
                else -> LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 8.sdp),
                    verticalArrangement = Arrangement.spacedBy(12.sdp),
                ) {
                    items(lessons, key = { it.id }) { lesson: LessonSummary ->
                        val result = state.results[lesson.id]
                        HubCard(
                            title = "Lesson ${lesson.number}: ${lesson.title}",
                            subtitle = result?.let { "Completed · exam ${it.score}/${it.total}" } ?: "Not started",
                            icon = Icons.Default.PlayArrow,
                            accent = colors.magenta,
                            done = result != null,
                            onClick = { lessonAd.unlock("lesson:${lesson.id}", "Unlock this lesson") { viewModel.onLessonClicked(lesson.id) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Lesson page ----------------

private val LESSON_TABS = listOf("Words", "Fine line", "Phrases", "Grammar")

fun NavGraphBuilder.lessonScreen() {
    animatedComposable<LessonDestination> { entry ->
        val dest = entry.toRoute<LessonDestination>()
        val viewModel = koinViewModel<LessonViewModel>(key = "lesson-${dest.lessonId}") { parametersOf(dest.lessonId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        OnResume(viewModel::refresh)
        val lesson = state.lesson
        ScreenScaffold(lesson?.let { "Lesson ${it.number}: ${it.title}" } ?: "Lesson", viewModel::onBack) { modifier ->
            when {
                state.isLoading -> Loading(modifier)
                lesson == null -> Message("Couldn't load the lesson. Check your connection.", modifier)
                else -> LessonBody(lesson, state.result, viewModel::onStartExam, viewModel::onExplainLanguage, modifier)
            }
        }
    }
}

@Composable
private fun LessonBody(
    lesson: Lesson,
    result: LessonResult?,
    onStartExam: () -> Unit,
    onExplainLanguage: (arabic: Boolean) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalSpeakMindColors.current
    val openYouTube = rememberYouTubeOpener()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val arabic = rememberExplainArabic()
    CompositionLocalProvider(LocalExplainArabic provides arabic) {
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 8.sdp),
            verticalArrangement = Arrangement.spacedBy(10.sdp),
        ) {
            item {
                // Video: opens in the YouTube app, so the author gets the view.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.sdp))
                        .background(Color(0xFFE53935).copy(alpha = 0.1f))
                        .border(1.sdp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(16.sdp))
                        .clickable { openYouTube(lesson.videoUrl) }
                        .padding(14.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.sdp).clip(RoundedCornerShape(12.sdp)).background(Color(0xFFE53935)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.sdp))
                    }
                    Spacer(Modifier.width(12.sdp))
                    Column(Modifier.weight(1f)) {
                        Text("Watch the lesson on YouTube", style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                        lesson.channelName?.let {
                            Text(
                                "by $it",
                                style = MaterialTheme.typography.labelMedium.copy(color = colors.neonCyan),
                                modifier = Modifier.clickable(enabled = lesson.channelUrl != null) {
                                    lesson.channelUrl?.let(openYouTube)
                                },
                            )
                        }
                    }
                }
            }
            item { ExplainLanguageToggle(arabic, onExplainLanguage) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.sdp),
                    horizontalArrangement = Arrangement.spacedBy(6.sdp),
                ) {
                    LESSON_TABS.forEachIndexed { i, name ->
                        val selected = i == tab
                        Text(
                            text = name,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (selected) colors.backgroundDark else colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) colors.gold else colors.surfaceVariant.copy(alpha = 0.7f))
                                .clickable { tab = i }
                                .padding(vertical = 8.sdp),
                        )
                    }
                }
            }
            val content = lesson.content
            when (tab) {
                0 -> items(content.vocabulary, key = { it.word }) { word -> VocabCard(word) }
                1 -> items(content.fineLines) { line ->
                    InfoCard {
                        Text(line.title, style = MaterialTheme.typography.titleMedium.copy(color = colors.magenta, fontWeight = FontWeight.Bold))
                        Explain(line.explanationAr, line.explanationEn, colors.textPrimary)
                        line.examples.forEach { ExampleText(it.sentence, label = it.word) }
                    }
                }
                2 -> items(content.expressions) { expression ->
                    InfoCard {
                        Text(expression.phrase, style = MaterialTheme.typography.titleMedium.copy(color = colors.neonCyan, fontWeight = FontWeight.Bold))
                        Explain(expression.meaningAr, expression.meaningEn, colors.textPrimary)
                        if (expression.example.isNotBlank()) ExampleText(expression.example)
                    }
                }
                else -> items(content.grammar) { point ->
                    InfoCard {
                        Text(point.title, style = MaterialTheme.typography.titleMedium.copy(color = colors.gold, fontWeight = FontWeight.Bold))
                        Explain(point.explanationAr, point.explanationEn, colors.textPrimary)
                        point.examples.forEach { ExampleText(it) }
                    }
                }
            }
        }
        Button(
            onClick = onStartExam,
            enabled = lesson.questions.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.gold, contentColor = colors.backgroundDark),
            shape = RoundedCornerShape(14.sdp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.sdp, vertical = 8.sdp).height(48.sdp),
        ) {
            Text(
                text = result?.let { "✓ Completed (${it.score}/${it.total}) · Retake exam" } ?: "Take the exam (${lesson.questions.size} questions)",
                fontWeight = FontWeight.Bold,
            )
        }
    }
    }
}

/** Collapsed to the word by default (try to recall it), tap to see the meaning and example. */
@Composable
private fun VocabCard(word: LessonWord) {
    val colors = LocalSpeakMindColors.current
    var open by rememberSaveable(word.word) { mutableStateOf(false) }
    InfoCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(word.word, style = MaterialTheme.typography.titleMedium.copy(color = colors.neonCyan, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            if (word.pos.isNotBlank()) Text(word.pos, style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted))
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (open) "Hide meaning" else "Show meaning",
                tint = colors.textMuted,
                modifier = Modifier.padding(start = 6.sdp).size(20.sdp),
            )
        }
        AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                if (LocalExplainArabic.current) {
                    ArabicText(word.meaningAr, colors.textPrimary)
                    if (word.meaningEn.isNotBlank()) Text(word.meaningEn, style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary))
                } else {
                    Text(word.meaningEn.ifBlank { word.meaningAr }, style = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary))
                }
                if (word.noteAr.isNotBlank() || word.noteEn.isNotBlank()) Explain(word.noteAr, word.noteEn, colors.gold, prefix = "💡 ")
                if (word.example.isNotBlank()) ExampleText(word.example)
            }
        }
    }
}

@Composable
private fun InfoCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(1.sdp, colors.textMuted.copy(alpha = 0.15f), RoundedCornerShape(14.sdp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.sdp),
        verticalArrangement = Arrangement.spacedBy(6.sdp),
    ) { content() }
}

/** Explanations in Arabic (default for Arabic phones) or English, chosen on the lesson page. */
private val LocalExplainArabic = staticCompositionLocalOf { true }

@Composable
private fun rememberExplainArabic(): Boolean {
    val chosen by ExplainLanguage.arabic.collectAsStateWithLifecycle()
    return chosen ?: (Locale.current.language == "ar")
}

/** The Arabic or English version; falls back to Arabic for lessons without English yet. */
@Composable
private fun Explain(ar: String, en: String, color: Color, prefix: String = "") {
    if (LocalExplainArabic.current || en.isBlank()) {
        if (ar.isNotBlank()) ArabicText(prefix + ar, color)
    } else {
        Text(prefix + en, style = MaterialTheme.typography.bodyMedium.copy(color = color), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ExplainLanguageToggle(arabic: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Explanations",
            style = MaterialTheme.typography.labelLarge.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(colors.surfaceVariant.copy(alpha = 0.7f)).padding(3.sdp),
        ) {
            listOf(true to "العربية", false to "English").forEach { (isArabic, label) ->
                val selected = arabic == isArabic
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = if (selected) Color.White else colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.neonCyan else Color.Transparent)
                        .clickable { onSelect(isArabic) }
                        .padding(horizontal = 14.sdp, vertical = 6.sdp),
                )
            }
        }
    }
}

/** Arabic explanations: a right-to-left paragraph, so English words and punctuation inside sit in the right place. */
@Composable
private fun ArabicText(text: String, color: Color) {
    if (text.isBlank()) return
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(color = color, textDirection = TextDirection.Rtl, textAlign = TextAlign.Start),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExampleText(sentence: String, label: String? = null) {
    val colors = LocalSpeakMindColors.current
    Text(
        text = buildAnnotatedString {
            if (label != null) {
                withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal)) { append(label) }
                append("  ")
                append(sentence)
            } else {
                append("“$sentence”")
            }
        },
        style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontStyle = FontStyle.Italic),
    )
}

// ---------------- Exam ----------------

fun NavGraphBuilder.lessonExamScreen() {
    animatedComposable<LessonExamDestination> { entry ->
        val dest = entry.toRoute<LessonExamDestination>()
        val viewModel = koinViewModel<ExamViewModel>(key = "exam-${dest.lessonId}") { parametersOf(dest.lessonId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val questions = state.lesson?.questions.orEmpty()
        val explainArabic = rememberExplainArabic()
        CompositionLocalProvider(LocalExplainArabic provides explainArabic) {
        ScreenScaffold("Exam", viewModel::onBack) { modifier ->
            when {
                state.lesson == null -> Loading(modifier)
                questions.isEmpty() -> Message("This lesson has no exam yet.", modifier)
                state.finished -> ExamResult(state.score, questions.size, viewModel::onRetry, viewModel::onBack, modifier)
                else -> ExamQuestionView(state, viewModel::onPick, viewModel::onNext, modifier)
            }
        }
        }
    }
}

@Composable
private fun ExamQuestionView(state: ExamUiState, onPick: (Int) -> Unit, onNext: () -> Unit, modifier: Modifier) {
    val colors = LocalSpeakMindColors.current
    val questions = state.lesson?.questions.orEmpty()
    val question = questions[state.index]
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 8.sdp),
        verticalArrangement = Arrangement.spacedBy(12.sdp),
    ) {
        item {
            Text("Question ${state.index + 1} of ${questions.size}", style = MaterialTheme.typography.labelLarge.copy(color = colors.textMuted))
            Spacer(Modifier.height(6.sdp))
            LinearProgressIndicator(
                progress = { (state.index + 1f) / questions.size },
                color = colors.gold,
                trackColor = colors.gold.copy(alpha = 0.15f),
                gapSize = 0.sdp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(4.sdp).clip(RoundedCornerShape(2.sdp)),
            )
            Spacer(Modifier.height(16.sdp))
            Text(question.question, style = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
        }
        items(question.choices.size) { i ->
            val answered = state.picked != null
            val right = answered && i == question.answer
            val wrong = answered && i == state.picked && !right
            val accent = when {
                right -> colors.success
                wrong -> colors.error
                else -> null
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.sdp))
                    .background(accent?.copy(alpha = 0.18f) ?: colors.surfaceVariant.copy(alpha = 0.7f))
                    .border(if (accent != null) 2.sdp else 1.sdp, accent ?: colors.textMuted.copy(alpha = 0.25f), RoundedCornerShape(14.sdp))
                    .clickable(enabled = !answered) { onPick(i) }
                    .padding(16.sdp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    question.choices[i],
                    style = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary, fontWeight = if (accent != null) FontWeight.Bold else FontWeight.Normal),
                    modifier = Modifier.weight(1f),
                )
                if (accent != null) {
                    Text(if (right) "✓" else "✗", style = MaterialTheme.typography.titleMedium.copy(color = accent, fontWeight = FontWeight.Bold))
                }
            }
        }
        if (state.picked != null) {
            item {
                val correct = state.picked == question.answer
                val tone = if (correct) colors.success else colors.error
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.sdp)).background(tone.copy(alpha = 0.1f)).padding(14.sdp),
                    verticalArrangement = Arrangement.spacedBy(6.sdp),
                ) {
                    Text(
                        if (correct) "✓ Correct!" else "✗ Not quite",
                        style = MaterialTheme.typography.titleSmall.copy(color = tone, fontWeight = FontWeight.Bold),
                    )
                    Explain(question.explanationAr, question.explanationEn, colors.textPrimary)
                }
                Spacer(Modifier.height(8.sdp))
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.gold, contentColor = colors.backgroundDark),
                    shape = RoundedCornerShape(14.sdp),
                    modifier = Modifier.fillMaxWidth().height(48.sdp),
                ) {
                    Text(if (state.index + 1 < questions.size) "Next" else "Finish", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ExamResult(score: Int, total: Int, onRetry: () -> Unit, onDone: () -> Unit, modifier: Modifier) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier.fillMaxSize().padding(32.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(72.sdp))
        Spacer(Modifier.height(16.sdp))
        Text("Lesson completed!", style = MaterialTheme.typography.headlineSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.sdp))
        Text("Your score: $score / $total", style = MaterialTheme.typography.titleMedium.copy(color = colors.gold, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(28.sdp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = colors.gold, contentColor = colors.backgroundDark),
            shape = RoundedCornerShape(14.sdp),
            modifier = Modifier.fillMaxWidth().height(48.sdp),
        ) { Text("Back to the lesson", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.sdp))
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(14.sdp), modifier = Modifier.fillMaxWidth().height(48.sdp)) {
            Text("Try again", color = colors.textPrimary)
        }
    }
}
