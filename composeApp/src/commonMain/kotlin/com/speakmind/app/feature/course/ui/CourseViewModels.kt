package com.speakmind.app.feature.course.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speakmind.app.config.ServerSettings
import com.speakmind.app.feature.course.data.Course
import com.speakmind.app.feature.course.data.CourseOutline
import com.speakmind.app.feature.course.data.CourseRepository
import com.speakmind.app.feature.course.data.Lesson
import com.speakmind.app.feature.course.data.LessonResult
import com.speakmind.app.navigation.CourseBookDestination
import com.speakmind.app.navigation.CourseDestination
import com.speakmind.app.navigation.CoursesDestination
import com.speakmind.app.navigation.LessonDestination
import com.speakmind.app.navigation.LessonExamDestination
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.premium.Paywall
import com.speakmind.app.premium.Premium
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------- Home: list of courses ----------------

data class CourseCard(val course: Course, val done: Int, val total: Int)

class CoursesHomeViewModel(
    private val repository: CourseRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {
    private val _cards = MutableStateFlow<List<CourseCard>>(emptyList())
    val cards: StateFlow<List<CourseCard>> = _cards.asStateFlow()

    // Again when the server's courses_version arrives: it decides whether the saved copy is still current.
    init { viewModelScope.launch { ServerSettings.coursesVersionFlow.collect { refresh() } } }

    fun refresh() {
        // Server time for the free-lesson limit, ready before a lesson is tapped.
        viewModelScope.launch { repository.syncServerClock() }
        viewModelScope.launch {
            // Saved copy first so Home doesn't jump when the section appears, then fresh data.
            run {
                repository.savedCourses()?.let { courses ->
                    val results = repository.results()
                    _cards.value = courses.map { course ->
                        val lessons = repository.savedOutline(course.id)?.lessons.orEmpty()
                        CourseCard(course, lessons.count { it.id in results }, lessons.size)
                    }
                }
            }
            val courses = repository.courses() ?: return@launch
            val results = repository.results()
            _cards.value = courses.map { course ->
                val lessons = repository.outline(course.id)?.lessons.orEmpty()
                CourseCard(course, lessons.count { it.id in results }, lessons.size)
            }
        }
    }

    /** A course with a single part opens straight on its lessons (outlines are saved by [refresh]). */
    fun onCourseClicked(course: Course) {
        viewModelScope.launch {
            val onlyBook = repository.savedOutline(course.id)?.books?.singleOrNull()
            navigationManager.navigate(
                if (onlyBook != null) CourseBookDestination(course.id, onlyBook.id, course.title)
                else CourseDestination(course.id, course.title)
            )
        }
    }
    fun onAllCoursesClicked() = navigationManager.navigate(CoursesDestination)

    /** "Start lesson 1" / "Continue": straight into the first lesson without a result, in book order. */
    fun onContinueCourse(course: Course) {
        viewModelScope.launch {
            val outline = repository.savedOutline(course.id) ?: repository.outline(course.id)
            val results = repository.results()
            val next = outline?.books?.sortedBy { it.number }
                ?.flatMap { outline.lessonsOf(it.id) }
                ?.let { lessons -> lessons.firstOrNull { it.id !in results } ?: lessons.firstOrNull() }
            if (next == null) onCourseClicked(course) else openLesson(repository, navigationManager, next.id)
        }
    }
    fun onBack() = navigationManager.back()
}

// ---------------- Course (books) and book (lessons) ----------------

data class OutlineUiState(
    val outline: CourseOutline? = null,
    val results: Map<String, LessonResult> = emptyMap(),
    val isLoading: Boolean = true,
)

class CourseOutlineViewModel(
    private val courseId: String,
    private val repository: CourseRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OutlineUiState())
    val uiState: StateFlow<OutlineUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            // Local first (instant ✓ after an exam, works offline), then fresh data from the server.
            val shown = _uiState.value.outline ?: repository.savedOutline(courseId)
            if (shown != null) _uiState.value = OutlineUiState(shown, repository.results(), isLoading = false)
            val outline = repository.outline(courseId) ?: _uiState.value.outline
            _uiState.value = OutlineUiState(outline, repository.results(), isLoading = false)
        }
    }

    fun onBookClicked(bookId: String, title: String) =
        navigationManager.navigate(CourseBookDestination(courseId, bookId, title))

    /** The course turned out to have one part: show its lessons instead, so Back goes to the course list. */
    fun onSingleBook(bookId: String, courseTitle: String) =
        navigationManager.navigate(CourseBookDestination(courseId, bookId, courseTitle)) {
            popUpTo<CourseDestination> { inclusive = true }
        }

    /** Premium: any lesson. Free: [CourseRepository.FREE_LESSONS_PER_DAY] a day, then the subscribe screen with a countdown. */
    fun onLessonClicked(lessonId: String) {
        viewModelScope.launch { openLesson(repository, navigationManager, lessonId) }
    }

    fun onBack() = navigationManager.back()
}

