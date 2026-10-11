package com.speakmind.app.feature.wordlookup.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TranslationApiClient(private val httpClient: HttpClient) {

    suspend fun translate(text: String): String? {
        return try {
            val trimmed = text.trim()
            val response = httpClient.get("https://api.mymemory.translated.net/get") {
                parameter("q", trimmed)
                parameter("langpair", "en|ar")
            }
            val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val translated = root["responseData"]?.jsonObject
                ?.get("translatedText")?.jsonPrimitive?.content
            if (translated.isNullOrBlank()) return null
            // Reject if translation is just the input with different casing/punctuation
            val normalize = { s: String -> s.replace(Regex("[^a-zA-Z0-9؀-ۿ]"), "").lowercase() }
            val hasArabic = translated.any { it in '؀'..'ۿ' }
            if (!hasArabic || normalize(translated) == normalize(trimmed)) return null
            translated
        } catch (_: Exception) {
            null
        }
    }
}
