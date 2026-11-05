package com.speakmind.app.feature.vocabulary.domain

/** Example sentences come from a general corpus; skip violent and unsuitable ones. */
private val UNSUITABLE = Regex(
    "\\b(kill\\w*|murder\\w*|dead|death|die[sd]?|dying|blood\\w*|gun\\w*|shot|shoot\\w*|bomb\\w*|war|wars|terror\\w*|rape\\w*|suicide|drunk|beer|wine|alcohol|vodka|whisk(e)?y|sex\\w*|pork|bacon|casino|gambl\\w*|naked|kiss\\w*|girlfriend|boyfriend|dating|nightclub|drug\\w*)\\b",
    RegexOption.IGNORE_CASE,
)

/** The shortest suitable examples, at most [max]: a few clear ones beat a long list. */
fun cleanExamples(sentences: List<String>, max: Int): List<String> =
    sentences.filterNot { UNSUITABLE.containsMatchIn(it) }.sortedBy { it.length }.take(max)
