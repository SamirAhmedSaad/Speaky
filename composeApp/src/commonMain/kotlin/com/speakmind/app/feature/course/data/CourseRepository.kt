package com.speakmind.app.feature.course.data

import com.speakmind.app.config.ServerSettings
import com.speakmind.app.db.SpeakyDatabase
import io.github.aakira.napier.Napier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.speakmind.app.platform.bootElapsedMillis
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Courses are read from Supabase (published rows only, enforced by RLS) and kept in a local copy
 * so they open offline. Lesson progress lives only on the phone.
 */
class CourseRepository(
    private val client: SupabaseClient,
    private val database: SpeakyDatabase,
    private val json: Json,
) {
    private val queries get() = database.speakMindQueries

    suspend fun courses(): List<Course>? = cached("courses", ListSerializer(Course.serializer())) {
        client.from("courses").select {
            order("sort", Order.ASCENDING)
        }.decodeList<Course>()
    }

    suspend fun outline(courseId: String): CourseOutline? = cached("outline:$courseId", CourseOutline.serializer()) {
        val course = client.from("courses").select {
            filter { eq("id", courseId) }
        }.decodeSingle<Course>()
        val books = client.from("course_books").select {
            filter { eq("course_id", courseId) }
            order("number", Order.ASCENDING)
        }.decodeList<CourseBook>()
        val lessons = if (books.isEmpty()) emptyList() else {
            client.from("course_lessons").select(Columns.list("id", "book_id", "number", "title")) {
                filter { isIn("book_id", books.map { it.id }) }
                order("number", Order.ASCENDING)
            }.decodeList<LessonSummary>()
        }
        CourseOutline(course, books, lessons)
    }

    suspend fun lesson(lessonId: String): Lesson? = cached("lesson:$lessonId", Lesson.serializer()) {
        client.from("course_lessons").select {
            filter { eq("id", lessonId) }
        }.decodeSingle<Lesson>()
    }

    /** The saved copies, for showing something instantly before the network answers. */
    suspend fun savedCourses(): List<Course>? = saved("courses", ListSerializer(Course.serializer()))
    suspend fun savedOutline(courseId: String): CourseOutline? = saved("outline:$courseId", CourseOutline.serializer())
    suspend fun savedLesson(lessonId: String): Lesson? = saved("lesson:$lessonId", Lesson.serializer())

    private suspend fun <T> saved(key: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        queries.selectCourseCache(key).executeAsOneOrNull()?.let {
            runCatching { json.decodeFromString(serializer, it) }.getOrNull()
        }
    }

    /** Explanation language picked on the lesson page; null until the user picks one. */
    suspend fun explainInArabic(): Boolean? = withContext(Dispatchers.IO) {
        queries.selectCourseCache(KEY_EXPLAIN_AR).executeAsOneOrNull()?.toBooleanStrictOrNull()
    }

    suspend fun setExplainInArabic(arabic: Boolean) = withContext(Dispatchers.IO) {
        queries.upsertCourseCache(KEY_EXPLAIN_AR, arabic.toString(), Clock.System.now().toEpochMilliseconds())
    }

    /**
     * Free users get [FREE_LESSONS_PER_DAY] different lessons per 24 hours, counted from the first of them
     * (reopening one doesn't count). Returns null when [lessonId] may open (and counts it), otherwise how
     * long until the free lessons come back, in millis. Time comes from the server, not the phone's clock.
     */
    suspend fun useFreeLesson(lessonId: String): Long? =
        useFreeDaily(KEY_FREE_LESSONS, FREE_LESSONS_PER_DAY, lessonId)

    /** Each idiom game's allowance: [FREE_GAME_LEVELS_PER_DAY] new level(s) per 24 hours, as [useFreeLesson]. */
    suspend fun useFreeGameLevel(game: String, levelId: String): Long? =
        useFreeDaily("$KEY_FREE_GAME_LEVELS:$game", FREE_GAME_LEVELS_PER_DAY, levelId)

    /** True when [game] still has its free level for the current 24 hours. */
    suspend fun hasFreeGameLevel(game: String): Boolean = withContext(Dispatchers.IO) {
        val saved = queries.selectCourseCache("$KEY_FREE_GAME_LEVELS:$game").executeAsOneOrNull()?.split('|', limit = 2)
        val start = saved?.firstOrNull()?.toLongOrNull() ?: return@withContext true
        if (trustedNow() >= start + FREE_LESSONS_WINDOW_MS) return@withContext true
        saved.getOrNull(1).orEmpty().split(',').count { it.isNotEmpty() } < FREE_GAME_LEVELS_PER_DAY
    }

    private suspend fun useFreeDaily(key: String, limit: Int, id: String): Long? = withContext(Dispatchers.IO) {
        val now = trustedNow()
        // Stored as "<start of the 24 hours, server millis>|id,id".
        val saved = queries.selectCourseCache(key).executeAsOneOrNull()?.split('|', limit = 2)
        var start = saved?.firstOrNull()?.toLongOrNull()
        var opened = if (start != null) saved?.getOrNull(1).orEmpty().split(',').filter { it.isNotEmpty() } else emptyList()
        if (start != null && now >= start + FREE_LESSONS_WINDOW_MS) {
            start = null
            opened = emptyList()
        }
        when {
            id in opened -> null
            opened.size < limit -> {
                val value = "${start ?: now}|${(opened + id).joinToString(",")}"
                queries.upsertCourseCache(key, value, now)
                null
            }
            else -> (start!! + FREE_LESSONS_WINDOW_MS - now).coerceAtLeast(0)
        }
    }

    /**
     * Reads the server's clock and remembers it with the boot clock, so [trustedNow] needs no request.
     * Called in the background when the course list loads.
     */
    suspend fun syncServerClock(): Long? = withContext(Dispatchers.IO) {
        val server = try {
            withTimeout(5_000) { client.postgrest.rpc("server_now").decodeAs<Long>() }
        } catch (e: Exception) {
            null
        }
        server?.also { queries.upsertCourseCache(KEY_SERVER_CLOCK, "$it|${bootElapsedMillis()}", it) }
    }

    /**
     * The server's clock: the last server time plus the time since then on the boot clock (which the user
     * can't change). After a phone restart it asks the server again; the phone's clock only if that fails
     * and the server was never reached.
     */
    private suspend fun trustedNow(): Long {
        val elapsed = bootElapsedMillis()
        val saved = queries.selectCourseCache(KEY_SERVER_CLOCK).executeAsOneOrNull()?.split('|')
        val then = saved?.getOrNull(0)?.toLongOrNull()
        val at = saved?.getOrNull(1)?.toLongOrNull()
        // A smaller boot clock means the phone restarted since then.
        if (then != null && at != null && elapsed >= at) return then + (elapsed - at)
        return syncServerClock() ?: Clock.System.now().toEpochMilliseconds()
    }

    /** Finished exams by lesson id. */
    suspend fun results(): Map<String, LessonResult> = withContext(Dispatchers.IO) {
        queries.selectCourseProgress().executeAsList()
            .associate { it.lesson_id to LessonResult(it.score.toInt(), it.total.toInt()) }
    }

    suspend fun saveResult(lessonId: String, score: Int, total: Int) = withContext(Dispatchers.IO) {
        queries.upsertCourseProgress(lessonId, score.toLong(), total.toLong(), Clock.System.now().toEpochMilliseconds())
    }

    /**
     * The saved copy while it's fresh (under a day old and newer than the server's last course
     * update), so opening courses costs no requests. Otherwise fresh data from the server, saved for
     * later; the old copy when offline; null if neither.
     */
    private suspend fun <T> cached(key: String, serializer: KSerializer<T>, fetch: suspend () -> T): T? =
        withContext(Dispatchers.IO) {
            val now = Clock.System.now().toEpochMilliseconds()
            queries.selectCourseCacheEntry(key).executeAsOneOrNull()
                ?.takeIf { now - it.updated_at < MAX_AGE_MS && it.updated_at >= validFrom(now) }
                ?.let { entry -> runCatching { json.decodeFromString(serializer, entry.json) }.getOrNull() }
                ?.let { return@withContext it }
            try {
                fetch().also { fresh -> queries.upsertCourseCache(key, json.encodeToString(serializer, fresh), now) }
            } catch (e: Exception) {
                Napier.w(e) { "Course data offline, using saved copy: $key" }
                saved(key, serializer)
            }
        }

    /**
     * Copies saved before this time are stale. Moves to "now" when the server's courses_version
     * differs from the one this phone last saw (read at app start with the other server switches).
     */
    private fun validFrom(now: Long): Long {
        val server = ServerSettings.coursesVersion
        val seen = queries.selectCourseCache(KEY_COURSES_VERSION).executeAsOneOrNull()?.toLongOrNull()
        if (server != null && server != seen) {
            queries.upsertCourseCache(KEY_COURSES_VERSION, server.toString(), now)
            queries.upsertCourseCache(KEY_VALID_FROM, now.toString(), now)
            // A first install has nothing saved yet, so there's nothing to throw away.
            return if (seen == null) 0L else now
        }
        return queries.selectCourseCache(KEY_VALID_FROM).executeAsOneOrNull()?.toLongOrNull() ?: 0L
    }

    companion object {
        const val FREE_LESSONS_PER_DAY = 2
        const val FREE_GAME_LEVELS_PER_DAY = 1
        private const val KEY_FREE_GAME_LEVELS = "pref:free_game_levels"
        private const val FREE_LESSONS_WINDOW_MS = 24 * 60 * 60 * 1000L
        private const val KEY_SERVER_CLOCK = "pref:server_clock"
        private const val KEY_FREE_LESSONS = "pref:free_lessons"
        private const val KEY_EXPLAIN_AR = "pref:explain_ar"
        private const val KEY_COURSES_VERSION = "pref:courses_version"
        private const val KEY_VALID_FROM = "pref:courses_valid_from"
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
