package com.speakmind.app.feature.story.data

import com.speakmind.app.feature.story.domain.model.Story

object RssParser {

    private val ITEM_REGEX = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val TITLE_REGEX = Regex("<title><!\\[CDATA\\[(.*?)]]></title>|<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val LINK_REGEX = Regex("<link>(.*?)</link>")
    private val PUB_DATE_REGEX = Regex("<pubDate>(.*?)</pubDate>")
    private val CATEGORY_REGEX = Regex("<category><!\\[CDATA\\[(.*?)]]></category>|<category>(.*?)</category>")
    private val CONTENT_REGEX = Regex("<content:encoded><!\\[CDATA\\[(.*?)]]></content:encoded>", RegexOption.DOT_MATCHES_ALL)
    private val DESCRIPTION_REGEX = Regex("<description><!\\[CDATA\\[(.*?)]]></description>|<description>(.*?)</description>", RegexOption.DOT_MATCHES_ALL)
    private val LEVEL_REGEX = Regex("–\\s*level\\s*(\\d)", RegexOption.IGNORE_CASE)
    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    fun parse(xml: String): List<Story> {
        val items = ITEM_REGEX.findAll(xml)
        return items.mapNotNull { matchResult ->
            val itemXml = matchResult.groupValues[1]
            parseItem(itemXml)
        }.toList()
    }

    private fun parseItem(itemXml: String): Story? {
        val rawTitle = extractTag(itemXml, TITLE_REGEX) ?: return null
        val link = extractTag(itemXml, LINK_REGEX) ?: return null

        val levelMatch = LEVEL_REGEX.find(rawTitle)
        val level = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val title = rawTitle
            .replace(LEVEL_REGEX, "")
            .replace("–", "")
            .trim()

        val rawContent = extractTag(itemXml, CONTENT_REGEX)
            ?: extractTag(itemXml, DESCRIPTION_REGEX)
            ?: ""
        val content = stripLeadingDate(stripHtml(rawContent).trim())

        val category = extractTag(itemXml, CATEGORY_REGEX) ?: ""
        val pubDate = extractTag(itemXml, PUB_DATE_REGEX) ?: ""

        return Story(
            title = title,
            content = content,
            link = link.trim(),
            level = level,
            category = category,
            pubDate = pubDate,
        )
    }

    private fun extractTag(xml: String, regex: Regex): String? {
        val match = regex.find(xml) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
    }

    private fun stripHtml(html: String): String {
        return html
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace(HTML_TAG_REGEX, "")
            .replace(Regex("\\s+"), " ")
    }

    private fun stripLeadingDate(text: String): String {
        return text
            .replace(Regex("^\\d{1,2}[.\\-/]\\d{1,2}[.\\-/]\\d{4}\\s*\\d{0,2}:?\\d{0,2}\\s*[–\\-]?\\s*"), "")
            .replace(Regex("^\\w+\\s+\\d{1,2},?\\s+\\d{4}\\s*[–\\-]?\\s*"), "")
            .trim()
    }
}
