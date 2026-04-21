package com.speakmind.app.feature.geminichat.data

import com.speakmind.app.feature.chat.domain.model.ChatMessage
import com.speakmind.app.feature.chat.domain.model.MessageRole
import com.speakmind.app.feature.home.domain.model.Scenario
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class NoModelQuotaException(cause: Throwable? = null) :
    Exception("Your API key doesn't have quota for any available model.", cause)

class InvalidApiKeyException(message: String = "Your Gemini API key is invalid or has been revoked.") :
    Exception(message)

class GeminiRepository(private val httpClient: HttpClient) {

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    // Tried in order — first one that succeeds is used for the rest of the session
    private val modelFallbacks = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
    )

    suspend fun chat(
        apiKey: String,
        messages: List<ChatMessage>,
        userLevel: String,
        scenario: Scenario? = null,
        isStructured: Boolean = false,
    ): String {
        val systemPrompt = if (isStructured) {
            "You are a JSON API. Respond with ONLY valid JSON. No explanations, no markdown, no extra text."
        } else buildString {
            append("You are Sage, a warm and encouraging English language tutor inside the Speaky app.")
            append(" The user's current level is $userLevel.")
            append(" Keep your responses conversational and to 2-4 sentences.")
            append(" Gently correct grammar mistakes by naturally including the correct form in your reply.")
            append(" Be supportive, friendly, and help the user feel confident practicing English.")
            if (scenario != null) {
                append(" The conversation is about: ${scenario.title}.")
                if (scenario.emotionalStakes.isNotBlank()) {
                    append(" Background context (not to be repeated verbatim): ${scenario.emotionalStakes}")
                }
                if (scenario.learningGoal.isNotBlank()) {
                    append(" Learning goal: ${scenario.learningGoal}.")
                }
                append(" Use this context to ask relevant follow-up questions and guide the discussion naturally.")
            }
        }

        val contents = buildJsonArray {
            messages
                .filter { it.role != MessageRole.SYSTEM }
                .forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.role == MessageRole.USER) "user" else "model")
                        putJsonArray("parts") {
                            addJsonObject { put("text", msg.content) }
                        }
                    }
                }
        }

        val requestBody = buildJsonObject {
            put("contents", contents)
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", systemPrompt) }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.7)
                put("maxOutputTokens", 512)
            }
        }.toString()

        var lastError: Exception? = null
        for (model in modelFallbacks) {
            try {
                val url = "$baseUrl/$model:generateContent?key=$apiKey"
                val response = httpClient.post(url) {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    setBody(requestBody)
                }
                val body = response.bodyAsText()
                val errorCode = extractErrorCode(body)
                if (errorCode == 401L || errorCode == 403L) {
                    // Key is invalid or revoked — no point retrying other models
                    throw InvalidApiKeyException()
                }
                if (errorCode == 429L || errorCode == 404L) {
                    // Quota exhausted or model not found — try next model
                    Napier.w { "Model $model returned $errorCode, trying next fallback" }
                    continue
                }
                return parseResponse(body)
            } catch (e: Exception) {
                Napier.e { "Gemini API error with model $model: ${e.message}" }
                lastError = e
            }
        }
        throw NoModelQuotaException(lastError)
    }

    private fun extractErrorCode(json: String): Long? {
        return try {
            Json.parseToJsonElement(json).jsonObject
                .get("error")?.jsonObject
                ?.get("code")?.jsonPrimitive?.longOrNull
        } catch (_: Exception) { null }
    }

    private fun parseResponse(json: String): String {
        return try {
            Json.parseToJsonElement(json).jsonObject
                .get("candidates")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content
                ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            Napier.e { "Failed to parse Gemini response: ${e.message}" }
            "Sorry, I had trouble with that response. Please try again."
        }
    }
}