/** Premium: any lesson. Free: [CourseRepository.FREE_LESSONS_PER_DAY] a day, then the subscribe screen with a countdown. */
internal suspend fun openLesson(repository: CourseRepository, navigationManager: NavigationManager, lessonId: String) {
    val open = { navigationManager.navigate(LessonDestination(lessonId)) }
    if (Premium.active.value) return open()
    val waitMs = repository.useFreeLesson(lessonId) ?: return open()
    Premium.showPaywall(
        Paywall(
            title = "Your free lessons are used up",
            message = "You get ${CourseRepository.FREE_LESSONS_PER_DAY} free lessons every 24 hours. " +
                "Subscribe for unlimited lessons, or come back when the timer ends.",
            waitMs = waitMs,
            onUnlocked = open,
        ),
    )
}

// ---------------- Explanation language (Arabic / English) ----------------

/** Shared by the lesson and exam screens. Null: not chosen yet, follow the phone's language. */
object ExplainLanguage {
    val arabic = MutableStateFlow<Boolean?>(null)
    private var loaded = false

    suspend fun load(repository: CourseRepository) {
        if (loaded) return
        loaded = true
        repository.explainInArabic()?.let { arabic.value = it }
    }
}

// ---------------- Lesson page ----------------

data class LessonUiState(
    val lesson: Lesson? = null,
    val result: LessonResult? = null,
    val isLoading: Boolean = true,
)

class LessonViewModel(
    private val lessonId: String,
    private val repository: CourseRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { ExplainLanguage.load(repository) }
        // Saved copy first (instant, offline), then the latest version from the server.
        viewModelScope.launch {
            repository.savedLesson(lessonId)?.let { show(it) }
            show(repository.lesson(lessonId) ?: _uiState.value.lesson)
        }
    }

    /** Re-reads the exam result, e.g. after coming back from the exam. */
    fun refresh() {
        viewModelScope.launch { _uiState.update { it.copy(result = repository.results()[lessonId]) } }
    }

    private suspend fun show(lesson: Lesson?) {
        _uiState.value = LessonUiState(lesson, repository.results()[lessonId], isLoading = false)
    }

    fun onStartExam() = navigationManager.navigate(LessonExamDestination(lessonId))

    fun onExplainLanguage(arabic: Boolean) {
        ExplainLanguage.arabic.value = arabic
        viewModelScope.launch { repository.setExplainInArabic(arabic) }
    }

    fun onBack() = navigationManager.back()
}

// ---------------- Exam ----------------

data class ExamUiState(
    val lesson: Lesson? = null,
    val index: Int = 0,
    /** The choice picked for the current question; null until the user answers. */
    val picked: Int? = null,
    val score: Int = 0,
    val finished: Boolean = false,
    /** Questions answered wrong, listed on the result screen. */
    val missed: List<Int> = emptyList(),
)

class ExamViewModel(
    private val lessonId: String,
    private val repository: CourseRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val lesson = repository.savedLesson(lessonId) ?: repository.lesson(lessonId)
            _uiState.update { it.copy(lesson = lesson) }
        }
    }

    fun onPick(choice: Int) {
        val state = _uiState.value
        val question = state.lesson?.questions?.getOrNull(state.index) ?: return
        if (state.picked != null) return
        val right = choice == question.answer
        _uiState.value = state.copy(
            picked = choice,
            score = state.score + if (right) 1 else 0,
            missed = if (right) state.missed else state.missed + state.index,
        )
    }

    fun onNext() {
        val state = _uiState.value
        val total = state.lesson?.questions?.size ?: return
        if (state.index + 1 < total) {
            _uiState.value = state.copy(index = state.index + 1, picked = null)
        } else {
            // Finishing the exam completes the lesson; the best score is kept.
            viewModelScope.launch {
                val previous = repository.results()[lessonId]
                if (previous == null || state.score >= previous.score) repository.saveResult(lessonId, state.score, total)
                _uiState.value = state.copy(finished = true)
            }
        }
    }

    fun onRetry() {
        _uiState.update { ExamUiState(lesson = it.lesson) }
    }

    fun onBack() = navigationManager.back()
}
