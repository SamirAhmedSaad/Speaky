package com.speakmind.app.feature.community.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.ui.components.CommunityPageHeader
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.navigation.NavigationManager
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import io.github.aakira.napier.Napier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object AdminSubscribersDestination

enum class PremiumSource(val label: String) { PLAY("Google Play"), GRANTED("Granted"), BOTH("Play + granted"), LAPSED("Lapsed") }

data class PremiumMember(
    val id: String,
    val nickname: String,
    val photoUrl: String?,
    val level: String?,
    val email: String?,
    val lastSeen: Long?,
    val source: PremiumSource,
    val since: Long?,
    val playConfirmed: Long?,
    val ended: Long?,
    val grantedAt: Long?,
    val note: String?,
    val grantedBy: String?,
) {
    val isGranted get() = source == PremiumSource.GRANTED || source == PremiumSource.BOTH
    val isPlay get() = source == PremiumSource.PLAY || source == PremiumSource.BOTH
}

data class GrantCandidate(val id: String, val nickname: String, val photoUrl: String?, val email: String?, val premium: Boolean)

@Serializable
private data class MemberDto(
    val id: String,
    val nickname: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val level: String? = null,
    val email: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val source: String,
    @SerialName("premium_since") val since: String? = null,
    @SerialName("play_confirmed_at") val playConfirmed: String? = null,
    @SerialName("ended_at") val ended: String? = null,
    @SerialName("granted_at") val grantedAt: String? = null,
    val note: String? = null,
    @SerialName("granted_by_name") val grantedBy: String? = null,
)

@Serializable
private data class CandidateDto(
    val id: String,
    val nickname: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val email: String? = null,
    val premium: Boolean = false,
)

/** Admin-only RPCs (see migration 20261105130000_admin_subscribers.sql). */
class AdminSubscribersRepository(private val client: SupabaseClient) {
    suspend fun members(): List<PremiumMember> =
        client.postgrest.rpc("admin_premium_members").decodeList<MemberDto>().map { d ->
            PremiumMember(
                id = d.id, nickname = d.nickname.orEmpty(), photoUrl = d.photoUrl, level = d.level, email = d.email,
                lastSeen = time(d.lastSeen),
                source = when (d.source) {
                    "play" -> PremiumSource.PLAY
                    "granted" -> PremiumSource.GRANTED
                    "both" -> PremiumSource.BOTH
                    else -> PremiumSource.LAPSED
                },
                since = time(d.since), playConfirmed = time(d.playConfirmed), ended = time(d.ended),
                grantedAt = time(d.grantedAt), note = d.note, grantedBy = d.grantedBy,
            )
        }

    suspend fun search(query: String): List<GrantCandidate> =
        client.postgrest.rpc("admin_premium_search", buildJsonObject { put("p_query", query) })
            .decodeList<CandidateDto>().map { GrantCandidate(it.id, it.nickname.orEmpty(), it.photoUrl, it.email, it.premium) }

    suspend fun grant(userId: String) {
        client.postgrest.rpc("admin_grant_premium", buildJsonObject { put("p_user", userId) })
    }

    suspend fun revoke(userId: String) {
        client.postgrest.rpc("admin_revoke_premium", buildJsonObject { put("p_user", userId) })
    }

    private fun time(value: String?): Long? =
        value?.let { runCatching { Instant.parse(it.replace(' ', 'T')).toEpochMilliseconds() }.getOrNull() }
}

enum class SubscriberTab(val label: String) { ALL("All"), PLAY("Google Play"), GRANTED("Granted"), LAPSED("Lapsed") }

data class AdminSubscribersUiState(
    val loaded: Boolean = false,
    val error: Boolean = false,
    val members: List<PremiumMember> = emptyList(),
    val tab: SubscriberTab = SubscriberTab.ALL,
    val query: String = "",
    val openId: String? = null,
    val granting: Boolean = false,
    val grantQuery: String = "",
    val candidates: List<GrantCandidate> = emptyList(),
    val searching: Boolean = false,
    val busyId: String? = null,
) {
    val active get() = members.filter { it.source != PremiumSource.LAPSED }
    val shown: List<PremiumMember>
        get() {
            val byTab = when (tab) {
                SubscriberTab.ALL -> active
                SubscriberTab.PLAY -> members.filter { it.isPlay }
                SubscriberTab.GRANTED -> members.filter { it.isGranted }
                SubscriberTab.LAPSED -> members.filter { it.source == PremiumSource.LAPSED }
            }
            val q = query.trim().lowercase()
            return if (q.isEmpty()) byTab else byTab.filter { q in it.nickname.lowercase() || q in it.email.orEmpty().lowercase() }
        }
    val open get() = members.firstOrNull { it.id == openId }
}

