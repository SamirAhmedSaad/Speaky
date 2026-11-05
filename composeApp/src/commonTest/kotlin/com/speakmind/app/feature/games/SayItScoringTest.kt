package com.speakmind.app.feature.games

import com.speakmind.app.feature.games.ui.WordGameViewModel.Companion.match
import com.speakmind.app.feature.games.ui.WordGameViewModel.Companion.words
import kotlin.test.Test
import kotlin.test.assertEquals

class SayItScoringTest {
    private fun score(phrase: String, heard: String) = match(words(phrase), heard).let { m -> m.count { it } * 100 / m.size }

    @Test fun exactMatchIsFull() = assertEquals(100, score("Could I get the bill, please?", "could I get the bill please"))
    @Test fun punctuationAndCaseIgnored() = assertEquals(100, score("Where are you from?", "Where Are You From"))
    @Test fun contractionsExpand() = assertEquals(100, score("I do not like it.", "I don't like it"))
    @Test fun letUs() = assertEquals(100, score("Let us meet at the park.", "let's meet at the park"))
    @Test fun missingWordLowersScore() = assertEquals(83, score("Could I get the bill, please?", "could I get the please"))
    @Test fun wrongWordNotMatched() = assertEquals(listOf(true, true, false, true), match(words("I like green tea"), "I like grin tea"))
    @Test fun nothingHeard() = assertEquals(0, score("Nice to meet you.", ""))
    @Test fun extraWordsAreFine() = assertEquals(100, score("Nice to meet you.", "oh nice to meet you too"))
}
