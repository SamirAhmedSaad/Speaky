package com.speakmind.app.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SplashDestination

@Serializable
data object OnboardingDestination

@Serializable
data object HomeDestination

@Serializable
data class ChatDestination(val scenarioId: String? = null, val conversationId: String? = null)

@Serializable
data class FlashcardReviewDestination(val showAllWords: Boolean = false)

@Serializable
data class ModelDownloadDestination(val scenarioId: String? = null)

@Serializable
data class AiSetupDestination(val scenarioId: String? = null)

@Serializable
data object VocabCategoryDestination

@Serializable
data class VocabWordListDestination(val level: String)

@Serializable
data object StoriesDestination

@Serializable
data class StoryDetailDestination(val storyId: Long)

@Serializable
data class WordDetailDestination(val wordId: Long = -1, val word: String = "")

@Serializable
data class ArticleDetailDestination(val scenarioId: String)

@Serializable
data object WordLookupDestination

@Serializable
data object PrivacyPolicyDestination

@Serializable
data object CommunitySetupDestination

@Serializable
data object CommunityHubDestination

@Serializable
data class RoomDestination(val roomId: String, val title: String)

@Serializable
data object PeopleDestination

@Serializable
data object AdminReportsDestination

@Serializable
data object AdminChatsDestination

/** Read-only view of one private chat for admins. */
@Serializable
data class AdminChatDestination(
    val roomId: String,
    val userAId: String,
    val userAName: String,
    val userBId: String?,
    val userBName: String,
)

@Serializable
data object MyGroupsDestination

@Serializable
data class GroupDetailDestination(val groupId: Long, val groupName: String)

@Serializable
data object CoursesDestination

@Serializable
data class CourseDestination(val courseId: String, val title: String)

@Serializable
data class CourseBookDestination(val courseId: String, val bookId: String, val title: String)

@Serializable
data class LessonDestination(val lessonId: String)

@Serializable
data class LessonExamDestination(val lessonId: String)
