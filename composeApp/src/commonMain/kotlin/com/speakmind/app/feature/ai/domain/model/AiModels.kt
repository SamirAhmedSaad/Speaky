package com.speakmind.app.feature.ai.domain.model

enum class CefrLevel(val label: String) {
    A1("A1"), A2("A2"), B1("B1"), B2("B2"), C1("C1");

    companion object {
        fun fromString(s: String): CefrLevel = entries.find { it.label == s } ?: A2
    }
}

data class DifficultyAssessment(
    val estimatedLevel: CefrLevel,
    val errorRate: Float,
    val avgSentenceLength: Float,
    val vocabularyComplexity: Float,
)
