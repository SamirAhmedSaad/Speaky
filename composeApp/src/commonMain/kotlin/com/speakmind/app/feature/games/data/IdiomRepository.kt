package com.speakmind.app.feature.games.data

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.course.data.CourseRepository
import com.speakmind.app.premium.Premium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import speaky.composeapp.generated.resources.Res

/** One idiom: [text] ("break the ice"), its English [meaning] and an [example] that contains it. */
@Serializable
data class Idiom(
    @SerialName("i") val text: String,
    @SerialName("m") val meaning: String,
    @SerialName("e") val example: String,
)

enum class IdiomGame(val key: String, val title: String) {
    RUSH("rush", "Idiom Rush"),
    BUILDER("builder", "Idiom Builder"),
}

/**
 * The idiom games' content and progress. Idioms come from files/idioms.json, grouped by CEFR level and
 * ordered from the most common; each game splits a level into game levels of [LEVEL_SIZE] idioms.
 * Progress lives in the course_cache table, next to the free-lesson allowance.
 */
class IdiomRepository(
    private val database: SpeakyDatabase,
    private val courseRepository: CourseRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: Map<String, List<Idiom>>? = null
    private val queries get() = database.speakMindQueries

    @OptIn(ExperimentalResourceApi::class)
    suspend fun idioms(tier: String): List<Idiom> {
        val all = cache ?: json.decodeFromString<Map<String, List<Idiom>>>(Res.readBytes("files/idioms.json").decodeToString())
            .also { cache = it }
        return all[tier].orEmpty()
    }

    /** The [LEVEL_SIZE] idioms of game level [level] (0-based). */
    suspend fun levelIdioms(tier: String, level: Int): List<Idiom> = idioms(tier).chunked(LEVEL_SIZE).getOrElse(level) { emptyList() }

    suspend fun levelCount(tier: String): Int = (idioms(tier).size + LEVEL_SIZE - 1) / LEVEL_SIZE

    /** How many game levels of [tier] are finished in [game]; the next one to play has this index. */
    suspend fun completed(game: IdiomGame, tier: String): Int = read("idiom:done:${game.key}:$tier")?.toIntOrNull() ?: 0

    suspend fun markCompleted(game: IdiomGame, tier: String, level: Int) {
        if (level + 1 > completed(game, tier)) write("idiom:done:${game.key}:$tier", (level + 1).toString())
    }

    suspend fun bestScore(tier: String): Int = read("idiom:best:$tier")?.toIntOrNull() ?: 0

    suspend fun saveBestScore(tier: String, score: Int) {
        if (score > bestScore(tier)) write("idiom:best:$tier", score.toString())
    }

    /** The level picked in the games, else the app level from onboarding / settings. */
    suspend fun tier(): String = read(KEY_TIER)?.takeIf { it in TIERS } ?: appLevel()

    suspend fun setTier(tier: String) = write(KEY_TIER, tier)

    suspend fun appLevel(): String = withContext(Dispatchers.IO) {
        queries.selectProgress().executeAsOneOrNull()?.level?.takeIf { it in TIERS } ?: "A2"
    }

    /** Null when this level may be played (and counts it for free users), else millis until the next free level. */
    suspend fun useFreeLevel(game: IdiomGame, tier: String, level: Int): Long? =
        if (Premium.active.value) null else courseRepository.useFreeGameLevel(game.key, "$tier-$level")

    /** A free player still has today's new level in some game (the Games button's badge). */
    suspend fun freeLevelWaiting(): Boolean =
        !Premium.active.value && GameKind.entries.any { courseRepository.hasFreeGameLevel(it.key) }

    /** A missed idiom goes to the flashcards (once), with its meaning on the back. */
    suspend fun saveToFlashcards(idiom: Idiom) = withContext(Dispatchers.IO) {
        if (queries.selectAllFlashcards().executeAsList().any { it.word.equals(idiom.text, ignoreCase = true) }) return@withContext
        val now = Clock.System.now().toEpochMilliseconds()
        queries.insertFlashcard(
            word = idiom.text,
            sentence = idiom.example,
            context = "Idiom",
            grammar_note = idiom.meaning,
            error_type = "",
            next_review = now,
            interval_days = 1.0,
            ease_factor = 2.5,
            repetitions = 0,
            created_at = now,
            source_scenario_id = null,
        )
        queries.incrementVocab()
    }

    private suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        queries.selectCourseCache(key).executeAsOneOrNull()
    }

    private suspend fun write(key: String, value: String) = withContext(Dispatchers.IO) {
        queries.upsertCourseCache(key, value, Clock.System.now().toEpochMilliseconds())
    }

    companion object {
        const val LEVEL_SIZE = 10
        val TIERS = listOf("A1", "A2", "B1", "B2", "C1")
        val TIER_NAMES = mapOf("A1" to "Beginner", "A2" to "Elementary", "B1" to "Intermediate", "B2" to "Upper intermediate", "C1" to "Advanced")
        private const val KEY_TIER = "pref:idiom_tier"

        /** Idiom Rush round length: beginners get more time. */
        fun rushSeconds(tier: String) = when (tier) {
            "A1" -> 90
            "A2" -> 80
            "B1" -> 70
            "B2" -> 60
            else -> 50
        }

        /** Right answers needed in one Idiom Rush round to pass its level. */
        const val RUSH_PASS = 8
    }
}
