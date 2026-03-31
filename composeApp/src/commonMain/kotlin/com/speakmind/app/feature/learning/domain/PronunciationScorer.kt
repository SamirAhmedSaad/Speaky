package com.speakmind.app.feature.learning.domain

data class PronunciationResult(
    val score: Int,
    val matchedWords: List<WordMatch>,
    val feedback: String,
)

data class WordMatch(
    val expected: String,
    val actual: String?,
    val isCorrect: Boolean,
)

object PronunciationScorer {

    /**
     * Compares user's spoken text (from STT) with the target text.
     * Returns a score 0-100 based on word-level accuracy.
     */
    fun score(target: String, spoken: String): PronunciationResult {
        val targetWords = target.lowercase().split("\\s+".toRegex())
            .map { it.replace(Regex("[^a-z']"), "") }
            .filter { it.isNotEmpty() }

        val spokenWords = spoken.lowercase().split("\\s+".toRegex())
            .map { it.replace(Regex("[^a-z']"), "") }
            .filter { it.isNotEmpty() }

        if (targetWords.isEmpty()) {
            return PronunciationResult(100, emptyList(), "Perfect!")
        }

        val matches = mutableListOf<WordMatch>()
        var correctCount = 0

        for (i in targetWords.indices) {
            val expected = targetWords[i]
            val actual = spokenWords.getOrNull(i)

            val isCorrect = actual != null && (
                actual == expected ||
                levenshteinRatio(expected, actual) >= 0.75f
            )

            if (isCorrect) correctCount++

            matches.add(WordMatch(
                expected = expected,
                actual = actual,
                isCorrect = isCorrect,
            ))
        }

        // Penalize extra or missing words
        val extraWords = (spokenWords.size - targetWords.size).coerceAtLeast(0)
        val missingWords = (targetWords.size - spokenWords.size).coerceAtLeast(0)
        val penalty = (extraWords + missingWords) * 5

        val rawScore = (correctCount.toFloat() / targetWords.size * 100).toInt()
        val finalScore = (rawScore - penalty).coerceIn(0, 100)

        val feedback = when {
            finalScore >= 90 -> "Excellent pronunciation!"
            finalScore >= 75 -> "Very good! Just a few words to improve."
            finalScore >= 50 -> "Good effort! Keep practicing the highlighted words."
            finalScore >= 25 -> "Nice try! Focus on the words marked in red."
            else -> "Let's try again. Listen carefully and repeat slowly."
        }

        return PronunciationResult(
            score = finalScore,
            matchedWords = matches,
            feedback = feedback,
        )
    }

    private fun levenshteinRatio(a: String, b: String): Float {
        val distance = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return if (maxLen == 0) 1f else 1f - (distance.toFloat() / maxLen)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[a.length][b.length]
    }
}
