package com.speakmind.app.feature.vocabgroup.domain.model

data class VocabGroup(
    val id: Long,
    val name: String,
    val wordCount: Int,
    val createdAt: Long,
)

data class VocabGroupWord(
    val id: Long,
    val groupId: Long,
    val word: String,
    val meaning: String,
    val examples: List<String>,
    val phonetic: String,
    val partOfSpeech: String,
    val addedAt: Long,
)
