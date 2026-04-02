package com.speakmind.app.feature.story.domain.model

data class Story(
    val id: Long = 0,
    val title: String,
    val content: String,
    val link: String,
    val level: Int,
    val category: String,
    val pubDate: String,
)

enum class StoryTopic(val subreddit: String, val label: String) {
    HORROR("nosleep", "Horror"),
    FUNNY("tifu", "Funny"),
    SCIFI("shortsciencefiction", "Sci-Fi"),
    WHOLESOME("TrueOffMyChest", "Wholesome"),
    MYSTERY("LetsNotMeet", "Mystery"),
    ADVENTURE("backpacking", "Adventure"),
    MOTIVATIONAL("offmychest", "Motivational"),
    LIFE_STORIES("confessions", "Life Stories"),
    THRILLER("Thetruthishere", "Thriller");

    companion object {
        fun fromLabel(label: String): StoryTopic =
            entries.firstOrNull { it.label == label } ?: HORROR
    }
}
