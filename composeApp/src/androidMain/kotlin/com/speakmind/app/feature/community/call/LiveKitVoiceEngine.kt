package com.speakmind.app.feature.community.call

import android.content.Context
import android.media.AudioManager
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** LiveKit voice, used when Agora can't connect at the start of a call. Room name = call id. */
class LiveKitVoiceEngine(private val context: Context) : VoiceEngine {
    private val _speaking = MutableStateFlow<Set<String>>(emptySet())
    override val speaking: StateFlow<Set<String>> = _speaking.asStateFlow()
    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null

    override suspend fun join(token: VoiceToken, userId: String) {
        val url = token.url ?: error("No LiveKit url")
        val created = LiveKit.create(context.applicationContext)
        room = created
        scope.launch {
            created.events.collect { event ->
                when (event) {
                    is RoomEvent.ActiveSpeakersChanged ->
                        _speaking.value = event.speakers.mapNotNull { it.identity?.value }.toSet()
                    is RoomEvent.Disconnected -> _connected.value = false
                    else -> Unit
                }
            }
        }
        created.connect(url, token.token)
        created.localParticipant.setMicrophoneEnabled(true)
        setSpeaker(true)
    }

    override fun setMuted(muted: Boolean) {
        val current = room ?: return
        scope.launch { current.localParticipant.setMicrophoneEnabled(!muted) }
    }

    override fun setSpeaker(on: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            // Android 12+: route the call to the loudspeaker or the earpiece.
            val type = if (on) android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            audio.availableCommunicationDevices.firstOrNull { it.type == type }?.let { audio.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = on
        }
    }

    override fun leave() {
        val current = room ?: return
        room = null
        current.disconnect()
        current.release()
        scope.cancel()
    }
}
