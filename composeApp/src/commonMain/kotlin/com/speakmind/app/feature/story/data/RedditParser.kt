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

    // ----- Atom feed (https://www.reddit.com/r/<sub>/top/.rss) -----

    private val ENTRY_REGEX = Regex("""<entry>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL)
    private val FEED_TITLE_REGEX = Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)
    private val FEED_CONTENT_REGEX = Regex("""<content[^>]*>(.*?)</content>""", RegexOption.DOT_MATCHES_ALL)
    private val FEED_LINK_REGEX = Regex("""<link href="([^"]+)"""")
    private val FEED_PUBLISHED_REGEX = Regex("""<published>([^<]+)</published>""")
    private val BLOCK_END_REGEX = Regex("""</p>|<br\s*/?>|</li>|</h\d>|</blockquote>""", RegexOption.IGNORE_CASE)
    private val TAG_REGEX = Regex("""<[^>]+>""")
    private val COMMENT_REGEX = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val NUMERIC_ENTITY_REGEX = Regex("""&#(x?)([0-9a-fA-F]+);""")
    private val BLANK_LINES_REGEX = Regex("""\n[ \t]*\n(\s*\n)+""")
    private val SUBMITTED_BY_REGEX = Regex("""\s*submitted by\s.*$""", RegexOption.DOT_MATCHES_ALL)

    fun parseFeed(xml: String, topicLabel: String): List<Story> =
        ENTRY_REGEX.findAll(xml).mapNotNull { parseEntry(it.groupValues[1], topicLabel) }.toList()

    private fun parseEntry(entry: String, topicLabel: String): Story? {
        val title = FEED_TITLE_REGEX.find(entry)?.groupValues?.get(1)?.decodeEntities()?.trim() ?: return null
        val html = FEED_CONTENT_REGEX.find(entry)?.groupValues?.get(1)?.decodeEntities() ?: return null
        val text = html.htmlToText()
        if (text.isBlank() || text == "[removed]" || text == "[deleted]" || text.length < 100) return null
        val link = FEED_LINK_REGEX.find(entry)?.groupValues?.get(1)?.decodeEntities().orEmpty()
        val published = FEED_PUBLISHED_REGEX.find(entry)?.groupValues?.get(1)
        val epoch = published?.let { runCatching { Instant.parse(it).epochSeconds }.getOrNull() } ?: 0L
        return Story(
            title = title,
            content = text.take(5000),
            link = link,
            level = 0,
            category = topicLabel,
            pubDate = formatEpoch(epoch),
        )
    }

    private fun String.htmlToText(): String = this
        .replace(COMMENT_REGEX, "")
        .replace(BLOCK_END_REGEX, "\n\n")
        .replace(TAG_REGEX, "")
        .decodeEntities()
        .replace(SUBMITTED_BY_REGEX, "")
        .replace(BLANK_LINES_REGEX, "\n\n")
        .lines().joinToString("\n") { it.trim() }
        .trim()

    private fun String.decodeEntities(): String = this
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .let { s ->
            NUMERIC_ENTITY_REGEX.replace(s) { m ->
                val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isNotEmpty()) 16 else 10)
                code?.let { c -> if (c in 0..0xFFFF) c.toChar().toString() else "" } ?: m.value
            }
        }
        .replace("&amp;", "&")

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
