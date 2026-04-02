package com.speakmind.app.feature.home.data

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.home.domain.model.Scenario
import com.speakmind.app.feature.story.data.RssParser
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class NewsInLevelsRepository(
    private val httpClient: HttpClient,
    private val database: SpeakyDatabase,
) {
    companion object {
        private const val RSS_URL = "https://www.newsinlevels.com/feed/"

        /**
         * News in Levels publishes each article in 3 levels.
         * Map app CEFR levels to NiL difficulty levels.
         */
        fun nilLevelForCefr(cefr: String): Int = when (cefr) {
            "A1", "A2" -> 1
            "B1", "B2" -> 2
            "C1"       -> 3
            else       -> 1
        }

        fun cefrLabel(nilLevel: Int): String = when (nilLevel) {
            1    -> "A2"
            2    -> "B1"
            3    -> "C1"
            else -> "A2"
        }
    }

    /**
     * Fetch daily topics from News in Levels RSS feed.
     * Each article is published at 3 levels — we filter to the one matching the user.
     * Returns Scenario objects, or empty list on failure.
     */
    suspend fun fetchDailyTopics(userLevel: String): List<Scenario> = withContext(Dispatchers.IO) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

        // Check cache first
        val cached = database.speakMindQueries.selectRssTopicsByDate(today, userLevel).executeAsList()
        if (cached.isNotEmpty()) {
            return@withContext cached.map { row ->
                Scenario(
                    id = row.id,
                    title = row.title,
                    category = row.category,
                    level = row.level,
                    emotionalStakes = row.description,
                    aiOpening = row.ai_opening,
                    learningGoal = "Discuss this news article, practice related vocabulary, and share opinions",
                    articleUrl = row.article_url,
                )
            }
        }

        // Fetch from network
        try {
            val response = httpClient.get(RSS_URL)
            val xml = response.bodyAsText()
            val allStories = RssParser.parse(xml)

            val nilLevel = nilLevelForCefr(userLevel)

            // Filter to only the articles at the user's level
            val scenarios = allStories
                .filter { it.level == nilLevel }
                .map { story ->
                    val id = "nil_${story.link.hashCode()}"
                    val aiOpening = buildAiOpening(story.title, story.content)

                    database.speakMindQueries.insertRssTopic(
                        id = id,
                        title = story.title,
                        category = story.category,
                        level = userLevel,
                        description = story.content,
                        ai_opening = aiOpening,
                        article_url = story.link,
                        image_url = "",
                        fetched_date = today,
                    )

                    Scenario(
                        id = id,
                        title = story.title,
                        category = story.category,
                        level = userLevel,
                        emotionalStakes = story.content,
                        aiOpening = aiOpening,
                        learningGoal = "Discuss this news article, practice related vocabulary, and share opinions",
                        articleUrl = story.link,
                    )
                }

            // Clean up old topics
            database.speakMindQueries.deleteOldRssTopics(today)

            scenarios
        } catch (e: Exception) {
            Napier.e("Failed to fetch RSS topics from News in Levels", e)
            emptyList()
        }
    }

    /**
     * Look up an RSS topic by ID (used by ChatViewModel when opening a conversation).
     */
    suspend fun getTopicById(id: String): Scenario? = withContext(Dispatchers.IO) {
        val row = database.speakMindQueries.selectRssTopicById(id).executeAsOneOrNull()
            ?: return@withContext null
        Scenario(
            id = row.id,
            title = row.title,
            category = row.category,
            level = row.level,
            emotionalStakes = row.description,
            aiOpening = row.ai_opening,
            learningGoal = "Discuss this news article, practice related vocabulary, and share opinions",
            articleUrl = row.article_url,
        )
    }

    private fun buildAiOpening(title: String, content: String): String {
        // Use first 200 chars of the article as context for the opener
        val snippet = content.take(200).let {
            if (content.length > 200) "$it..." else it
        }
        return "Let's talk about today's news: \"$title\". $snippet What are your thoughts on this?"
    }
}
