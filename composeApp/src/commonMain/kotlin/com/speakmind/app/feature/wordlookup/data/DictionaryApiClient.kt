package com.speakmind.app.feature.wordlookup.data

import com.speakmind.app.feature.wordlookup.domain.WordLookupResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DictionaryApiClient(private val httpClient: HttpClient) {

    suspend fun lookup(word: String): WordLookupResult? {
        return try {
            val response = httpClient.get(
                "https://api.dictionaryapi.dev/api/v2/entries/en/${word.trim().lowercase()}"
            )
            parse(response.bodyAsText())
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(json: String): WordLookupResult? {
        return try {
            val entry = Json.parseToJsonElement(json).jsonArray
                .firstOrNull()?.jsonObject ?: return null

            val word = entry["word"]?.jsonPrimitive?.content ?: return null
            val phonetic = entry["phonetic"]?.jsonPrimitive?.content ?: ""

            val audioUrl = entry["phonetics"]?.jsonArray
                ?.firstOrNull {
                    it.jsonObject["audio"]?.jsonPrimitive?.content?.isNotEmpty() == true
                }
                ?.jsonObject?.get("audio")?.jsonPrimitive?.content

            val meanings = entry["meanings"]?.jsonArray ?: return null

            // Primary part of speech from first meaning
            val partOfSpeech = meanings.firstOrNull()?.jsonObject
                ?.get("partOfSpeech")?.jsonPrimitive?.content ?: ""

            // First definition from each part of speech (up to 2), joined as paragraphs
            val meaningParts = meanings.take(2).mapNotNull { m ->
                val defs = m.jsonObject["definitions"]?.jsonArray ?: return@mapNotNull null
                defs.firstOrNull()?.jsonObject?.get("definition")?.jsonPrimitive?.content
            }
            val meaning = meaningParts.joinToString("\n\n")
            if (meaning.isBlank()) return null

            // Examples from all definitions across all meanings
            val examples = meanings.flatMap { m ->
                m.jsonObject["definitions"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["example"]?.jsonPrimitive?.content }
                    ?: emptyList()
            }.take(3)

            WordLookupResult(
                word = word,
                phonetic = phonetic,
                audioUrl = audioUrl,
                partOfSpeech = partOfSpeech,
                meaning = meaning,
                examples = examples,
                level = null,
                arabicTranslation = null,
                source = WordLookupResult.Source.DICTIONARY,
            )
        } catch (_: Exception) {
            null
        }
    }
}
