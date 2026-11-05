package com.speakmind.app.feature.community.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object AdminCallsDestination

data class AdminCallsUiState(
    val loaded: Boolean = false,
    val bans: List<CallBan> = emptyList(),
    val stats: CallStats? = null,
    val asking: Boolean = false,
)

class AdminCallsViewModel(
    private val repository: HubCallRepository,
    private val manager: HubCallManager,
    private val navigationManager: NavigationManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminCallsUiState())
    val uiState: StateFlow<AdminCallsUiState> = _uiState.asStateFlow()
    val call = manager.state

    init {
        manager.watch(com.speakmind.app.feature.community.data.model.SPEAKY_HUB_ROOM_ID)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val bans = repository.bans()
            val stats = repository.stats()
            _uiState.update { it.copy(loaded = true, bans = bans, stats = stats) }
        }
    }

    fun onToggle() {
        val state = manager.state.value
        when {
            !state.enabled -> setEnabled(true, endLive = false)
            state.live != null -> _uiState.update { it.copy(asking = true) }
            else -> setEnabled(false, endLive = false)
        }
    }

    /** Turning calls off while one is live: [endLive] ends it now, otherwise it finishes on its own. */
    fun setEnabled(on: Boolean, endLive: Boolean) {
        _uiState.update { it.copy(asking = false) }
        manager.showEnabled(on)
        viewModelScope.launch {
            repository.setEnabled(on, endLive)
            manager.refresh()
        }
    }

    fun onDismissAsk() = _uiState.update { it.copy(asking = false) }

    fun onEndNow() = manager.adminEnd()

    fun onUnban(userId: String) {
        viewModelScope.launch {
            repository.adminUnban(userId)
            load()
        }
    }

    fun onBack() = navigationManager.back()
}

fun NavGraphBuilder.adminCallsScreen() {
    animatedComposable<AdminCallsDestination> {
        val viewModel = koinViewModel<AdminCallsViewModel>()
        val ui by viewModel.uiState.collectAsStateWithLifecycle()
        val call by viewModel.call.collectAsStateWithLifecycle()
        AdminCallsContent(ui, call, viewModel)
    }
}

@Composable
private fun AdminCallsContent(ui: AdminCallsUiState, call: HubCallState, viewModel: AdminCallsViewModel) {
    val colors = LocalSpeakMindColors.current
    Column(
        Modifier.fillMaxSize().background(colors.backgroundDark).windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CommunityPageHeader(title = "Voice calls", subtitle = "Admins only", onBack = viewModel::onBack)
        if (!ui.loaded || !call.loaded) {
            Text("Loading…", Modifier.padding(horizontal = 20.dp), color = colors.textMuted, fontSize = 15.sp)
            return@Column
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(colors.mintSoft), contentAlignment = Alignment.Center) {
                        Icon(SpeakyIcons.Phone, null, tint = colors.onMintSoft, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Hub voice calls", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                call.enabled && call.live != null -> "On · a call is live"
                                call.enabled -> "On · anyone in the Hub can start one"
                                call.live != null -> "Off · last call finishing"
                                else -> "Off · the call button is hidden"
                            },
                            color = colors.textMuted, fontSize = 13.sp,
                        )
                    }
                    Switch(call.enabled, viewModel::onToggle)
                }
                Text(
                    "When off, the call button disappears from the Hub for everyone, and nobody can start or join a call.",
                    color = colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                )
            }

            call.live?.let { live ->
                Card(border = Color(0xFFA7EBD0)) {
                    Text("Live now · ${live.members.size} in the call", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(live.members.joinToString { it.nickname }, color = colors.textMuted, fontSize = 13.sp, maxLines = 2)
                    SpeakyPrimaryButton("End call now", viewModel::onEndNow, background = Color(0xFFFDE2E2), color = Color(0xFFB42318), height = 46.dp, radius = 14.dp)
                }
            }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(SpeakyIcons.Ban, null, tint = Color(0xFFB42318), modifier = Modifier.size(18.dp))
                    Text("Banned from calls · ${ui.bans.size}", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (ui.bans.isEmpty()) Text("Nobody is banned. Long-press someone in a call to ban them.", color = colors.textMuted, fontSize = 13.sp)
                ui.bans.forEach { ban ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UserAvatar(ban.nickname, null, 36.dp, Color.Transparent)
                        Column(Modifier.weight(1f)) {
                            Text(ban.nickname.ifEmpty { "Someone" }, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(untilText(ban.untilMillis), color = colors.textMuted, fontSize = 12.sp)
                        }
                        Text(
                            "Unban",
                            Modifier.clip(RoundedCornerShape(12.dp)).background(colors.backgroundDark)
                                .clickable { viewModel.onUnban(ban.userId) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            ui.stats?.let { stats ->
                Card {
                    Text("This month", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    StatRow("Calls", stats.calls.toString())
                    StatRow("Minutes used (Agora)", "${stats.agoraMinutes} / 10,000 free")
                    StatRow("Minutes used (LiveKit)", stats.liveKitMinutes.toString())
                    StatRow("Calls that used LiveKit", stats.fallbackCalls.toString())
                }
            }
        }
    }
    if (ui.asking) TurnOffSheet(onFinish = { viewModel.setEnabled(false, endLive = false) }, onEndNow = { viewModel.setEnabled(false, endLive = true) }, onDismiss = viewModel::onDismissAsk)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TurnOffSheet(onFinish: () -> Unit, onEndNow: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Turn off voice calls?", style = displayStyle(22.sp), color = colors.textPrimary)
            Text("A call is live right now. What should happen to it?", color = colors.textSecondary, fontSize = 14.sp)
            Choice("Let it finish (recommended)", "No new calls can start. The current call keeps going until people leave.", onFinish)
            Choice("End it now for everyone", "Everyone in the call sees “This call was ended by an admin”.", onEndNow)
        }
    }
}

@Composable
private fun Choice(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.backgroundDark).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = colors.textMuted, fontSize = 13.sp)
    }
}

@Composable
private fun Card(border: Color = Color.Transparent, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSpeakMindColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface)
            .border(1.5.dp, border, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    val colors = LocalSpeakMindColors.current
    Row {
        Text(label, Modifier.weight(1f), color = colors.textMuted, fontSize = 14.sp)
        Text(value, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Switch(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(999.dp)).background(if (on) Color(0xFF19B88A) else Color(0xFFCFC9E6))
            .clickable(role = Role.Switch, onClick = onClick).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White))
    }
}

private fun untilText(until: Long?): String {
    if (until == null) return "Forever"
    val days = ((until - Clock.System.now().toEpochMilliseconds()) / 86_400_000L) + 1
    return "$days day${if (days == 1L) "" else "s"} left"
}
