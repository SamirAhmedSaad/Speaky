package com.speakmind.app.feature.home.domain

import com.speakmind.app.feature.home.domain.model.Scenario
import kotlin.random.Random

class DailyTopicSelector {

    private val levelOrder = listOf("A1", "A2", "B1", "B2", "C1")

    fun selectDailyTopics(
        allScenarios: List<Scenario>,
        userLevel: String,
        date: String,
        recentScenarioIds: Set<String>,
        weakAreas: List<String>,
        count: Int = 10,
    ): List<Scenario> {
        if (allScenarios.isEmpty()) return emptyList()

        val userIdx = levelOrder.indexOf(userLevel).coerceAtLeast(0)
        val seed = (date + userLevel).hashCode().toLong()
        val random = Random(seed)

        // Score each scenario
        val scored = allScenarios.map { scenario ->
            val levelIdx = levelOrder.indexOf(scenario.level).coerceAtLeast(0)
            val levelDiff = kotlin.math.abs(levelIdx - userIdx)

            // Level weight: same level = 3.0, one away = 1.5, two away = 0.5, further = 0.1
            val levelWeight = when (levelDiff) {
                0 -> 3.0
                1 -> 1.5
                2 -> 0.5
                else -> 0.1
            }

            // Weak area boost: +2.0 per matching tag
            val weakAreaBoost = if (weakAreas.isNotEmpty()) {
                scenario.practiceTags.count { tag -> weakAreas.any { it.equals(tag, ignoreCase = true) } } * 2.0
            } else 0.0

            // Recency penalty: recently played = 0.1x
            val recencyFactor = if (scenario.id in recentScenarioIds) 0.1 else 1.0

            // Random jitter for variety
            val jitter = 0.5 + random.nextDouble() * 1.0

            val score = (levelWeight + weakAreaBoost) * recencyFactor * jitter
            scenario to score
        }

        // Sort by score descending and pick top candidates
        val sorted = scored.sortedByDescending { it.second }

        // Ensure category diversity: pick top 7 by score, then fill remaining from underrepresented categories
        val selected = mutableListOf<Scenario>()
        val usedCategories = mutableMapOf<String, Int>()

        for ((scenario, _) in sorted) {
            if (selected.size >= count) break

            val categoryCount: Int = usedCategories[scenario.category] ?: 0
            // Allow max 2 from the same category in the first 7
            if (selected.size < 7 || categoryCount < 2) {
                selected.add(scenario)
                usedCategories[scenario.category] = categoryCount + 1
            }
        }

        // If we still need more, fill from remaining
        if (selected.size < count) {
            val selectedIds = selected.map { it.id }.toSet()
            for ((scenario, _) in sorted) {
                if (selected.size >= count) break
                if (scenario.id !in selectedIds) {
                    selected.add(scenario)
                }
            }
        }

        // Shuffle the final selection with the same seed for consistent order
        return selected.take(count).shuffled(Random(seed + 1))
    }
}
