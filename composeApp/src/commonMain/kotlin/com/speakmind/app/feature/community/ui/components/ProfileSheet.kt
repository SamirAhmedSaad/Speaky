package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp

/** Bottom sheet with a learner's profile and actions. Pass null for actions that shouldn't show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    user: CommunityUser,
    online: Boolean,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onMessage: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onBlockToggle: (() -> Unit)?,
) {
    val colors = LocalSpeakMindColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp)
                .padding(bottom = 28.sdp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Box(
                    Modifier
                        .size(96.sdp)
                        .clip(RoundedCornerShape(48.sdp))
                        .background(Brush.linearGradient(listOf(colors.neonCyan, colors.magenta))),
                    contentAlignment = Alignment.Center,
                ) {
                    UserAvatar(nickname = user.nickname, photoUrl = user.photoUrl, size = 90.sdp, borderColor = Color.Transparent)
                }
                OnlineDot(online = online, size = 20.sdp, modifier = Modifier.align(Alignment.BottomEnd).padding(4.sdp))
            }
            Spacer(Modifier.height(12.sdp))
            Text(
                user.nickname,
                style = MaterialTheme.typography.headlineSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.sdp),
                modifier = Modifier.padding(top = 6.sdp),
            ) {
                if (user.level.isNotEmpty()) {
                    Text(
                        user.level,
                        style = MaterialTheme.typography.labelMedium.copy(color = colors.gold, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.sdp))
                            .background(colors.gold.copy(alpha = 0.14f))
                            .padding(horizontal = 10.sdp, vertical = 3.sdp),
                    )
                }
                Text(
                    when {
                        isBlocked -> "Blocked"
                        online -> "Online now"
                        user.lastSeen > 0 -> formatLastSeen(user.lastSeen)
                        else -> "Learner"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = when {
                            isBlocked -> colors.error
                            online -> colors.success
                            else -> colors.textMuted
                        },
                    ),
                )
            }
            Spacer(Modifier.height(22.sdp))
            if (onMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.sdp)
                        .clip(RoundedCornerShape(24.sdp))
                        .background(Brush.horizontalGradient(listOf(colors.neonCyan, colors.neonCyanDark)))
                        .clickable(onClick = onMessage),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = colors.backgroundDark, modifier = Modifier.size(20.sdp))
                    Spacer(Modifier.width(8.sdp))
                    Text("Send message", color = colors.backgroundDark, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.sdp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.sdp), modifier = Modifier.fillMaxWidth()) {
                if (onReport != null) {
                    SheetAction("Report", Icons.Default.Flag, colors.warning, onReport, Modifier.weight(1f))
                }
                if (onBlockToggle != null) {
                    SheetAction(if (isBlocked) "Unblock" else "Block", Icons.Default.Block, colors.error, onBlockToggle, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(44.sdp)
            .clip(RoundedCornerShape(14.sdp))
            .background(tint.copy(alpha = 0.08f))
            .border(1.sdp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.sdp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.sdp))
        Spacer(Modifier.width(6.sdp))
        Text(label, color = tint, fontWeight = FontWeight.SemiBold)
    }
}
