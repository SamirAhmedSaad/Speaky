package com.speakmind.app.feature.story.data

import com.speakmind.app.feature.story.domain.model.Story
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object RedditParser {

    private val TITLE_REGEX = Regex(""""title"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    private val SELFTEXT_REGEX = Regex(""""selftext"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.DOT_MATCHES_ALL)
    private val URL_REGEX = Regex(""""url"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    private val CREATED_REGEX = Regex(""""created_utc"\s*:\s*([\d.]+)""")

    private val KIND_T3_REGEX = Regex(""""kind"\s*:\s*"t3"""")

    fun parse(json: String, topicLabel: String): List<Story> {
        val parts = KIND_T3_REGEX.split(json)
        return parts.drop(1).mapNotNull { part ->
            parsePost(part, topicLabel)
        }
    }

    private fun parsePost(json: String, topicLabel: String): Story? {
        val title = TITLE_REGEX.find(json)?.groupValues?.get(1)?.unescapeJson() ?: return null
        val selftext = SELFTEXT_REGEX.find(json)?.groupValues?.get(1)?.unescapeJson() ?: return null

        if (selftext.isBlank() || selftext == "[removed]" || selftext == "[deleted]") return null
        if (selftext.length < 100) return null

        val url = URL_REGEX.find(json)?.groupValues?.get(1)?.unescapeJson() ?: ""
        val createdUtc = CREATED_REGEX.find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val pubDate = formatEpoch(createdUtc.toLong())

        return Story(
            title = title,
            content = selftext.take(5000),
            link = url,
            level = 0,
            category = topicLabel,
            pubDate = pubDate,
        )
    }

    private val UNICODE_ESCAPE_REGEX = Regex("""\\u([0-9a-fA-F]{4})""")

    private fun String.unescapeJson(): String = this
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\\\", "\\")
        .replace("\\r", "")
        .let { s -> UNICODE_ESCAPE_REGEX.replace(s) { it.groupValues[1].toInt(16).toChar().toString() } }
        .trim()

    private fun formatEpoch(epochSeconds: Long): String {
        return try {
            val instant = Instant.fromEpochSeconds(epochSeconds)
            val local = instant.toLocalDateTime(TimeZone.UTC)
            val month = local.month.name.lowercase()
                .replaceFirstChar { it.uppercase() }
            "$month ${local.dayOfMonth}, ${local.year}"
        } catch (_: Exception) {
            ""
        }
    }
}
