package com.speakmind.app.feature.course.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Rows written by tools/courses/add_lesson.py (Supabase tables courses, course_books, course_lessons). */
@Serializable
data class Course(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val sort: Int = 0,
    /** "Beginner", "Intermediate", "Beginner → Advanced"... shown as a badge. */
    val level: String? = null,
    /** The YouTube creator whose lessons the course follows. */
    @SerialName("channel_name") val channelName: String? = null,
    @SerialName("channel_url") val channelUrl: String? = null,
    /** The YouTube channel's logo, shown on the course card. */
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class CourseBook(
    val id: String,
    @SerialName("course_id") val courseId: String,
    val number: Int,
    val title: String,
)

@Serializable
data class LessonSummary(
    val id: String,
    @SerialName("book_id") val bookId: String,
    val number: Int,
    val title: String,
)

/** A course with its books and published lessons, for the course and book screens. */
@Serializable
data class CourseOutline(
    val course: Course,
    val books: List<CourseBook>,
    val lessons: List<LessonSummary>,
) {
    fun lessonsOf(bookId: String) = lessons.filter { it.bookId == bookId }.sortedBy { it.number }
}

@Serializable
data class Lesson(
    val id: String,
    @SerialName("book_id") val bookId: String,
    val number: Int,
    val title: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("channel_name") val channelName: String? = null,
    @SerialName("channel_url") val channelUrl: String? = null,
    val content: LessonContent = LessonContent(),
    val questions: List<ExamQuestion> = emptyList(),
) {
    val videoUrl get() = "https://www.youtube.com/watch?v=$videoId"
}

@Serializable
data class LessonContent(
    val vocabulary: List<LessonWord> = emptyList(),
    @SerialName("fine_lines") val fineLines: List<FineLine> = emptyList(),
    val expressions: List<LessonExpression> = emptyList(),
    val grammar: List<GrammarPoint> = emptyList(),
)

@Serializable
data class LessonWord(
    val word: String,
    val pos: String = "",
    @SerialName("meaning_en") val meaningEn: String = "",
    @SerialName("meaning_ar") val meaningAr: String = "",
    @SerialName("note_ar") val noteAr: String = "",
    @SerialName("note_en") val noteEn: String = "",
    val example: String = "",
)

@Serializable
data class FineLine(
    val title: String,
    val words: List<String> = emptyList(),
    @SerialName("explanation_ar") val explanationAr: String = "",
    @SerialName("explanation_en") val explanationEn: String = "",
    val examples: List<FineLineExample> = emptyList(),
)

@Serializable
data class FineLineExample(val word: String, val sentence: String)

@Serializable
data class LessonExpression(
    val phrase: String,
    @SerialName("meaning_ar") val meaningAr: String = "",
    @SerialName("meaning_en") val meaningEn: String = "",
    val example: String = "",
)

@Serializable
data class GrammarPoint(
    val title: String,
    @SerialName("explanation_ar") val explanationAr: String = "",
    @SerialName("explanation_en") val explanationEn: String = "",
    val examples: List<String> = emptyList(),
)

@Serializable
data class ExamQuestion(
    val question: String,
    val choices: List<String>,
    val answer: Int,
    @SerialName("explanation_ar") val explanationAr: String = "",
    @SerialName("explanation_en") val explanationEn: String = "",
)

/** A finished exam. A lesson counts as completed once its exam has been finished. */
data class LessonResult(val score: Int, val total: Int)
