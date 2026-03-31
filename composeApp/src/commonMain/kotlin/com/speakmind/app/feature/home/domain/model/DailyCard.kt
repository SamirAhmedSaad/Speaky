package com.speakmind.app.feature.home.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Scenario(
    val id: String,
    val title: String,
    val category: String,
    val level: String,
    @SerialName("emotional_stakes") val emotionalStakes: String,
    @SerialName("ai_opening") val aiOpening: String,
    @SerialName("suggested_vocab") val suggestedVocab: List<String> = emptyList(),
    @SerialName("learning_goal") val learningGoal: String = "",
    @SerialName("rescue_hint") val rescueHint: String = "",
)

data class DailyCard(
    val scenario: Scenario,
    val categoryIcon: String = getCategoryIcon(scenario.category),
)

fun getCategoryIcon(category: String): String = when (category) {
    "Daily Life" -> "\uD83C\uDFE0"
    "Travel" -> "\u2708\uFE0F"
    "Work" -> "\uD83D\uDCBC"
    "Social" -> "\uD83D\uDC65"
    "News & Opinions" -> "\uD83D\uDCF0"
    "Emergency" -> "\uD83D\uDEA8"
    "Business English" -> "\uD83D\uDCCA"
    "Idioms & Slang" -> "\uD83D\uDCAC"
    else -> "\uD83D\uDCDA"
}

fun getLevelColor(level: String): Long = when (level) {
    "A1" -> 0xFF4CAF50
    "A2" -> 0xFF8BC34A
    "B1" -> 0xFFFFEB3B
    "B2" -> 0xFFFF9800
    "C1" -> 0xFFF44336
    else -> 0xFF9E9E9E
}
