package com.speakmind.app.feature.community.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.data.model.AdminReport
import com.speakmind.app.feature.community.ui.components.CommunityEmptyState
import com.speakmind.app.feature.community.ui.components.CommunityLoading
import com.speakmind.app.feature.community.ui.components.CommunityPageHeader
import com.speakmind.app.feature.community.ui.components.UserAvatar
import com.speakmind.app.feature.community.ui.components.formatClock
import com.speakmind.app.feature.community.ui.components.formatListTime
import com.speakmind.app.navigation.AdminReportsDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.adminReportsScreen() {
    animatedComposable<AdminReportsDestination> {
        val viewModel = koinViewModel<AdminReportsViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        AdminReportsContent(uiState, viewModel)
    }
}

@Composable
private fun AdminReportsContent(uiState: AdminReportsUiState, viewModel: AdminReportsViewModel) {
    val colors = LocalSpeakMindColors.current
    LaunchedEffect(uiState.info, uiState.error) {
        if (uiState.info != null || uiState.error != null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.onMessageDismissed()
        }
    }
    Box(Modifier.fillMaxSize().background(colors.backgroundGradient)) {
        Column(Modifier.fillMaxSize()) {
            CommunityPageHeader(title = "Reports", subtitle = "Community moderation", onBack = viewModel::onBack)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.sdp, vertical = 6.sdp),
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
            ) {
                items(REPORT_FILTERS) { (value, label) ->
                    val selected = uiState.status == value
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (selected) colors.backgroundDark else colors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.sdp))
                            .background(if (selected) colors.warning else colors.surfaceVariant.copy(alpha = 0.7f))
                            .clickable { viewModel.onStatusSelected(value) }
                            .padding(horizontal = 14.sdp, vertical = 6.sdp),
                    )
                }
            }
            if (uiState.status == MODERATORS_TAB) {
                ModeratorsList(uiState, viewModel)
            } else when {
                uiState.isLoading && uiState.reports.isEmpty() -> CommunityLoading()
                uiState.reports.isEmpty() -> CommunityEmptyState("🛡️", "All clear", "No reports here.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.sdp, end = 20.sdp, top = 8.sdp, bottom = 32.sdp),
                    verticalArrangement = Arrangement.spacedBy(12.sdp),
                ) {
                    items(uiState.reports, key = { it.id }) { report ->
                        ReportCard(report, viewModel)
                    }
                }
            }
        }
        (uiState.error ?: uiState.info)?.let { msg ->
            Text(
                msg,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.sdp)
                    .clip(RoundedCornerShape(12.sdp))
                    .background(if (uiState.error != null) Color(0xFFB71C1C) else colors.neonCyanDark)
                    .padding(horizontal = 14.sdp, vertical = 10.sdp),
            )
        }
    }

    uiState.banTarget?.let { target ->
        var removeMessages by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::onBanCancelled,
            title = { Text("Ban ${target.reportedNickname}?") },
            text = {
                Column {
                    Text("They won't be able to post or send private messages.")
                    Row(
                        Modifier.fillMaxWidth().clickable { removeMessages = !removeMessages }.padding(top = 8.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = removeMessages, onCheckedChange = { removeMessages = it })
                        Text("Also remove all their messages")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmBan(removeMessages) }) {
                    Text("Ban", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::onBanCancelled) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReportCard(report: AdminReport, viewModel: AdminReportsViewModel) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(1.sdp, colors.warning.copy(alpha = 0.25f), RoundedCornerShape(16.sdp))
            .padding(14.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                report.reason.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium.copy(color = colors.warning, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.sdp))
                    .background(colors.warning.copy(alpha = 0.12f))
                    .padding(horizontal = 8.sdp, vertical = 2.sdp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${formatListTime(report.createdAtMillis)} ${formatClock(report.createdAtMillis)}",
                style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
            )
        }
        Text(
            "${report.reporterNickname} reported ${report.reportedNickname}",
            style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 8.sdp),
        )
        Text(
            "Reported ${report.timesReported} time${if (report.timesReported != 1) "s" else ""}" +
                if (report.reportedBanned) " · BANNED" else "",
            style = MaterialTheme.typography.labelSmall.copy(color = if (report.reportedBanned) colors.error else colors.textMuted),
        )
        if (report.messageSnapshot.isNotBlank()) {
            Text(
                "“${report.messageSnapshot}”",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textSecondary, fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(top = 8.sdp),
            )
        }
        if (report.details.isNotBlank()) {
            Text(
                "Note: ${report.details}",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 4.sdp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.sdp), modifier = Modifier.padding(top = 12.sdp)) {
            if (report.reportedBanned) {
                AdminButton("Unban", colors.success) { viewModel.onUnban(report) }
            } else {
                AdminButton("Ban user", colors.error) { viewModel.onBanRequested(report) }
            }
            if (report.status == "open") {
                AdminButton("Dismiss", colors.textMuted) { viewModel.onDismissReport(report) }
            }
        }
    }
}

@Composable
private fun ModeratorsList(uiState: AdminReportsUiState, viewModel: AdminReportsViewModel) {
    val colors = LocalSpeakMindColors.current
    when {
        uiState.isLoading && uiState.moderators.isEmpty() -> CommunityLoading()
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 20.sdp, end = 20.sdp, top = 8.sdp, bottom = 32.sdp),
            verticalArrangement = Arrangement.spacedBy(10.sdp),
        ) {
            item {
                Text(
                    "To add a moderator, open People, tap the person and choose Make moderator.",
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted),
                    modifier = Modifier.padding(bottom = 4.sdp),
                )
            }
            items(uiState.moderators, key = { it.uid }) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.sdp))
                        .background(colors.surfaceVariant.copy(alpha = 0.7f))
                        .border(1.sdp, colors.gold.copy(alpha = 0.25f), RoundedCornerShape(16.sdp))
                        .padding(12.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(nickname = user.nickname, photoUrl = user.photoUrl, size = 40.sdp, borderColor = colors.gold)
                    Spacer(Modifier.width(12.sdp))
                    Text(
                        user.nickname,
                        style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    AdminButton("Remove", colors.error) { viewModel.onRemoveModerator(user) }
                }
            }
        }
    }
}

@Composable
private fun AdminButton(label: String, tint: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(color = tint, fontWeight = FontWeight.Bold),
        modifier = Modifier
            .clip(RoundedCornerShape(12.sdp))
            .background(tint.copy(alpha = 0.1f))
            .border(1.sdp, tint.copy(alpha = 0.3f), RoundedCornerShape(12.sdp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.sdp, vertical = 8.sdp),
    )
}
