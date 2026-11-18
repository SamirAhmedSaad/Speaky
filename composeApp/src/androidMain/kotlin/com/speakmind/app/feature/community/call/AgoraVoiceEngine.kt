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
    /** Agora's numeric ids → community user ids (filled from Agora's callbacks, read on its threads). */
    private val accounts = java.util.concurrent.ConcurrentHashMap<Int, String>()

    // Agora reports volumes in two separate callbacks: one for me (uid 0) and one for everyone else.
    // Keep both and publish their union; replacing the whole set on each callback made the other
    // side's highlight vanish every 0.4 s.
    @Volatile private var meTalking = false
    @Volatile private var othersTalking: Set<String> = emptySet()
    @Volatile private var muted = false

    private fun publishSpeaking() {
        _speaking.value = if (meTalking && !muted) othersTalking + myUserId else othersTalking
    }

    /** Community user id for an Agora uid; asks the engine when the join callback hasn't arrived yet. */
    private fun accountOf(uid: Int): String? = accounts[uid] ?: runCatching {
        val info = UserInfo()
        if (rtc?.getUserInfoByUid(uid, info) == 0) info.userAccount?.takeIf { it.isNotEmpty() } else null
    }.getOrNull()?.also { accounts[uid] = it }
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
            userInfo?.userAccount?.takeIf { it.isNotEmpty() }?.let { accounts[uid] = it }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            accountOf(uid)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            accounts[uid]?.let { gone -> if (gone in othersTalking) { othersTalking = othersTalking - gone; publishSpeaking() } }
        }

        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            val list = speakers.orEmpty()
            val isLocalReport = list.size == 1 && list[0].uid == 0
            if (isLocalReport) {
                // Mine: voice activity (not just noise) and loud enough.
                val me = list[0]
                meTalking = me.vad == 1 && me.volume > SPEAKING_VOLUME
            } else {
                othersTalking = list
                    .filter { it.uid != 0 && it.volume > SPEAKING_VOLUME }
                    .mapNotNull { accountOf(it.uid) }
                    .toSet()
            }
            publishSpeaking()
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
        this.muted = muted
        rtc?.muteLocalAudioStream(muted)
        publishSpeaking()
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
