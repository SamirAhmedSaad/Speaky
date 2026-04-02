package com.speakmind.app.feature.home.data

import com.speakmind.app.feature.home.domain.model.Scenario
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import speaky.composeapp.generated.resources.Res

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

    suspend fun getScenarioById(id: String): Scenario? {
        return loadScenarios().find { it.id == id }
    }
}
