package com.speakmind.app.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SplashDestination

@Serializable
data object OnboardingDestination

@Serializable
data object HomeDestination

@Serializable
data class ChatDestination(val scenarioId: String? = null)

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
data object ChannelDestination

@Serializable
data object MyGroupsDestination

@Serializable
data class GroupDetailDestination(val groupId: Long, val groupName: String)

