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

/**
 * Every game in the Games arcade. [levelSize] items make one level; [pass] right answers finish it and
 * unlock the next. Content comes per CEFR level (A1–C1), easiest first.
 */
enum class GameKind(
    val key: String,
    val title: String,
    val tagline: String,
    val category: String,
    val emoji: String,
    val colorA: Long,
    val colorB: Long,
    val levelSize: Int,
    val pass: Int,
) {
    RUSH("rush", "Idiom Rush", "Beat the clock, tap the right meaning", "Idioms", "🔥", 0xFFFFB36B, 0xFFFF5A5A, IdiomRepository.LEVEL_SIZE, IdiomRepository.RUSH_PASS),
    BUILDER("builder", "Idiom Builder", "Fix the broken idiom", "Idioms", "🧩", 0xFF7EE2B8, 0xFF19B88A, IdiomRepository.LEVEL_SIZE, IdiomRepository.LEVEL_SIZE),
    SPELL("spell", "Listen & Spell", "Hear a word, spell it right", "Listening", "🎧", 0xFF7CC4FF, 0xFF3B6BF5, 10, 7),
    SAY("say", "Say It!", "Speak the phrase, get scored", "Speaking", "🎤", 0xFFFF9EC4, 0xFFE0408A, 6, 4),
    SITUATION("situation", "What Would You Say?", "Pick the natural reply in real life", "Speaking", "☕", 0xFFFFD86B, 0xFFF59E0B, 8, 6),
    PHRASAL("phrasal", "Phrasal Pop", "Pop the missing word: give __ up", "Vocabulary", "🫧", 0xFFB49CFF, 0xFF5B3DF5, 10, 7),
    ODD("odd", "Odd One Out", "Find the word that doesn't belong", "Vocabulary", "🃏", 0xFF7FE3E0, 0xFF0E9F9A, 10, 7),
    ;

    val isIdiom get() = this == RUSH || this == BUILDER

    /** Progress key prefix; the idiom games keep their original keys. */
    internal val doneKey get() = if (isIdiom) "idiom:done:$key" else "game:done:$key"

    companion object {
        fun of(key: String) = entries.first { it.key == key }
    }
}

@Serializable
data class SpellItem(@SerialName("w") val word: String, @SerialName("s") val sentence: String, @SerialName("m") val meaning: String)

@Serializable
data class SayItem(@SerialName("p") val phrase: String, @SerialName("c") val context: String)

@Serializable
data class SituationItem(
    val place: String,
    @SerialName("e") val emoji: String,
    @SerialName("s") val situation: String,
    /** The first option is the best one. */
    @SerialName("o") val options: List<String>,
    @SerialName("n") val notes: List<String>,
)

@Serializable
data class PhrasalItem(
    @SerialName("q") val sentence: String,
    @SerialName("a") val answer: String,
    @SerialName("d") val distractors: List<String>,
    @SerialName("v") val verb: String,
    @SerialName("m") val meaning: String,
)

@Serializable
data class OddItem(
    @SerialName("t") val topic: String,
    @SerialName("w") val group: List<String>,
    @SerialName("o") val odd: String,
    @SerialName("e") val emoji: String,
    @SerialName("y") val why: String,
)

/** Content, progress and the free-level allowance for every game (idiom content stays in [IdiomRepository]). */
class GameRepository(
    private val database: SpeakyDatabase,
    private val courseRepository: CourseRepository,
    private val idioms: IdiomRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val files = mutableMapOf<String, Any>()
    private val queries get() = database.speakMindQueries

    @OptIn(ExperimentalResourceApi::class)
    @Suppress("UNCHECKED_CAST")
    private suspend inline fun <reified T> load(name: String): Map<String, List<T>> =
        (files[name] ?: try {
            json.decodeFromString<Map<String, List<T>>>(Res.readBytes("files/games/$name.json").decodeToString()).also { files[name] = it }
        } catch (e: Exception) {
            io.github.aakira.napier.Napier.w("Game content $name failed to load", e)
            emptyMap<String, List<T>>()
        }) as Map<String, List<T>>

    suspend fun spell(tier: String) = load<SpellItem>("spell")[tier].orEmpty()
    suspend fun say(tier: String) = load<SayItem>("say")[tier].orEmpty()
    suspend fun situations(tier: String) = load<SituationItem>("situation")[tier].orEmpty()
    suspend fun phrasal(tier: String) = load<PhrasalItem>("phrasal")[tier].orEmpty()
    suspend fun odd(tier: String) = load<OddItem>("odd")[tier].orEmpty()

    private suspend fun itemCount(kind: GameKind, tier: String): Int = when (kind) {
        GameKind.RUSH, GameKind.BUILDER -> idioms.idioms(tier).size
        GameKind.SPELL -> spell(tier).size
        GameKind.SAY -> say(tier).size
        GameKind.SITUATION -> situations(tier).size
        GameKind.PHRASAL -> phrasal(tier).size
        GameKind.ODD -> odd(tier).size
    }

    suspend fun levelCount(kind: GameKind, tier: String): Int = (itemCount(kind, tier) + kind.levelSize - 1) / kind.levelSize

    /** The items of [level] (0-based). */
    fun <T> levelItems(kind: GameKind, all: List<T>, level: Int): List<T> = all.chunked(kind.levelSize).getOrElse(level) { emptyList() }

    /** Finished levels of [tier]; the next one to play has this index (levels unlock in order). */
    suspend fun completed(kind: GameKind, tier: String): Int = read("${kind.doneKey}:$tier")?.toIntOrNull() ?: 0

    suspend fun markCompleted(kind: GameKind, tier: String, level: Int) {
        if (level + 1 > completed(kind, tier)) write("${kind.doneKey}:$tier", (level + 1).toString())
    }

    suspend fun tier(): String = idioms.tier()
    suspend fun setTier(tier: String) = idioms.setTier(tier)
    suspend fun appLevel(): String = idioms.appLevel()

    /** Null when this level may be played now (and counts it for free users), else millis until the next free one. */
    suspend fun useFreeLevel(kind: GameKind, tier: String, level: Int): Long? =
        if (Premium.active.value) null else courseRepository.useFreeGameLevel(kind.key, "$tier-$level")

    /** A free player still has today's new level in some game (the Games button's badge). */
    suspend fun freeLevelWaiting(): Boolean =
        !Premium.active.value && GameKind.entries.any { courseRepository.hasFreeGameLevel(it.key) }

    /** A missed word goes to the flashcards (once). */
    suspend fun saveToFlashcards(word: String, sentence: String, context: String, note: String) = withContext(Dispatchers.IO) {
        if (queries.selectAllFlashcards().executeAsList().any { it.word.equals(word, ignoreCase = true) }) return@withContext
        val now = Clock.System.now().toEpochMilliseconds()
        queries.insertFlashcard(
            word = word,
            sentence = sentence,
            context = context,
            grammar_note = note,
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

    private suspend fun read(key: String): String? = withContext(Dispatchers.IO) { queries.selectCourseCache(key).executeAsOneOrNull() }

    private suspend fun write(key: String, value: String) = withContext(Dispatchers.IO) {
        queries.upsertCourseCache(key, value, Clock.System.now().toEpochMilliseconds())
    }
}
