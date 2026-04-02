package com.speakmind.app.feature.vocabulary.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VocabularyData(
    val levels: List<VocabLevel>,
)

@Serializable
data class VocabLevel(
    val level: String,
    val label: String,
    val description: String,
    val words: List<VocabWord>,
)

@Serializable
data class VocabWord(
    val word: String,
    @SerialName("partOfSpeech") val partOfSpeech: String,
    @SerialName("frequencyRank") val frequencyRank: Int,
    val meaning: String,
    val sentences: List<String>,
)
