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
            val firstMeaning = meanings.firstOrNull()?.jsonObject ?: return null
            val partOfSpeech = firstMeaning["partOfSpeech"]?.jsonPrimitive?.content ?: ""
            val defs = firstMeaning["definitions"]?.jsonArray ?: return null
            val meaning = defs.firstOrNull()?.jsonObject
                ?.get("definition")?.jsonPrimitive?.content ?: return null
            val examples = defs
                .mapNotNull { it.jsonObject["example"]?.jsonPrimitive?.content }
                .take(3)

            WordLookupResult(
                word = word,
                phonetic = phonetic,
                audioUrl = audioUrl,
                partOfSpeech = partOfSpeech,
                meaning = meaning,
                examples = examples,
                level = null,
                source = WordLookupResult.Source.DICTIONARY,
            )
        } catch (_: Exception) {
            null
        }
    }
}
