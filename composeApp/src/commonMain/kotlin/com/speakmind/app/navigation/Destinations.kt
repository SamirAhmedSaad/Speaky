package com.speakmind.app.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SplashDestination

@Serializable
data object HomeDestination

@Serializable
data class ChatDestination(val scenarioId: String? = null)

@Serializable
data object FlashcardReviewDestination

@Serializable
data class ModelDownloadDestination(val scenarioId: String? = null)
