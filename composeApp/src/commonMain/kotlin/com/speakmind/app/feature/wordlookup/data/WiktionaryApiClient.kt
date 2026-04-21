package com.speakmind.app.feature.wordlookup.data

import com.speakmind.app.feature.wordlookup.domain.WordLookupResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WiktionaryApiClient(private val httpClient: HttpClient) {

    suspend fun lookup(word: String): WordLookupResult? {
        return try {
            val response = httpClient.get(
                "https://en.wiktionary.org/api/rest_v1/page/definition/${word.trim().lowercase()}"
            )
            parse(word.trim(), response.bodyAsText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun lookupExamples(word: String): List<String> {
        return try {
            val response = httpClient.get(
                "https://en.wiktionary.org/api/rest_v1/page/definition/${word.trim().lowercase()}"
            )
            val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val defs = root["en"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("definitions")?.jsonArray ?: return emptyList()
            extractExamples(defs)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parse(word: String, json: String): WordLookupResult? {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            val langEntries = root["en"]?.jsonArray ?: return null
            val firstEntry = langEntries.firstOrNull()?.jsonObject ?: return null

            val partOfSpeech = firstEntry["partOfSpeech"]?.jsonPrimitive?.content ?: ""
            val definitions = firstEntry["definitions"]?.jsonArray ?: return null
            val firstDef = definitions.firstOrNull()?.jsonObject ?: return null
            val meaning = firstDef["definition"]?.jsonPrimitive?.content
                ?.stripHtml() ?: return null

            WordLookupResult(
                word = word,
                phonetic = "",
                audioUrl = null,
                partOfSpeech = partOfSpeech,
                meaning = meaning,
                examples = extractExamples(definitions),
                level = null,
                source = WordLookupResult.Source.WIKTIONARY,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractExamples(definitions: JsonArray): List<String> {
        return definitions.flatMap { def ->
            val obj = def.jsonObject
            val parsed = obj["parsedExamples"]?.jsonArray
                ?.mapNotNull { it.jsonObject["example"]?.jsonPrimitive?.content?.stripHtml() }
                ?: emptyList()
            val raw = obj["examples"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.stripHtml() }
                ?: emptyList()
            parsed + raw
        }.filter { it.isNotBlank() }.take(3)
    }

    private fun String.stripHtml(): String = replace(Regex("<[^>]+>"), "").trim()
}
