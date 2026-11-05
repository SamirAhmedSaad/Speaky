package com.speakmind.app.feature.community.call

import android.content.Context
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Agora voice: the channel is the call id and each user joins with their community user id. */
class AgoraVoiceEngine(private val context: Context) : VoiceEngine {
    private val _speaking = MutableStateFlow<Set<String>>(emptySet())
    override val speaking: StateFlow<Set<String>> = _speaking.asStateFlow()
    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var rtc: RtcEngine? = null
    private var myUserId = ""
    /** Agora's numeric ids → community user ids. */
    private val accounts = mutableMapOf<Int, String>()
    private val joined = CompletableDeferred<Unit>()

    private val handler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            joined.complete(Unit)
        }

        override fun onError(err: Int) {
            android.util.Log.w(TAG, "onError $err")
            if (!joined.isCompleted) joined.completeExceptionally(IllegalStateException("Agora error $err"))
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            android.util.Log.i(TAG, "connection state $state reason $reason")
            if (state == Constants.CONNECTION_STATE_FAILED) {
                if (!joined.isCompleted) joined.completeExceptionally(IllegalStateException("Agora connection failed ($reason)"))
                _connected.value = false
            }
        }

        override fun onUserInfoUpdated(uid: Int, userInfo: UserInfo?) {
            userInfo?.userAccount?.let { accounts[uid] = it }
        }

        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            _speaking.value = speakers.orEmpty()
                .filter { it.volume > SPEAKING_VOLUME }
                .mapNotNull { if (it.uid == 0) myUserId else accounts[it.uid] }
                .toSet()
        }
    }

    override suspend fun join(token: VoiceToken, userId: String) {
        myUserId = userId
        // Read outside apply: inside it, `context` would be RtcEngineConfig's own (still null) context.
        val appContext = context.applicationContext
        val config = RtcEngineConfig().apply {
            mContext = appContext
            mAppId = token.appId
            mEventHandler = handler
            mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            mAudioScenario = Constants.AUDIO_SCENARIO_CHATROOM
        }
        val engine = RtcEngine.create(config)
        rtc = engine
        // Some networks (e.g. in Saudi Arabia) block voice over UDP: let Agora fall back to its
        // cloud proxy over TCP 443, or force TCP when this SDK doesn't take the automatic mode.
        if (engine.setCloudProxy(Constants.PROXY_TYPE_TCP_AUTO_FALLBACK) != 0) {
            engine.setCloudProxy(Constants.TRANSPORT_TYPE_TCP_PROXY)
        }
        engine.enableAudio()
        engine.enableAudioVolumeIndication(400, 3, true)
        engine.setDefaultAudioRoutetoSpeakerphone(true)
        val options = ChannelMediaOptions().apply {
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            publishMicrophoneTrack = true
            autoSubscribeAudio = true
        }
        val result = engine.joinChannelWithUserAccount(token.token, token.channel, userId, options)
        if (result != 0) error("Agora join refused ($result)")
        joined.await()
    }

    override fun setMuted(muted: Boolean) {
        rtc?.muteLocalAudioStream(muted)
    }

    override fun setSpeaker(on: Boolean) {
        rtc?.setEnableSpeakerphone(on)
    }

    override fun leave() {
        val engine = rtc ?: return
        rtc = null
        engine.leaveChannel()
        // destroy() blocks until the engine stops; keep it off the caller's thread.
        Thread { RtcEngine.destroy() }.start()
    }

    private companion object {
        const val TAG = "AgoraVoice"
        const val SPEAKING_VOLUME = 25
    }
}