class AdminSubscribersViewModel(
    private val repository: AdminSubscribersRepository,
    private val navigationManager: NavigationManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminSubscribersUiState())
    val uiState: StateFlow<AdminSubscribersUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            try {
                val members = repository.members()
                _uiState.update { it.copy(loaded = true, error = false, members = members) }
            } catch (e: Exception) {
                Napier.w("admin_premium_members failed", e)
                _uiState.update { it.copy(loaded = true, error = true) }
            }
        }
    }

    fun onTab(tab: SubscriberTab) = _uiState.update { it.copy(tab = tab) }
    fun onQuery(query: String) = _uiState.update { it.copy(query = query) }
    fun onOpen(id: String) = _uiState.update { it.copy(openId = id) }
    fun onClose() = _uiState.update { it.copy(openId = null, granting = false, grantQuery = "", candidates = emptyList()) }
    fun onOpenGrant() = _uiState.update { it.copy(granting = true, openId = null) }
    fun onBack() = navigationManager.back()

    fun onGrantQuery(query: String) {
        _uiState.update { it.copy(grantQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(candidates = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(searching = true) }
            val found = try { repository.search(query.trim()) } catch (e: Exception) { emptyList() }
            _uiState.update { it.copy(candidates = found, searching = false) }
        }
    }

    fun onGrant(userId: String) = change(userId) { repository.grant(userId) }

    fun onRevoke(userId: String) = change(userId) {
        repository.revoke(userId)
        _uiState.update { it.copy(openId = null) }
    }

    private fun change(userId: String, action: suspend () -> Unit) {
        if (_uiState.value.busyId != null) return
        _uiState.update { it.copy(busyId = userId) }
        viewModelScope.launch {
            try {
                action()
                val members = repository.members()
                _uiState.update { s -> s.copy(members = members, candidates = s.candidates.filterNot { it.id == userId }) }
            } catch (e: Exception) {
                Napier.w("premium change failed", e)
            }
            _uiState.update { it.copy(busyId = null) }
        }
    }
}

fun NavGraphBuilder.adminSubscribersScreen() {
    animatedComposable<AdminSubscribersDestination> {
        val viewModel = koinViewModel<AdminSubscribersViewModel>()
        val ui by viewModel.uiState.collectAsStateWithLifecycle()
        SubscribersContent(ui, viewModel)
    }
}

private val GoldGradient = Brush.linearGradient(listOf(Color(0xFFF5D27A), Color(0xFFD4A017), Color(0xFFA87A0C)))
private val GoldText = Color(0xFF2B1D00)

@Composable
private fun SubscribersContent(ui: AdminSubscribersUiState, viewModel: AdminSubscribersViewModel) {
    val colors = LocalSpeakMindColors.current
    Box(Modifier.fillMaxSize().background(colors.backgroundDark)) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CommunityPageHeader(title = "Subscribers", subtitle = "Premium members · admins only", onBack = viewModel::onBack) }
            when {
                !ui.loaded -> item { Text("Loading…", Modifier.padding(horizontal = 20.dp), color = colors.textMuted, fontSize = 15.sp) }
                ui.error -> item { Text("Couldn't load subscribers. Check your connection and try again.", Modifier.padding(horizontal = 20.dp), color = colors.textMuted, fontSize = 15.sp) }
                else -> {
                    item { Summary(ui) }
                    item { SearchField(ui.query, "Search name or email", viewModel::onQuery, Modifier.padding(horizontal = 20.dp)) }
                    item { Tabs(ui.tab, viewModel::onTab) }
                    if (ui.shown.isEmpty()) {
                        item {
                            Text(
                                if (ui.query.isNotBlank()) "Nobody matches “${ui.query.trim()}”." else when (ui.tab) {
                                    SubscriberTab.LAPSED -> "No Google Play subscription ended in the last 90 days."
                                    SubscriberTab.GRANTED -> "Nobody has granted Premium yet."
                                    else -> "No Premium members yet."
                                },
                                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = colors.textMuted, fontSize = 14.sp,
                            )
                        }
                    } else {
                        item {
                            Column(
                                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.surface).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                ui.shown.forEach { MemberRow(it) { viewModel.onOpen(it.id) } }
                            }
                        }
                    }
                }
            }
        }
        if (ui.loaded && !ui.error) {
            SpeakyPrimaryButton(
                "Grant Premium to someone", viewModel::onOpenGrant,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                background = Color(0xFF17142B), color = Color.White, height = 54.dp, radius = 16.dp,
                leading = SpeakyIcons.Crown,
            )
        }
    }
    ui.open?.let { MemberSheet(it, ui.busyId == it.id, viewModel) }
    if (ui.granting) GrantSheet(ui, viewModel)
}

