package com.speakmind.app.feature.story.ui

internal fun String.stripMarkdown(): String = this
    .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1") // [text](url) → text
    .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")      // ![img](url) → remove
    .replace(Regex("""[*_~`#>]+"""), "")                // bold/italic/code/headers
    .replace(Regex("""\s{2,}"""), " ")                  // collapse extra whitespace
    .trim()
