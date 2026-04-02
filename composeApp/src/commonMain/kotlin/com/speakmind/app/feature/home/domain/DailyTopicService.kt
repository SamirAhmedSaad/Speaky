package com.speakmind.app.feature.home.domain

import com.speakmind.app.db.SpeakyDatabase
import com.speakmind.app.feature.home.data.DailyTopicCache
import com.speakmind.app.feature.home.data.NewsInLevelsRepository
import com.speakmind.app.feature.home.data.ScenarioRepository
import com.speakmind.app.feature.home.domain.model.Scenario
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class DailyTopicService(
    private val scenarioRepository: ScenarioRepository,
    private val dailyTopicCache: DailyTopicCache,
    private val dailyTopicSelector: DailyTopicSelector,
    private val database: SpeakyDatabase,
    private val newsInLevelsRepository: NewsInLevelsRepository,
) {

    suspend fun getDailyScenarios(userLevel: String): List<Scenario> {
        // Try fetching fresh topics from RSS first
        val rssTopics = newsInLevelsRepository.fetchDailyTopics(userLevel)
        if (rssTopics.isNotEmpty()) {
            return rssTopics
        }

        // Fallback: use static scenarios with selection algorithm
        return getStaticScenarios(userLevel)
    }

    private suspend fun getStaticScenarios(userLevel: String): List<Scenario> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

        // Check cache first
        val cachedIds = dailyTopicCache.getCachedTopics(today, userLevel)
        if (cachedIds != null) {
            val allScenarios = scenarioRepository.loadScenarios()
            val idMap = allScenarios.associateBy { it.id }
            return cachedIds.mapNotNull { idMap[it] }
        }

        // Gather inputs
        val allScenarios = scenarioRepository.loadScenarios()
        val recentIds = database.speakMindQueries
            .selectRecentScenarioIds()
            .executeAsList()
            .filterNotNull()
            .toSet()

        val weakAreas = database.speakMindQueries
            .selectTopMistakes()
            .executeAsList()
            .map { it.error_type }

        // Run selection algorithm
        val selected = dailyTopicSelector.selectDailyTopics(
            allScenarios = allScenarios,
            userLevel = userLevel,
            date = today,
            recentScenarioIds = recentIds,
            weakAreas = weakAreas,
        )

        // Cache results
        dailyTopicCache.cacheTopics(today, userLevel, selected.map { it.id })

        // Cleanup old entries
        dailyTopicCache.cleanup(beforeDate = today)

        return selected
    }
}
