package com.speakmind.app.feature.home.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewsInLevelsCleanupTest {
    @Test
    fun removesFeedFooterAndVideoPointer() {
        val raw = "Big news today. Difficult words: cut (reduction). You can watch the original video in the Level 3 section. " +
            "The post Pope Leo visits South America – level 2 appeared first on English news and easy articles for students of English."
        assertEquals("Big news today. Difficult words: cut (reduction).", NewsInLevelsRepository.cleanContent(raw))
    }

    @Test
    fun leavesNormalTextAlone() {
        val text = "The post office was closed. People waited outside."
        assertEquals(text, NewsInLevelsRepository.cleanContent(text))
    }

    @Test
    fun siteNoticesAreSkipped() {
        assertTrue(NewsInLevelsRepository.isSiteNotice("Information"))
        assertTrue(NewsInLevelsRepository.isSiteNotice(" information "))
        assertFalse(NewsInLevelsRepository.isSiteNotice("News"))
    }
}
