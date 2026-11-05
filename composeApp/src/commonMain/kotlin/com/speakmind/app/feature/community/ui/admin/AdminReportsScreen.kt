package com.speakmind.app.feature.community.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.feature.community.ui.components.CommunityChip
import com.speakmind.app.feature.community.ui.components.CommunityToast
import com.speakmind.app.feature.community.ui.components.ConfirmSheet
import com.speakmind.app.feature.community.ui.components.DangerRed
import com.speakmind.app.feature.community.ui.components.SheetButton
import com.speakmind.app.feature.community.ui.components.SheetCheckRow
import com.speakmind.app.ui.components.SpeakyIcons
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.adminReportsScreen() {
    animatedComposable<AdminReportsDestination> {
        val viewModel = koinViewModel<AdminReportsViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        AdminReportsContent(uiState, viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminReportsContent(uiState: AdminReportsUiState, viewModel: AdminReportsViewModel) {
    val colors = LocalSpeakMindColors.current
    LaunchedEffect(uiState.info, uiState.error) {
        if (uiState.info != null || uiState.error != null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.onMessageDismissed()
        }
    }
    Box(Modifier.fillMaxSize().background(colors.backgroundDark)) {
        Column(Modifier.fillMaxSize()) {
            CommunityPageHeader(title = "Reports", subtitle = "Community moderation", onBack = viewModel::onBack)
            Row(
                modifier = Modifier
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                REPORT_FILTERS.forEach { (value, label) ->
                    CommunityChip(label = label, selected = uiState.status == value, onClick = { viewModel.onStatusSelected(value) })
                }
            }
            if (uiState.status == MODERATORS_TAB) {
                ModeratorsList(uiState, viewModel)
            } else when {
                uiState.isLoading && uiState.reports.isEmpty() -> CommunityLoading()
                uiState.reports.isEmpty() -> CommunityEmptyState("🛡️", "All clear", "No reports here.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(uiState.reports, key = { _, report -> report.id }) { index, report ->
                        if (index >= uiState.reports.size - 5) LaunchedEffect(uiState.reports.size) { viewModel.loadMore() }
                        ReportCard(report, viewModel)
                    }
                    if (uiState.isLoadingMore) item(key = "more_reports") { CommunityLoading() }
                }
            }
        }
        (uiState.error ?: uiState.info)?.let { msg ->
            CommunityToast(msg, isError = uiState.error != null, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    uiState.banTarget?.let { target ->
        var removeMessages by remember { mutableStateOf(false) }
        ConfirmSheet(
            title = "Ban ${target.reportedNickname}?",
            message = "They won't be able to post or send private messages.",
            confirmLabel = "Ban user",
            icon = SpeakyIcons.Shield,
            onConfirm = { viewModel.onConfirmBan(removeMessages) },
            onDismiss = viewModel::onBanCancelled,
            extra = { SheetCheckRow("Also remove all their messages", removeMessages) { removeMessages = !removeMessages } },
        )
    }
}

@Composable
private fun ReportCard(report: AdminReport, viewModel: AdminReportsViewModel) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(reasonLabel(report.reason), colors.error.copy(alpha = 0.12f), colors.error, FontWeight.ExtraBold)
            if (report.timesReported > 1) {
                Pill("Reported ${report.timesReported} times", colors.goldSoft, colors.onGoldSoft, FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(
                // Today: just the time (formatListTime already gives it); older: date and time.
                formatListTime(report.createdAtMillis).let { day ->
                    val clock = formatClock(report.createdAtMillis)
                    if (day == clock) clock else "$day $clock"
                },
                color = colors.textMuted,
                fontSize = 12.sp,
            )
        }
        Text(
            "${report.reporterNickname} reported ${report.reportedNickname}",
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        if (report.reportedBanned) {
            Text("Banned", color = colors.error, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (report.messageSnapshot.isNotBlank()) {
            ReportSection("Reported message", "“${report.messageSnapshot}”", colors.backgroundDark, colors.textMuted)
        } else {
            ReportSection(null, "No message: they reported the person", colors.backgroundDark, colors.textMuted)
        }
        if (report.details.isNotBlank()) {
            ReportSection("Their note", report.details, colors.primarySoft, colors.onPrimarySoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (report.reportedBanned) {
                SheetButton("Unban", colors.mintSoft, colors.onMintSoft, 48, { viewModel.onUnban(report) }, Modifier.weight(1f))
            } else {
                SheetButton("Ban user", DangerRed, Color.White, 48, { viewModel.onBanRequested(report) }, Modifier.weight(1f))
            }
            if (report.status == "open") {
                SheetButton("Dismiss", colors.surfaceVariant, colors.textPrimary, 48, { viewModel.onDismissReport(report) }, Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, color: Color, weight: FontWeight) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = weight,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ModeratorsList(uiState: AdminReportsUiState, viewModel: AdminReportsViewModel) {
    val colors = LocalSpeakMindColors.current
    when {
        uiState.isLoading && uiState.moderators.isEmpty() -> CommunityLoading()
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "To add a moderator, open People, tap the person and choose Make moderator.",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.primarySoft)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            items(uiState.moderators, key = { it.uid }) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UserAvatar(nickname = user.nickname, photoUrl = user.photoUrl, size = 46.dp, borderColor = colors.gold)
                    Column(Modifier.weight(1f)) {
                        Text(user.nickname, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Moderator", color = colors.success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    SheetButton("Remove", colors.error.copy(alpha = 0.12f), colors.error, 40, { viewModel.onRemoveModerator(user) }, Modifier.width(96.dp))
                }
            }
        }
    }
}

/** The reason a user picked when reporting, in words (stored as "spam", "harassment"...). */
private fun reasonLabel(reason: String): String = when (reason) {
    "spam" -> "Spam"
    "harassment" -> "Harassment"
    "inappropriate" -> "Inappropriate"
    "scam" -> "Scam or fraud"
    "other" -> "Something else"
    else -> reason.replaceFirstChar { it.uppercase() }
}

/** A labelled tray in a report card ("Reported message", "Their note"). */
@Composable
private fun ReportSection(label: String?, text: String, background: Color, labelColor: Color) {
    val colors = LocalSpeakMindColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (label != null) Text(label, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = if (label != null) colors.textPrimary else colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