@Composable
private fun Summary(ui: AdminSubscribersUiState) {
    val active = ui.active
    val monthAgo = Clock.System.now().toEpochMilliseconds() - 30L * 86_400_000L
    val newThisMonth = active.count { (it.since ?: it.grantedAt ?: 0L) > monthAgo }
    val lapsed = ui.members.count { it.source == PremiumSource.LAPSED }
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(GoldGradient).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row {
            Stat(active.size, "Premium now", Modifier.weight(1f))
            Stat(active.count { it.isPlay }, "Google Play", Modifier.weight(1f))
            Stat(active.count { it.isGranted }, "Granted", Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GoldText.copy(alpha = 0.18f)))
        Text("+$newThisMonth new in the last 30 days · $lapsed lapsed", color = GoldText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Stat(value: Int, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value.toString(), style = displayStyle(28.sp), color = GoldText)
        Text(label, color = GoldText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, background: Color? = null) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(background ?: colors.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SpeakyIcons.Search, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = colors.textMuted, fontSize = 15.sp)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.textPrimary), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Tabs(selected: SubscriberTab, onPick: (SubscriberTab) -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SubscriberTab.entries.forEach { tab ->
            val on = tab == selected
            Text(
                tab.label,
                Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) colors.textPrimary else colors.surface)
                    .clickable { onPick(tab) }.padding(horizontal = 12.dp, vertical = 9.dp),
                color = if (on) colors.surface else colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
    }
}

