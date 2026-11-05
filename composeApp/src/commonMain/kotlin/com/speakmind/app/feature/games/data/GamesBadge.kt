package com.speakmind.app.feature.games.data

import kotlinx.coroutines.flow.MutableStateFlow

/** Whether the bottom bar's Games button shows "today's free level is waiting". */
object GamesBadge {
    val waiting = MutableStateFlow(false)

    suspend fun refresh(repository: IdiomRepository) {
        waiting.value = repository.freeLevelWaiting()
    }
}
