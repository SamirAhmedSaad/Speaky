package com.speakmind.app.feature.vocabulary.data

import com.speakmind.app.feature.vocabulary.domain.model.VocabLevel
import com.speakmind.app.feature.vocabulary.domain.model.VocabularyData
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import speaky.composeapp.generated.resources.Res

class VocabularyRepository(private val json: Json) {
    private var cachedData: VocabularyData? = null

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadVocabulary(): VocabularyData {
        cachedData?.let { return it }
        val bytes = Res.readBytes("files/vocabulary.json")
        val jsonString = bytes.decodeToString()
        val data = json.decodeFromString<VocabularyData>(jsonString)
        cachedData = data
        return data
    }

    suspend fun getLevels(): List<VocabLevel> {
        return loadVocabulary().levels
    }

    suspend fun getByLevel(level: String): VocabLevel? {
        return loadVocabulary().levels.find { it.level == level }
    }
}
