package com.speakmind.app.feature.wordlookup.domain

data class WordLookupResult(
    val word: String,
    val phonetic: String,
    val audioUrl: String?,
    val partOfSpeech: String,
    val meaning: String,
    val examples: List<String>,
    val level: String?,
    val source: Source,
) {
    enum class Source { DICTIONARY, AI_ENHANCED, AI_ONLY }
}
