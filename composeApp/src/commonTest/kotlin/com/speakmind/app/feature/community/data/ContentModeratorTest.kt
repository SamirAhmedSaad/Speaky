package com.speakmind.app.feature.community.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentModeratorTest {

    @Test
    fun everydayEnglishIsAllowed() {
        listOf(
            "What is the title of this book?",
            "I signed the petition for the competition",
            "Her attitude changed after the institute",
            "A large quantity of grapes",
            "I live in Essex near the peacock farm",
            "The vibration of the phone woke me",
            "In my opinion, the signal is weak",
            "The line is slanted",
            "We will jump from topic to topic",
            "Can you help me with the past tense?",
            "I love learning English every day!",
        ).forEach { text ->
            assertTrue(ContentModerator.check(text).isAllowed, "Should allow: $text")
        }
    }

    @Test
    fun harmfulContentIsBlocked() {
        listOf(
            "visit example.com now",
            "https://spam.example/abc",
            "email me at someone@gmail.com",
            "call me +966 555 123 4567",
            "add me on instagram",
            "send nudes",
            "you are a b1tch",
            "f u c k",
            "shit",
            "kill yourself",
        ).forEach { text ->
            assertFalse(ContentModerator.check(text).isAllowed, "Should block: $text")
        }
    }
}
