package com.speakmind.app.ui.theme

import com.speakmind.app.db.SpeakyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TtsSpeedManager(private val database: SpeakyDatabase) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun load() {
        val stored = database.speakMindQueries.selectProgress().executeAsOneOrNull()?.tts_speed ?: 1.0
        _speed.value = stored.toFloat()
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed
        scope.launch { database.speakMindQueries.updateTtsSpeed(speed.toDouble()) }
    }
}