@Composable
private fun MemberRow(member: PremiumMember, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val lapsed = member.source == PremiumSource.LAPSED
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UserAvatar(member.nickname, member.photoUrl, 46.dp, Color.Transparent, premium = !lapsed)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    member.nickname.ifEmpty { "Someone" }, Modifier.weight(1f, fill = false),
                    color = if (lapsed) colors.textPrimary else colors.gold, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                member.level?.takeIf { it.isNotBlank() }?.let { Text(it, color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            val start = if (lapsed) "Ended ${member.ended?.let(::dateText) ?: "—"}" else "Since ${(member.since ?: member.grantedAt)?.let(::dateText) ?: "—"}"
            Text("$start · ${seenText(member.lastSeen)}", color = colors.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SourceBadge(member.source)
    }
}

@Composable
private fun SourceBadge(source: PremiumSource) {
    val colors = LocalSpeakMindColors.current
    val (bg, fg) = when (source) {
        PremiumSource.GRANTED -> Color(0xFFDDF6EC) to Color(0xFF0B5E45)
        PremiumSource.LAPSED -> colors.backgroundDark to colors.textMuted
        else -> Color(0xFFE0ECFF) to Color(0xFF1E4FA8)
    }
    Text(
        if (source == PremiumSource.BOTH) "Both" else source.label,
        Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
        color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberSheet(member: PremiumMember, busy: Boolean, viewModel: AdminSubscribersViewModel) {
    val colors = LocalSpeakMindColors.current
    val lapsed = member.source == PremiumSource.LAPSED
    ModalBottomSheet(
        onDismissRequest = viewModel::onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                UserAvatar(member.nickname, member.photoUrl, 56.dp, Color.Transparent, premium = !lapsed)
                Column(Modifier.weight(1f)) {
                    Text(member.nickname.ifEmpty { "Someone" }, style = displayStyle(22.sp), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    member.email?.let { Text(it, color = colors.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            val facts = buildList {
                add("Premium from" to member.source.label)
                if (member.isPlay || lapsed) member.since?.let { add("Paying since" to dateText(it)) }
                if (member.isPlay) add("Last confirmed by Play" to (member.playConfirmed?.let(::dayText) ?: "—"))
                if (lapsed) add("Ended" to (member.ended?.let(::dateText) ?: "—"))
                if (member.isGranted) {
                    add("Granted" to (member.grantedAt?.let(::dateText) ?: "—") + (member.grantedBy?.let { " · by $it" } ?: ""))
                    member.note?.let { add("Note" to it) }
                }
                member.level?.takeIf { it.isNotBlank() }?.let { add("Level" to it) }
                add("Last seen" to seenText(member.lastSeen).removePrefix("Seen "))
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.backgroundDark).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                facts.forEach { (k, v) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(k, color = colors.textMuted, fontSize = 14.sp, maxLines = 1)
                        Text(
                            v, Modifier.weight(1f), color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
            }
            Text(
                when {
                    member.isGranted -> "Removing ends granted Premium right away." + if (member.isPlay) " Their Google Play subscription stays." else ""
                    member.isPlay -> "Paid on Google Play. Refunds and cancellations are done in Play Console › Order management."
                    else -> "Their Google Play subscription ended or was cancelled."
                },
                color = colors.textMuted, fontSize = 12.sp, lineHeight = 18.sp,
            )
            when {
                member.isGranted -> SpeakyPrimaryButton(
                    if (busy) "Removing…" else "Remove Premium", { viewModel.onRevoke(member.id) },
                    background = Color(0xFFFDE2E2), color = Color(0xFFB42318), height = 52.dp, radius = 16.dp,
                )
                lapsed -> SpeakyPrimaryButton(
                    if (busy) "Granting…" else "Grant Premium", { viewModel.onGrant(member.id) },
                    background = Color(0xFF17142B), color = Color.White, height = 52.dp, radius = 16.dp,
                )
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrantSheet(ui: AdminSubscribersUiState, viewModel: AdminSubscribersViewModel) {
    val colors = LocalSpeakMindColors.current
    ModalBottomSheet(
        onDismissRequest = viewModel::onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp).heightIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Grant Premium", style = displayStyle(22.sp), color = colors.textPrimary)
            Text(
                "Free Premium with no Google Play payment, until you remove it. Good for testers, teachers and friends.",
                color = colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp,
            )
            SearchField(ui.grantQuery, "Search name or email", viewModel::onGrantQuery, background = colors.backgroundDark)
            when {
                ui.grantQuery.trim().length < 2 -> Text("Type at least 2 letters.", color = colors.textMuted, fontSize = 13.sp)
                ui.searching && ui.candidates.isEmpty() -> Text("Searching…", color = colors.textMuted, fontSize = 13.sp)
                ui.candidates.isEmpty() -> Text("Nobody found.", color = colors.textMuted, fontSize = 13.sp)
            }
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ui.candidates, key = { it.id }) { person ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UserAvatar(person.nickname, person.photoUrl, 42.dp, Color.Transparent, premium = person.premium)
                        Column(Modifier.weight(1f)) {
                            Text(person.nickname.ifEmpty { "Someone" }, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(person.email, "Pays on Play".takeIf { person.premium }).joinToString(" · "),
                                color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            if (ui.busyId == person.id) "…" else "Grant",
                            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF17142B))
                                .clickable(enabled = ui.busyId == null) { viewModel.onGrant(person.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun dateText(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${d.dayOfMonth} ${MONTHS[d.monthNumber - 1]} ${d.year}"
}

private fun dayText(millis: Long): String {
    val days = (Clock.System.now().toEpochMilliseconds() - millis) / 86_400_000L
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        else -> "$days days ago"
    }
}

private fun seenText(millis: Long?): String {
    if (millis == null) return "Never seen"
    val minutes = (Clock.System.now().toEpochMilliseconds() - millis) / 60_000L
    return when {
        minutes < 3 -> "Online now"
        minutes < 60 -> "Seen $minutes min ago"
        minutes < 24 * 60 -> "Seen ${minutes / 60} h ago"
        minutes < 48 * 60 -> "Seen yesterday"
        else -> "Seen ${minutes / (24 * 60)} days ago"
    }
}
