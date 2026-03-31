package com.speakmind.app.feature.home.data

import com.speakmind.app.feature.home.domain.model.Scenario
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import speakmind.composeapp.generated.resources.Res

class ScenarioRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedScenarios: List<Scenario>? = null

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadScenarios(): List<Scenario> {
        cachedScenarios?.let { return it }
        val bytes = Res.readBytes("files/scenarios.json")
        val jsonString = bytes.decodeToString()
        val scenarios = json.decodeFromString<List<Scenario>>(jsonString)
        cachedScenarios = scenarios
        return scenarios
    }

    suspend fun getDailyScenarios(userLevel: String): List<Scenario> {
        val all = loadScenarios()
        // Return all scenarios sorted by relevance to user level
        val levelOrder = listOf("A1", "A2", "B1", "B2", "C1")
        val userIdx = levelOrder.indexOf(userLevel).coerceAtLeast(0)
        return all.sortedBy {
            val idx = levelOrder.indexOf(it.level).coerceAtLeast(0)
            kotlin.math.abs(idx - userIdx)
        }
    }
}
