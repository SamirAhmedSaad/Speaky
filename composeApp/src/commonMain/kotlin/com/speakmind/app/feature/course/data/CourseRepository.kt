package com.speakmind.app.feature.course.data

import com.speakmind.app.db.SpeakyDatabase
import io.github.aakira.napier.Napier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
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

    /** Finished exams by lesson id. */
    suspend fun results(): Map<String, LessonResult> = withContext(Dispatchers.IO) {
        queries.selectCourseProgress().executeAsList()
            .associate { it.lesson_id to LessonResult(it.score.toInt(), it.total.toInt()) }
    }

    suspend fun saveResult(lessonId: String, score: Int, total: Int) = withContext(Dispatchers.IO) {
        queries.upsertCourseProgress(lessonId, score.toLong(), total.toLong(), Clock.System.now().toEpochMilliseconds())
    }

    /** Fresh data when online (saved for later), the saved copy when offline, null if neither. */
    private suspend fun <T> cached(key: String, serializer: KSerializer<T>, fetch: suspend () -> T): T? =
        withContext(Dispatchers.IO) {
            try {
                fetch().also { fresh ->
                    queries.upsertCourseCache(key, json.encodeToString(serializer, fresh), Clock.System.now().toEpochMilliseconds())
                }
            } catch (e: Exception) {
                Napier.w(e) { "Course data offline, using saved copy: $key" }
                saved(key, serializer)
            }
        }
}
