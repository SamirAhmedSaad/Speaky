package com.speakmind.app.feature.course.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.speakmind.app.feature.course.data.Course
import com.speakmind.app.feature.course.data.Lesson
import com.speakmind.app.feature.course.data.LessonResult
import com.speakmind.app.feature.course.data.LessonWord
import com.speakmind.app.navigation.CourseBookDestination
import com.speakmind.app.navigation.CourseDestination
import com.speakmind.app.navigation.CoursesDestination
import com.speakmind.app.navigation.LessonDestination
import com.speakmind.app.navigation.LessonExamDestination
import com.speakmind.app.ui.components.BannerAdView
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyCard
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPill
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.SpeakyProgress
import com.speakmind.app.ui.components.SpeakySectionLabel
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

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

/** The design's green for "done" (same in light and dark). */
private val DoneGreen = Color(0xFF19B88A)

// ---------------- Home section ----------------

/** "Courses" on Home: one card that opens the list of courses. Hidden until a course is published. */
@Composable
fun CoursesHomeSection(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<CoursesHomeViewModel>()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    OnResume(viewModel::refresh)
    if (cards.isEmpty()) return
    val colors = LocalSpeakMindColors.current
    val done = cards.sumOf { it.done }
    val total = cards.sumOf { it.total }
    SpeakyCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        radius = 22.dp,
        padding = PaddingValues(12.dp),
        onClick = viewModel::onAllCoursesClicked,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primarySoft),
                contentAlignment = Alignment.Center,
            ) { Icon(SpeakyIcons.Video, null, tint = colors.neonCyan, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Video courses", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${cards.size} course${if (cards.size == 1) "" else "s"}" +
                        if (done > 0) " · $done lesson${if (done == 1) "" else "s"} completed" else " · beginner to advanced",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                )
                if (done > 0 && total > 0) SpeakyProgress(done.toFloat() / total, colors.neonCyan, height = 5.dp)
            }
            Icon(SpeakyIcons.Chevron, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------- Shared pieces ----------------

/** Soft tint and its ink for a course, in the same order as the Learn tab's course cards. */
@Composable
private fun courseTone(course: Course): Pair<Color, Color> {
    val colors = LocalSpeakMindColors.current
    val tones = listOf(
        colors.primarySoft to colors.onPrimarySoft,
        colors.mintSoft to colors.onMintSoft,
        colors.coralSoft to colors.onCoralSoft,
        colors.goldSoft to colors.onGoldSoft,
    )
    return tones[course.sort.coerceAtLeast(0) % tones.size]
}

/** "BBC · Storytellers" -> "BBC", "English Together · Daily" -> "ET". */
private fun courseInitials(course: Course): String {
    val name = course.title.substringBefore('·').trim()
    val words = name.split(' ').filter { it.isNotBlank() }
    val first = words.firstOrNull() ?: return "?"
    if (words.size == 1 || (first.length <= 4 && first.all { it.isUpperCase() })) return first.take(4).uppercase()
    return words.take(2).joinToString("") { it.first().uppercase() }
}

@Composable
private fun CourseLogo(course: Course, size: Dp = 56.dp) {
    val (bg, fg) = courseTone(course)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(courseInitials(course), color = fg, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        // Drawn over the initials, which stay as the fallback while loading or offline.
        if (course.imageUrl != null) AsyncImage(
            model = course.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** Course level: mint for beginners, gold for the rest. */
@Composable
private fun LevelPill(level: String, fontSize: Int = 11) {
    val colors = LocalSpeakMindColors.current
    val beginner = level.startsWith("Beginner", ignoreCase = true)
    SpeakyPill(
        level,
        background = if (beginner) colors.mintSoft else colors.goldSoft,
        color = if (beginner) colors.onMintSoft else colors.onGoldSoft,
        fontSize = fontSize.sp,
        padding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Violet ink for titles on white; the lighter tint in dark mode, where the deep violet would disappear. */
@Composable
private fun accentInk(): Color {
    val colors = LocalSpeakMindColors.current
    return if (colors.surface.luminance() < 0.5f) colors.onPrimarySoft else colors.neonCyanDark
}

/** Page background, the shared banner at the bottom, and room for the clock at the top. */
@Composable
private fun CoursePage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LocalSpeakMindColors.current.backgroundDark).navigationBarsPadding(),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        BannerAdView()
    }
}

/** Back button with a title block (design header: 20pt from the top, gap 10). */
@Composable
private fun CourseHeader(onBack: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpeakyBackButton(onBack)
        content()
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalSpeakMindColors.current.neonCyan)
    }
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = LocalSpeakMindColors.current.textMuted, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

// ---------------- All courses ----------------

fun NavGraphBuilder.coursesScreen() {
    animatedComposable<CoursesDestination> {
        val viewModel = koinViewModel<CoursesHomeViewModel>()
        val cards by viewModel.cards.collectAsStateWithLifecycle()
        OnResume(viewModel::refresh)
        val colors = LocalSpeakMindColors.current
        // Level filter, built from the levels the published courses actually have.
        var filter by rememberSaveable { mutableStateOf<String?>(null) }
        val groups = remember(cards) {
            cards.mapNotNull { it.course.level?.levelGroup() }.distinct()
        }
        CoursePage {
            CourseHeader(viewModel::onBack) {
                Text("Courses", Modifier.weight(1f), style = displayStyle(26.sp), color = colors.textPrimary)
                if (cards.isNotEmpty()) {
                    Text(
                        "${cards.size} course${if (cards.size == 1) "" else "s"}",
                        color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (cards.isEmpty()) {
                Loading(Modifier.weight(1f))
                return@CoursePage
            }
            val shown = cards.filter { filter == null || it.course.level?.levelGroup() == filter }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (groups.size > 1) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf<String?>(null) + groups).forEach { group ->
                            val on = filter == group
                            SpeakyPill(
                                group ?: "All",
                                background = if (on) colors.textPrimary else colors.surface,
                                color = if (on) colors.surface else colors.textPrimary,
                                fontSize = 13.sp,
                                padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                onClick = { filter = group },
                            )
                        }
                    }
                }
                items(shown, key = { it.course.id }) { card ->
                    CourseRow(
                        course = card.course,
                        done = card.done,
                        total = card.total,
                        onClick = { viewModel.onCourseClicked(card.course) },
                    )
                }
            }
        }
    }
}

/** "Beginner → Intermediate" -> "Beginner"; "Upper intermediate" -> "Upper". */
private fun String.levelGroup(): String =
    trim().split(' ', '→', '-', '/').firstOrNull { it.isNotBlank() }?.trimEnd('.')?.replaceFirstChar { it.uppercase() } ?: this

@Composable
private fun CourseRow(course: Course, done: Int, total: Int, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CourseLogo(course)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                course.title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val by = course.channelName ?: course.subtitle?.takeIf { total == 0 }
            if (!course.level.isNullOrBlank() || !by.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    course.level?.takeIf { it.isNotBlank() }?.let { LevelPill(it) }
                    if (!by.isNullOrBlank()) {
                        Text(by, color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            SpeakyProgress(if (total > 0) done.toFloat() / total else 0f, colors.neonCyan, height = 5.dp)
        }
        if (total > 0) {
            if (done == total) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(DoneGreen), contentAlignment = Alignment.Center) {
                    Icon(SpeakyIcons.Check, "Completed", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            } else {
                Text("$done/$total", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
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
        // Nothing to choose between with a single part: go straight to its lessons.
        LaunchedEffect(state.outline) {
            val outline = state.outline ?: return@LaunchedEffect
            outline.books.singleOrNull()?.let { viewModel.onSingleBook(it.id, outline.course.title) }
        }
        CoursePage {
            val outline = state.outline
            if (outline == null) {
                CourseHeader(viewModel::onBack) {
                    Text(dest.title, Modifier.weight(1f), style = displayStyle(22.sp), color = colors.textPrimary, maxLines = 2)
                }
                if (state.isLoading) Loading(Modifier.weight(1f))
                else Message("Couldn't load the course. Check your connection.", Modifier.weight(1f))
                return@CoursePage
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { CourseHero(outline.course, outline.lessons.size, viewModel::onBack) }
                item {
                    SpeakySectionLabel("Books", Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp))
                }
                items(outline.books, key = { it.id }) { book ->
                    val lessons = outline.lessonsOf(book.id)
                    val done = lessons.count { it.id in state.results }
                    BookRow(
                        number = book.number.takeIf { it > 0 } ?: (outline.books.indexOf(book) + 1),
                        title = book.title,
                        meta = when {
                            lessons.isEmpty() -> "Locked · coming soon"
                            done == lessons.size -> "All ${lessons.size} lessons done"
                            else -> "$done of ${lessons.size} lessons" + if (done > 0) " · in progress" else ""
                        },
                        done = done,
                        total = lessons.size,
                        onClick = { viewModel.onBookClicked(book.id, book.title) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

/** Coloured top block with the course name, author and level. */
@Composable
private fun CourseHero(course: Course, lessonCount: Int, onBack: () -> Unit) {
    val (bg, fg) = courseTone(course)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpeakyIconButton(SpeakyIcons.Back, "Back", onBack, background = Color.White.copy(alpha = 0.6f), tint = fg)
        course.channelName?.takeIf { it.isNotBlank() }?.let {
            Text("BY ${it.uppercase()}", color = fg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
        }
        Text(course.title, style = displayStyle(30.sp, lineHeight = 32.sp), color = fg)
        course.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = fg, fontSize = 14.sp, lineHeight = 21.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pill = Color.White.copy(alpha = 0.6f)
            course.level?.takeIf { it.isNotBlank() }?.let {
                SpeakyPill(it, pill, fg, fontSize = 12.sp, padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp))
            }
            if (lessonCount > 0) {
                SpeakyPill(
                    "$lessonCount lesson${if (lessonCount == 1) "" else "s"}", pill, fg,
                    fontSize = 12.sp, padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun BookRow(
    number: Int,
    title: String,
    meta: String,
    done: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpeakMindColors.current
    val locked = total == 0
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.55f else 1f)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val started = done > 0
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(if (started) colors.neonCyan else colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = if (started) colors.onPrimary else accentInk(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(meta, color = colors.textMuted, fontSize = 12.sp)
            if (!locked) SpeakyProgress(done.toFloat() / total, colors.neonCyan, height = 5.dp)
        }
        Icon(
            if (locked) SpeakyIcons.Lock else SpeakyIcons.Chevron,
            if (locked) "Locked" else null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ---------------- Book: list of lessons ----------------

fun NavGraphBuilder.courseBookScreen() {
    animatedComposable<CourseBookDestination> { entry ->
        val dest = entry.toRoute<CourseBookDestination>()
        val viewModel = koinViewModel<CourseOutlineViewModel>(key = "book-${dest.bookId}") { parametersOf(dest.courseId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        OnResume(viewModel::refresh)
        val colors = LocalSpeakMindColors.current
        val bookTitle = state.outline?.let { outline ->
            if (outline.books.size == 1) outline.course.title else outline.books.find { it.id == dest.bookId }?.title
        } ?: dest.title
        val lessons = state.outline?.lessonsOf(dest.bookId)
        CoursePage {
            CourseHeader(viewModel::onBack) {
                Column(Modifier.weight(1f)) {
                    Text(bookTitle, style = displayStyle(22.sp, lineHeight = 26.sp), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!lessons.isNullOrEmpty()) {
                        Text(
                            "${lessons.count { it.id in state.results }} of ${lessons.size} lessons done",
                            color = colors.textMuted, fontSize = 12.sp,
                        )
                    }
                }
            }
            when {
                state.isLoading && lessons == null -> Loading(Modifier.weight(1f))
                lessons.isNullOrEmpty() -> Message("Lessons for this book are coming soon.", Modifier.weight(1f))
                else -> {
                    // The first lesson without an exam result is the one to do next.
                    val next = lessons.firstOrNull { it.id !in state.results }?.id
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(lessons, key = { it.id }) { lesson ->
                            LessonRow(
                                title = "Lesson ${lesson.number} · ${lesson.title}",
                                result = state.results[lesson.id],
                                next = lesson.id == next,
                                onClick = { viewModel.onLessonClicked(lesson.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(title: String, result: LessonResult?, next: Boolean, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .then(if (next) Modifier.border(2.dp, colors.coral, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (bg, tint) = when {
            result != null -> DoneGreen to Color.White
            next -> colors.coral to Color.White
            else -> colors.primarySoft to colors.neonCyan
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            if (result != null) Icon(SpeakyIcons.Check, "Completed", tint = tint, modifier = Modifier.size(20.dp))
            else Icon(SpeakyIcons.Play, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                result != null -> Text("Exam ${result.score}/${result.total}", color = colors.success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                next -> Text("Up next", color = colors.textMuted, fontSize = 12.sp)
                else -> Text("Not started", color = colors.textMuted, fontSize = 12.sp)
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
        val colors = LocalSpeakMindColors.current
        CoursePage {
            CourseHeader(viewModel::onBack) {
                Column(Modifier.weight(1f)) {
                    if (lesson != null) Text("LESSON ${lesson.number}", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        lesson?.title ?: "Lesson",
                        style = displayStyle(20.sp, lineHeight = 24.sp),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                state.isLoading -> Loading(Modifier.weight(1f))
                lesson == null -> Message("Couldn't load the lesson. Check your connection.", Modifier.weight(1f))
                else -> LessonBody(lesson, state.result, viewModel::onStartExam, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LessonBody(
    lesson: Lesson,
    result: LessonResult?,
    onStartExam: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalSpeakMindColors.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { VideoCard(lesson) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LESSON_TABS.forEachIndexed { i, name ->
                        val selected = i == tab
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colors.textPrimary else colors.surface)
                                .clickable { tab = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name,
                                color = if (selected) colors.surface else colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            val content = lesson.content
            when (tab) {
                0 -> items(content.vocabulary, key = { it.word }) { word -> VocabCard(word) }
                1 -> items(content.fineLines) { line ->
                    InfoCard {
                        CardTitle(line.title)
                        Bilingual(line.explanationEn, line.explanationAr)
                        line.examples.forEach { ExampleText(it.sentence, label = it.word) }
                    }
                }
                2 -> items(content.expressions) { expression ->
                    InfoCard {
                        CardTitle(expression.phrase)
                        Bilingual(expression.meaningEn, expression.meaningAr)
                        if (expression.example.isNotBlank()) ExampleText(expression.example)
                    }
                }
                else -> items(content.grammar) { point ->
                    InfoCard {
                        CardTitle(point.title)
                        Bilingual(point.explanationEn, point.explanationAr)
                        point.examples.forEach { ExampleText(it) }
                    }
                }
            }
        }
        SpeakyPrimaryButton(
            text = result?.let { "Completed ${it.score}/${it.total} · Retake exam" }
                ?: "Take the exam · ${lesson.questions.size} question${if (lesson.questions.size == 1) "" else "s"}",
            onClick = onStartExam,
            enabled = lesson.questions.isNotEmpty(),
            background = colors.coral,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        )
    }
}

/** Video: opens in the YouTube app, so the author gets the view. */
@Composable
private fun VideoCard(lesson: Lesson) {
    val colors = LocalSpeakMindColors.current
    val openYouTube = rememberYouTubeOpener()
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF17142B))
            .clickable { openYouTube(lesson.videoUrl) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = (-30).dp, y = 40.dp).size(160.dp)
                .clip(CircleShape).background(colors.neonCyan.copy(alpha = 0.6f)),
        )
        AsyncImage(
            model = "https://img.youtube.com/vi/${lesson.videoId}/hqdefault.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(0.45f),
        )
        Box(
            Modifier.size(width = 68.dp, height = 48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFF0033)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SpeakyIcons.Play, "Watch the lesson on YouTube", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Watch on YouTube", Modifier.weight(1f), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            lesson.channelName?.let {
                Text(
                    "by $it",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(enabled = lesson.channelUrl != null) { lesson.channelUrl?.let(openYouTube) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word.word, Modifier.weight(1f), color = accentInk(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            if (word.pos.isNotBlank()) Text(word.pos, color = colors.textMuted, fontSize = 12.sp)
        }
        AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Bilingual(word.meaningEn, word.meaningAr)
                if (word.noteAr.isNotBlank() || word.noteEn.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.goldSoft).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Bilingual(word.noteEn, word.noteAr, color = colors.onGoldSoft, secondary = colors.onGoldSoft, fontSize = 14)
                    }
                }
                if (word.example.isNotBlank()) ExampleText(word.example)
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, color = accentInk(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun InfoCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    SpeakyCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 18.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/**
 * English and Arabic together (whichever exist): English first, then the Arabic as a right-to-left
 * paragraph so English words and punctuation inside it sit in the right place.
 */
@Composable
private fun Bilingual(
    en: String,
    ar: String,
    color: Color = LocalSpeakMindColors.current.textPrimary,
    secondary: Color = LocalSpeakMindColors.current.textSecondary,
    fontSize: Int = 15,
) {
    if (en.isNotBlank()) {
        Text(en, color = color, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp, modifier = Modifier.fillMaxWidth())
    }
    if (ar.isNotBlank()) {
        Text(
            ar,
            color = if (en.isNotBlank()) secondary else color,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.6f).sp,
            style = TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Start),
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
        color = colors.textMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontStyle = FontStyle.Italic,
    )
}

// ---------------- Exam ----------------

fun NavGraphBuilder.lessonExamScreen() {
    animatedComposable<LessonExamDestination> { entry ->
        val dest = entry.toRoute<LessonExamDestination>()
        val viewModel = koinViewModel<ExamViewModel>(key = "exam-${dest.lessonId}") { parametersOf(dest.lessonId) }
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val questions = state.lesson?.questions.orEmpty()
        CoursePage {
            when {
                state.lesson == null -> Loading(Modifier.weight(1f))
                questions.isEmpty() -> {
                    CourseHeader(viewModel::onBack) {}
                    Message("This lesson has no exam yet.", Modifier.weight(1f))
                }
                state.finished -> ExamResult(state, viewModel::onRetry, viewModel::onBack, Modifier.weight(1f))
                else -> ExamQuestionView(state, viewModel::onPick, viewModel::onNext, viewModel::onBack, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExamQuestionView(state: ExamUiState, onPick: (Int) -> Unit, onNext: () -> Unit, onLeave: () -> Unit, modifier: Modifier) {
    val colors = LocalSpeakMindColors.current
    val questions = state.lesson?.questions.orEmpty()
    val question = questions[state.index]
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeakyIconButton(SpeakyIcons.Close, "Leave exam", onLeave)
            SpeakyProgress((state.index + 1f) / questions.size, colors.coral, Modifier.weight(1f), height = 10.dp)
            Text("${state.index + 1}/${questions.size}", color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("CHOOSE THE BEST ANSWER", color = colors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(question.question, style = displayStyle(26.sp, lineHeight = 31.sp), color = colors.textPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.choices.forEachIndexed { i, choice ->
                    val answered = state.picked != null
                    val right = answered && i == question.answer
                    val wrong = answered && i == state.picked && !right
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .clip(shape)
                            .background(
                                when {
                                    right -> colors.mintSoft
                                    wrong -> colors.error.copy(alpha = 0.14f)
                                    else -> colors.surface
                                },
                            )
                            .border(2.dp, if (right) DoneGreen else if (wrong) colors.error else colors.surface, shape)
                            .clickable(enabled = !answered) { onPick(i) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(choice, Modifier.weight(1f), color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (right) Icon(SpeakyIcons.Check, "Correct", tint = colors.success, modifier = Modifier.size(22.dp))
                        if (wrong) Icon(SpeakyIcons.Close, "Wrong", tint = colors.error, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (state.picked != null) {
                val correct = state.picked == question.answer
                val ink = if (correct) colors.onMintSoft else colors.error
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (correct) colors.mintSoft else colors.error.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(if (correct) "Correct!" else "Not quite", color = ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Bilingual(question.explanationEn, question.explanationAr, color = colors.textPrimary, fontSize = 14)
                }
            }
        }
        if (state.picked != null) {
            SpeakyPrimaryButton(
                if (state.index + 1 < questions.size) "Next" else "Finish",
                onClick = onNext,
                background = colors.textPrimary,
                color = colors.surface,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ExamResult(state: ExamUiState, onRetry: () -> Unit, onDone: () -> Unit, modifier: Modifier) {
    val colors = LocalSpeakMindColors.current
    val questions = state.lesson?.questions.orEmpty()
    val total = questions.size
    val score = state.score
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScoreRing(score, total)
            Text("Lesson completed!", style = displayStyle(30.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(
                "Your best score for this lesson is kept.",
                color = colors.textSecondary, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("$score", "Correct", colors.success, Modifier.weight(1f))
                StatTile("${total - score}", "To review", colors.error, Modifier.weight(1f))
            }
            val missed = state.missed.mapNotNull { questions.getOrNull(it) }
            if (missed.isNotEmpty()) {
                SpeakyCard(Modifier.fillMaxWidth(), radius = 20.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                    SpeakySectionLabel("Review these")
                    missed.forEach { q ->
                        Text(
                            buildAnnotatedString {
                                append(q.question)
                                append(" · ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.success)) {
                                    append(q.choices.getOrElse(q.answer) { "" })
                                }
                            },
                            color = colors.textPrimary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeakyPrimaryButton("Back to the lesson", onClick = onDone)
            SpeakyPrimaryButton(
                "Try again", onClick = onRetry, background = colors.surface, color = colors.textPrimary,
                height = 50.dp, radius = 16.dp,
            )
        }
    }
}

@Composable
private fun ScoreRing(score: Int, total: Int) {
    val colors = LocalSpeakMindColors.current
    val fraction = if (total > 0) score.toFloat() / total else 0f
    val track = colors.track
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(190.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2 + 7.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(DoneGreen, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score/$total", style = displayStyle(44.sp), color = colors.textPrimary)
            Text("${(fraction * 100).roundToInt()}%", color = colors.success, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = colors.textMuted, fontSize = 12.sp)
    }
}
