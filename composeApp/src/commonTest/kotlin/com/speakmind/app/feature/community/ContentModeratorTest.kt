package com.speakmind.app.feature.community

import com.speakmind.app.feature.community.data.ContentModerator
import com.speakmind.app.feature.community.data.ViolationType
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class ContentModeratorTest {
    private val blocked = mapOf(
        ViolationType.DATING to listOf(
            "be my girlfriend please", "I'm looking for a wife to marry", "My brother is dating", "I want to get married", "looking for a life partner", "ابحث عن زوجة", "زواج", "I'm looking for a girlfriend", "will you marry me", "go on a date with me", "I love you",
            "you are so beautiful", "send me your pic", "send ur photo", "show me your face",
            "let's video call", "any girls here?", "hi babe", "habibti",
        ),
        ViolationType.SEXUAL to listOf(
            "s3x", "seks chat", "let's make love", "sleep with me", "kinky", "send nudes",
            "sexy girl", "f*ck", "سكس", "ابعتي صور عارية",
            "are you gay", "g4y", "lesbian", "LGBT rights", "queer", "homosexual", "شواذ", "المثلية", "لوطي",
        ),
        ViolationType.CONTACT_HARVESTING to listOf(
            "my insta is john_99", "add me on ig", "i.n.s.t.a", "1nsta", "snap me", "my snap",
            "watsap me", "whats app", "wtsp", "telegram", "my tele", "fb", "tik tok",
            "0/5/0/1/2/3/4/5/6/7", "0 5 0 1 2 3 4 5 6 7", "05-01-23-45-67", "0.5.0.1.2.3.4.5",
            "zero five zero one two three four five", "٠٥٠١٢٣٤٥٦٧", "0️⃣5️⃣0️⃣1️⃣2️⃣3️⃣4️⃣5️⃣",
            "call 0 5 zero 1 2 three 4 5", "let's talk in another app", "we can chat on other app", "talk outside this app", "move to a different platform", "I can't talk here", "which app do you use?", "do you have social media", "نكمل في برنامج ثاني", "كلمني برا التطبيق", "خارج التطبيق", "add me on zangi", "on teams", "zoom id", "imo app", "add me on imo", "zinger", "my imo", "botim", "lets talk on zoom", "hellotalk", "email me", "ايمو", "زانجي", "give me your phone", "share your phone", "واتس", "انستا", "رقمك كم",
        ),
    )

    private val allowed = listOf(
        "Hello everyone, how are you today?", "What's up?", "whats up guys", "I was born in 1995.",
        "I have 2 cats and 3 dogs.", "Lesson 12 of 66 is hard.", "The price is 1,500 riyals.",
        "I work from 9 to 5 every day.", "I like chicken breast with rice.", "Use extra virgin olive oil.",
        "My number one goal is fluency.", "Can you explain the present perfect?", "I love this app!",
        "I love learning English.", "Where are you from?", "How old is your brother?",
        "My sister is married.", "Single words are easy to learn.", "I took a quick snapshot.",
        "The television is big.", "Instant coffee is cheap.", "Your body needs water.",
        "I sleep in my bed at night.", "We have one or two questions.", "I read one, two, three books.",
        "Are you single?", "Are you married?", "Do you have a girlfriend?", "Do you have a husband?",
        "Are you a boy or a girl?", "Is she married?", "My parents are married.", "I use this app every day.", "Which app is best for learning English?", "Another word for happy is glad.", "The other application form is long.", "I visited another site in Rome.", "Imogen is my teacher.", "The signal is weak here.", "Zoom in on the picture.", "Our teams won the match.", "IMO this is easy.", "We went on a hike.", "Yalla, let's start!", "Stand in line please.", "Let's meet at school.",
        "This verb is irregular: شاذ", "هو مثلي في الطول", "انا جاي بكرة", "أحب الزبدة", "ما هي جنسيتك؟", "عكس الكلمة", "شعري طويل", "حبيبي يا صديقي",
    )

    @Test
    fun blocksBadMessages() {
        val misses = blocked.flatMap { (type, list) -> list.filter { ContentModerator.check(it).isAllowed }.map { "$type: $it" } }
        if (misses.isNotEmpty()) fail("Not blocked:\n" + misses.joinToString("\n"))
    }

    @Test
    fun allowsNormalMessages() {
        val wrong = allowed.mapNotNull { m -> ContentModerator.check(m).takeIf { !it.isAllowed }?.let { "$m -> ${it.violationType}" } }
        if (wrong.isNotEmpty()) fail("Wrongly blocked:\n" + wrong.joinToString("\n"))
    }

    @Test
    fun hubTopicQuestionsAreAllowed() {
        val wrong = com.speakmind.app.feature.community.ui.room.HUB_TOPICS.flatMap { it.questions }
            .mapNotNull { q -> ContentModerator.check(q).takeIf { !it.isAllowed }?.let { "$q -> ${it.violationType}" } }
        if (wrong.isNotEmpty()) fail("Topic questions blocked:\n" + wrong.joinToString("\n"))
    }

    @Test
    fun hiddenNumberNeedsEightDigits() {
        assertTrue(ContentModerator.hasHiddenNumber("0/5/0/1/2/3/4/5"))
        assertTrue(!ContentModerator.hasHiddenNumber("1 2 3 4 5 6 7"))
    }
}
