package com.speakmind.app.feature.wordlookup.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TranslationApiClient(private val httpClient: HttpClient) {

    suspend fun translateToArabic(word: String): String? {
        return try {
            val response = httpClient.get(
                "https://api.mymemory.translated.net/get?q=${word.trim().lowercase()}&langpair=en|ar"
            )
            val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val translated = root["responseData"]?.jsonObject
                ?.get("translatedText")?.jsonPrimitive?.content
            if (translated.isNullOrBlank() || translated == word) null else translated
        } catch (_: Exception) {
            null
        }
    }
}
