package com.speakmind.app.feature.dailyword.domain.model

data class DailyWordData(
    val id: Long,
    val word: String,
    val level: String,
    val partOfSpeech: String,
    val meaning: String,
    val sentences: List<String>,
    val sentDate: String,
    val isRead: Boolean,
)
