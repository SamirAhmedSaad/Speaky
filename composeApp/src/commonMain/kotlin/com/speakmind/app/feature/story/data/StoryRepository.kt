package com.speakmind.app.feature.story.data

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.story.domain.model.Story
import com.speakmind.app.feature.story.domain.model.StoryTopic
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class StoryRepository(
    private val httpClient: HttpClient,
    private val database: SpeakyDatabase,
) {
    companion object {
        private fun redditUrl(subreddit: String) =
            "https://www.reddit.com/r/$subreddit/top.json?limit=25&t=week"
    }

    private val fetchedTopics = mutableSetOf<String>()

    suspend fun getStories(topic: StoryTopic = StoryTopic.HORROR, forceRefresh: Boolean = false): List<Story> =
        withContext(Dispatchers.IO) {
            if (forceRefresh || topic.subreddit !in fetchedTopics) {
                try {
                    fetchAndCacheStories(topic)
                    fetchedTopics += topic.subreddit
                } catch (e: Exception) {
                    Napier.e("Failed to fetch Reddit stories for ${topic.subreddit}", e)
                }
            }

            database.speakMindQueries
                .selectStoriesByCategory(topic.label)
                .executeAsList()
                .map { row ->
                    Story(
                        id = row.id,
                        title = row.title,
                        content = row.content,
                        link = row.link,
                        level = row.level.toInt(),
                        category = row.category,
                        pubDate = row.pub_date,
                    )
                }
        }

    private suspend fun fetchAndCacheStories(topic: StoryTopic) {
        val response = httpClient.get(redditUrl(topic.subreddit)) {
            header("User-Agent", "android:com.speakmind.app:v1.0 (AI English Tutor)")
        }
        val json = response.bodyAsText()
        val stories = RedditParser.parse(json, topic.label)
        val now = Clock.System.now().toEpochMilliseconds()

        // Clean old stories for this topic (older than 7 days)
        val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
        database.speakMindQueries.deleteOldStories(sevenDaysAgo)

        stories.forEach { story ->
            database.speakMindQueries.insertStory(
                title = story.title,
                content = story.content,
                link = story.link,
                level = story.level.toLong(),
                category = story.category,
                pub_date = story.pubDate,
                fetched_at = now,
            )
        }
    }
}
