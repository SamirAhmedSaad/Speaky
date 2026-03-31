package com.speakmind.app.feature.ai.domain

import com.speakmind.app.feature.chat.domain.model.Correction

data class ParsedResponse(
    val reply: String,
    val corrections: List<Correction>,
)

object ResponseParser {

    private val correctionPattern = Regex(
        """\[CORRECTION:\s*"(.+?)"\s*->\s*"(.+?)"\s*\|\s*TYPE:\s*(\w+)\s*(?:\|\s*(.+?))?\]""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawResponse: String): ParsedResponse {
        val corrections = mutableListOf<Correction>()

        // Extract corrections
        correctionPattern.findAll(rawResponse).forEach { match ->
            corrections.add(
                Correction(
                    original = match.groupValues[1],
                    corrected = match.groupValues[2],
                    type = match.groupValues[3].lowercase(),
                    explanation = match.groupValues.getOrNull(4) ?: "",
                )
            )
        }

        // Clean the reply by removing correction markers
        var cleanReply = rawResponse
        correctionPattern.findAll(rawResponse).forEach { match ->
            cleanReply = cleanReply.replace(match.value, "")
        }

        // Also handle simpler correction format
        val simplePattern = Regex("""\*\*Correction:\*\*\s*(.+)""")
        simplePattern.findAll(cleanReply).forEach { match ->
            cleanReply = cleanReply.replace(match.value, "").trim()
        }

        return ParsedResponse(
            reply = cleanReply.trim(),
            corrections = corrections,
        )
    }
}
