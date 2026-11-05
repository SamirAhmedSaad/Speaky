package com.speakmind.app.feature.community.call

import com.speakmind.app.config.AppConfig
import io.github.aakira.napier.Napier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CallMember(
    val userId: String,
    val nickname: String,
    val photoUrl: String?,
    val muted: Boolean,
    val premiumSeat: Boolean,
    val isPremium: Boolean,
    val joinedAtMillis: Long,
)

data class LiveCall(
    val id: String,
    val provider: String,
    val startedBy: String,
    val startedAtMillis: Long,
    val peakMembers: Int,
    val endedAtMillis: Long?,
    val endReason: String?,
    val members: List<CallMember>,
)

data class CallBan(val userId: String, val nickname: String, val untilMillis: Long?, val bannedBy: String?)

data class CallStats(val calls: Int, val agoraMinutes: Long, val liveKitMinutes: Long, val fallbackCalls: Int)

/** Why the server refused a seat, from the database function's exception. */
class CallRefused(val reason: String) : Exception(reason)

/** A provider couldn't hand out a token (keys missing on the server, or down). */
class ProviderUnavailable(val provider: String) : Exception("provider unavailable: $provider")

/** Hub voice calls on Supabase: who is in the call, seats, bans and the admin switch. Audio is not here. */
class HubCallRepository(private val client: SupabaseClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { HttpClient() }

    fun myUserId(): String? = client.auth.currentUserOrNull()?.id

    /** The room's live call, or null. */
    suspend fun liveCall(roomId: String): LiveCall? =
        client.from(CALLS).select(Columns.raw(CALL_COLUMNS)) {
            filter {
                eq("room_id", roomId)
                exact("ended_at", null)
            }
            limit(1)
        }.decodeList<CallDto>().firstOrNull()?.toModel()

    suspend fun call(callId: String): LiveCall? =
        client.from(CALLS).select(Columns.raw(CALL_COLUMNS)) {
            filter { eq("id", callId) }
        }.decodeList<CallDto>().firstOrNull()?.toModel()

    suspend fun callsEnabled(): Boolean = try {
        client.from("app_settings").select(Columns.list("value")) {
            filter { eq("key", "voice_calls_on") }
        }.decodeList<SettingDto>().firstOrNull()?.value?.toString() != "false"
    } catch (e: Exception) {
        true
    }

    /** Emits whenever a call, its members or the admin switch change. */
    fun changes(): Flow<Unit> = channelFlow {
        val channel = client.channel("voice-calls-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}")
        val flows = listOf(
            channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = CALLS },
            channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = MEMBERS },
            channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "app_settings" },
        )
        val jobs = flows.map { flow -> launch { flow.collect { send(Unit) } } }
        channel.subscribe()
        try {
            awaitCancellation()
        } finally {
            jobs.forEach { it.cancel() }
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
        }
    }

    /** Starts the room's call (or joins the live one). Returns the call id and its provider. */
    suspend fun start(roomId: String): Seat = seat("voice_call_start", buildJsonObject { put("p_room", roomId) })

    suspend fun join(callId: String): Seat = seat("voice_call_join", buildJsonObject { put("p_call", callId) })

    private suspend fun seat(function: String, params: kotlinx.serialization.json.JsonObject): Seat = try {
        val dto = client.postgrest.rpc(function, params).decodeAs<SeatDto>()
        Seat(dto.callId, dto.provider, dto.premiumSeat)
    } catch (e: Exception) {
        val message = e.message.orEmpty()
        throw CallRefused(REFUSALS.firstOrNull { message.contains(it) } ?: "failed")
    }

    suspend fun leave(callId: String) {
        runCatching { client.postgrest.rpc("voice_call_leave", buildJsonObject { put("p_call", callId) }) }
            .onFailure { Napier.w("voice_call_leave failed", it) }
    }

    /** False: the call ended or this user was removed. Null: offline, try again later. */
    suspend fun heartbeat(callId: String): Boolean? = try {
        client.postgrest.rpc("voice_call_heartbeat", buildJsonObject { put("p_call", callId) }).decodeAs<Boolean>()
    } catch (e: Exception) {
        null
    }

    suspend fun setMuted(callId: String, muted: Boolean) {
        runCatching {
            client.postgrest.rpc("voice_call_set_muted", buildJsonObject {
                put("p_call", callId)
                put("p_muted", muted)
            })
        }
    }

    /** Moves the starter's call to LiveKit; returns the provider the call ends up with. */
    suspend fun useFallback(callId: String): String =
        client.postgrest.rpc("voice_call_use_fallback", buildJsonObject { put("p_call", callId) }).decodeAs<String>()

    /** The signed token for the call's provider. */
    suspend fun token(callId: String): VoiceToken {
        val access = client.auth.currentAccessTokenOrNull() ?: throw CallRefused("not_signed_in")
        val response = http.post("${AppConfig.supabaseUrl}/functions/v1/voice-token") {
            header(HttpHeaders.Authorization, "Bearer $access")
            header("apikey", AppConfig.supabaseAnonKey)
            contentType(ContentType.Application.Json)
            setBody("""{"call_id":"$callId"}""")
        }
        val dto = json.decodeFromString(TokenDto.serializer(), response.bodyAsText())
        when (dto.error) {
            null -> Unit
            "provider_unavailable" -> throw ProviderUnavailable(dto.provider ?: "")
            "ended" -> throw CallRefused("ended")
            else -> throw CallRefused(dto.error)
        }
        return VoiceToken(dto.provider ?: PROVIDER_AGORA, dto.token.orEmpty(), dto.channel ?: callId, dto.appId, dto.url)
    }

    /** When this user's call ban ends; Long.MAX_VALUE for good; null when not banned. */
    suspend fun myBanUntil(): Long? {
        val me = myUserId() ?: return null
        return try {
            val ban = client.from(BANS).select(Columns.list("user_id", "until")) {
                filter { eq("user_id", me) }
            }.decodeList<BanDto>().firstOrNull() ?: return null
            val until = ban.until?.let { parseTime(it) } ?: return Long.MAX_VALUE
            until.takeIf { it > kotlinx.datetime.Clock.System.now().toEpochMilliseconds() }
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Admin ----------

    suspend fun isAdmin(): Boolean = try {
        client.postgrest.rpc("is_community_admin").decodeAs<Boolean>()
    } catch (e: Exception) {
        false
    }

    suspend fun adminEnd(callId: String) = adminRpc("voice_call_admin_end") { put("p_call", callId) }

    suspend fun adminMute(callId: String, userId: String) = adminRpc("voice_call_admin_mute") {
        put("p_call", callId)
        put("p_user", userId)
    }

    suspend fun adminRemove(callId: String, userId: String) = adminRpc("voice_call_admin_remove") {
        put("p_call", callId)
        put("p_user", userId)
    }

    /** [days] null bans for good. */
    suspend fun adminBan(userId: String, days: Int?) = adminRpc("voice_call_admin_ban") {
        put("p_user", userId)
        put("p_days", days)
    }

    suspend fun adminUnban(userId: String) = adminRpc("voice_call_admin_unban") { put("p_user", userId) }

    suspend fun setEnabled(on: Boolean, endLive: Boolean) = adminRpc("voice_calls_set_enabled") {
        put("p_on", on)
        put("p_end_live", endLive)
    }

    suspend fun stats(): CallStats? = try {
        val dto = client.postgrest.rpc("voice_call_admin_stats").decodeAs<StatsDto>()
        CallStats(dto.calls, dto.agoraMinutes.toLong(), dto.livekitMinutes.toLong(), dto.fallbackCalls)
    } catch (e: Exception) {
        Napier.w("call stats failed", e)
        null
    }

    suspend fun bans(): List<CallBan> = try {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val rows = client.from(BANS).select(Columns.list("user_id", "until", "banned_by")) {
            order("created_at", Order.DESCENDING)
        }.decodeList<BanDto>()
            .map { it to it.until?.let { until -> parseTime(until) } }
            .filter { (_, until) -> until == null || until > now }
        val names = if (rows.isEmpty()) emptyMap() else client.from("community_users").select(Columns.list("id", "nickname")) {
            filter { isIn("id", rows.map { it.first.userId }) }
        }.decodeList<NameDto>().associate { it.id to it.nickname }
        rows.map { (dto, until) -> CallBan(dto.userId, names[dto.userId].orEmpty(), until, dto.bannedBy) }
    } catch (e: Exception) {
        Napier.w("call bans failed", e)
        emptyList()
    }

    /** Postgres timestamps ("2026-11-06T08:51:34.930304+00:00") to epoch millis. */
    private fun parseTime(value: String): Long = Instant.parse(value.replace(" ", "T")).toEpochMilliseconds()

    private suspend fun adminRpc(function: String, params: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): Boolean = try {
        client.postgrest.rpc(function, buildJsonObject(params))
        true
    } catch (e: Exception) {
        Napier.w("$function failed", e)
        false
    }

    data class Seat(val callId: String, val provider: String, val premiumSeat: Boolean)

    private fun CallDto.toModel() = LiveCall(
        id = id,
        provider = provider,
        startedBy = startedBy,
        startedAtMillis = Instant.parse(startedAt).toEpochMilliseconds(),
        peakMembers = peakMembers,
        endedAtMillis = endedAt?.let { Instant.parse(it).toEpochMilliseconds() },
        endReason = endReason,
        members = members.orEmpty().map { m ->
            CallMember(
                userId = m.userId,
                nickname = m.user?.nickname.orEmpty(),
                photoUrl = m.user?.photoUrl,
                muted = m.muted,
                premiumSeat = m.premiumSeat,
                isPremium = m.user?.isPremium == true,
                joinedAtMillis = Instant.parse(m.joinedAt).toEpochMilliseconds(),
            )
        }.sortedBy { it.joinedAtMillis },
    )

    private companion object {
        const val CALLS = "voice_calls"
        const val MEMBERS = "voice_call_members"
        const val BANS = "voice_call_bans"
        const val CALL_COLUMNS = "id,provider,started_by,started_at,peak_members,ended_at,end_reason," +
            "members:voice_call_members(user_id,muted,premium_seat,joined_at," +
            "user:community_users!user_id(nickname,photo_url,is_premium))"
        val REFUSALS = listOf("premium_full", "full", "banned", "calls_off", "ended", "not_signed_in")
    }
}

@Serializable
private data class CallDto(
    val id: String,
    val provider: String = PROVIDER_AGORA,
    @SerialName("started_by") val startedBy: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("peak_members") val peakMembers: Int = 1,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
    val members: List<MemberDto>? = null,
)

@Serializable
private data class MemberDto(
    @SerialName("user_id") val userId: String,
    val muted: Boolean = false,
    @SerialName("premium_seat") val premiumSeat: Boolean = false,
    @SerialName("joined_at") val joinedAt: String,
    val user: UserDto? = null,
)

@Serializable
private data class UserDto(
    val nickname: String = "",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("is_premium") val isPremium: Boolean? = null,
)

@Serializable
private data class SeatDto(
    @SerialName("call_id") val callId: String,
    val provider: String = PROVIDER_AGORA,
    @SerialName("premium_seat") val premiumSeat: Boolean = false,
)

@Serializable
private data class TokenDto(
    val provider: String? = null,
    val token: String? = null,
    val channel: String? = null,
    @SerialName("app_id") val appId: String? = null,
    val url: String? = null,
    val error: String? = null,
)

@Serializable
private data class SettingDto(val value: JsonElement? = null)

@Serializable
private data class BanDto(
    @SerialName("user_id") val userId: String,
    val until: String? = null,
    @SerialName("banned_by") val bannedBy: String? = null,
    val user: UserDto? = null,
)

@Serializable
private data class NameDto(val id: String, val nickname: String = "")

@Serializable
private data class StatsDto(
    val calls: Int = 0,
    // Postgres divides to a decimal (7.05 minutes).
    @SerialName("agora_minutes") val agoraMinutes: Double = 0.0,
    @SerialName("livekit_minutes") val livekitMinutes: Double = 0.0,
    @SerialName("fallback_calls") val fallbackCalls: Int = 0,
)
