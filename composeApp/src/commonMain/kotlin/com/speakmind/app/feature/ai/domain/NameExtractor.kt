package com.speakmind.app.feature.ai.domain

import com.speakmind.app.feature.profile.domain.NameValidationResult
import com.speakmind.app.feature.profile.domain.NameValidator

object NameExtractor {

    // Each pattern captures only letters/hyphens/apostrophes — no spaces — so "samir and I"
    // doesn't bleed into the name. An optional second word handles compound names like "Mary Jane".
    private val patterns = listOf(
        Regex("""my name(?:'s| is)\s+([A-Za-z'\-]{1,20}(?:\s[A-Za-z'\-]{1,20})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:I'm|I am)\s+([A-Za-z'\-]{1,20}(?:\s[A-Za-z'\-]{1,20})?)""", RegexOption.IGNORE_CASE),
        Regex("""call me\s+([A-Za-z'\-]{1,20}(?:\s[A-Za-z'\-]{1,20})?)""", RegexOption.IGNORE_CASE),
        Regex("""you can call me\s+([A-Za-z'\-]{1,20}(?:\s[A-Za-z'\-]{1,20})?)""", RegexOption.IGNORE_CASE),
        Regex("""people call me\s+([A-Za-z'\-]{1,20}(?:\s[A-Za-z'\-]{1,20})?)""", RegexOption.IGNORE_CASE),
    )

    private val stopWords = setOf(
        "and", "but", "or", "so", "the", "a", "an", "is", "are", "was", "were",
        "i", "he", "she", "they", "we", "you", "it", "from", "in", "at", "to",
        "not", "no", "yes", "ok", "also", "just", "here", "there",
    )

    fun extract(message: String): String? {
        for (pattern in patterns) {
            val match = pattern.find(message) ?: continue
            val words = match.groupValues[1].trim().split(" ")
            // Drop any trailing word that looks like a stop word (catches "Mary and")
            val nameWords = words.takeWhile { it.lowercase() !in stopWords }
            val candidate = nameWords.take(2).joinToString(" ")
            if (candidate.isNotEmpty() && NameValidator.validate(candidate) is NameValidationResult.Valid) {
                return candidate.split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            }
        }
        return null
    }
}
