package com.speakmind.app.feature.wordlookup.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The most common meaning(s) of a word, most frequent first. */
data class WordSense(val partOfSpeech: String, val meaning: String)

/**
 * Datamuse (no key, generous limits) lists definitions with the most common sense first,
 * unlike dictionaryapi.dev, which lists them in a fixed order (e.g. "brave" → noun "A Native American warrior").
 */
class DatamuseApiClient(private val httpClient: HttpClient) {

    suspend fun lookup(word: String): WordSense? {
        return try {
            val response = httpClient.get("https://api.datamuse.com/words") {
                parameter("sp", word.trim().lowercase())
                parameter("md", "d")
                parameter("max", 1)
            }
            parse(word.trim().lowercase(), response.bodyAsText())
        } catch (_: Exception) {
            null
        }
    }

    internal fun parse(word: String, json: String): WordSense? {
        val entry = Json.parseToJsonElement(json).jsonArray.firstOrNull()?.jsonObject ?: return null
        if (entry["word"]?.jsonPrimitive?.content?.lowercase() != word) return null
        val senses = entry["defs"]?.jsonArray.orEmpty()
            .mapNotNull { def ->
                val (tag, text) = def.jsonPrimitive.content.split('\t', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                if (isUnusual(text)) return@mapNotNull null
                val clean = stripLabels(text)
                if (clean.isBlank() || isCircular(word, clean)) null else tag to clean
            }
        val primary = senses.firstOrNull() ?: return null
        // A second meaning from another part of speech, if there is one (e.g. "brave" adj + verb).
        val secondary = senses.firstOrNull { it.first != primary.first }
        val meaning = listOfNotNull(primary, secondary).joinToString("\n\n") { it.second }
        return WordSense(partOfSpeech = POS_NAMES[primary.first] ?: primary.first, meaning = meaning)
    }

    /** Senses learners shouldn't see first: old, rare, rude or regional. */
    private fun isUnusual(text: String): Boolean {
        val labels = LEADING_LABELS.find(text)?.value?.lowercase() ?: return false
        return UNUSUAL_LABELS.any { it in labels }
    }

    private fun stripLabels(text: String): String = text.replace(LEADING_LABELS, "").trim()

    /** "To run." for "run" explains nothing. */
    private fun isCircular(word: String, text: String): Boolean {
        val words = text.lowercase().replace(Regex("[^a-z ]"), "").split(' ').filter { it.isNotBlank() }
        return words.size <= 3 && word in words
    }

    private companion object {
        val LEADING_LABELS = Regex("""^(\s*\([^)]*\)\s*)+""")
        val UNUSUAL_LABELS = listOf(
            "dated", "obsolete", "archaic", "rare", "historical", "euphemistic", "slang", "vulgar",
            "offensive", "derogatory", "dialect", "nonstandard", "uncommon", "informal", "colloquial",
        )
        val POS_NAMES = mapOf("n" to "noun", "v" to "verb", "adj" to "adjective", "adv" to "adverb")
    }
}
