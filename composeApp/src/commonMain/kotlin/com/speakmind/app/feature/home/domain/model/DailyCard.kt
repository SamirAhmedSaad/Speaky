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
    @SerialName("practice_tags") val practiceTags: List<String> = emptyList(),
    @SerialName("article_url") val articleUrl: String = "",
)

data class DailyCard(
    val scenario: Scenario,
    val categoryIcon: String = getCategoryIcon(scenario.category),
)

fun getCategoryIcon(category: String): String = when (category.lowercase()) {
    "daily life" -> "\uD83C\uDFE0"
    "travel" -> "\u2708\uFE0F"
    "work" -> "\uD83D\uDCBC"
    "social" -> "\uD83D\uDC65"
    "news & opinions" -> "\uD83D\uDCF0"
    "emergency" -> "\uD83D\uDEA8"
    "business english" -> "\uD83D\uDCCA"
    "idioms & slang" -> "\uD83D\uDCAC"
    "shopping" -> "\uD83D\uDED2"
    "technology" -> "\uD83D\uDCBB"
    "health" -> "\uD83C\uDFE5"
    "culture" -> "\uD83C\uDFA8"
    "education" -> "\uD83C\uDF93"
    "entertainment" -> "\uD83C\uDFAC"
    // RSS categories from News in Levels
    "news" -> "\uD83D\uDCF0"
    "interesting" -> "\uD83D\uDCA1"
    "information" -> "\u2139\uFE0F"
    "sport" -> "\u26BD"
    "nature" -> "\uD83C\uDF3F"
    "funny" -> "\uD83D\uDE04"
    "exercises" -> "\uD83D\uDCDD"
    "science" -> "\uD83D\uDD2C"
    // Additional categories from Breaking News English
    "business" -> "\uD83D\uDCBC"
    "food" -> "\uD83C\uDF54"
    else -> "\uD83D\uDCDA"
}

fun getLevelColor(level: String): Long = when (level) {
    "A1" -> 0xFF4CAF50
    "A2" -> 0xFF8BC34A
    "B1" -> 0xFFFFEB3B
    "B2" -> 0xFFFF9800
    "C1" -> 0xFFF44336
    "C2" -> 0xFF9C27B0
    else -> 0xFF9E9E9E
}

/** High-contrast versions for light backgrounds. */
fun getLevelColorLight(level: String): Long = when (level) {
    "A1" -> 0xFF2E7D32  // dark green
    "A2" -> 0xFF558B2F  // dark lime green
    "B1" -> 0xFFF57F17  // dark amber (replaces invisible yellow)
    "B2" -> 0xFFD84315  // deep burnt orange
    "C1" -> 0xFFC62828  // dark crimson
    "C2" -> 0xFF6A1B9A  // dark purple
    else -> 0xFF424242  // dark gray
}
