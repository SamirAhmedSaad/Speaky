package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors

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
    /** Admins only: make or remove this person as a moderator. */
    isModerator: Boolean = false,
    onModeratorToggle: (() -> Unit)? = null,
    /** Admins only: remove this person's photo and ask them for a modest one. */
    onRemovePhoto: (() -> Unit)? = null,
    /** Admins only: ban this person directly (no report needed). */
    onBan: (() -> Unit)? = null,
    /** Admins only: this person is banned; shows Unban instead of Ban. */
    isBanned: Boolean = false,
    onUnban: (() -> Unit)? = null,
) {
    val colors = LocalSpeakMindColors.current
    val premium = isPremiumMember(user.uid)
    val dark = colors.surface.luminance() < 0.5f
    // Premium: warm gold banner; everyone else: the soft brand violet.
    val banner = if (premium) (if (dark) colors.goldSoft else Color(0xFFFFF1C9)) else colors.primarySoft
    val blobA = if (premium) Color(0xFFF5D27A).copy(alpha = 0.6f) else colors.neonCyan.copy(alpha = 0.18f)
    val blobB = if (premium) Color(0xFFF5B301).copy(alpha = 0.4f) else colors.coral.copy(alpha = 0.22f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        scrimColor = Color(0x7317142B),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth().height(110.dp).background(banner)) {
            Box(Modifier.offset(x = 20.dp, y = 18.dp).size(70.dp).background(blobA, CircleShape))
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 30.dp).size(40.dp).background(blobB, CircleShape))
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (premium) Color(0xFFE0C27A) else colors.neonCyan.copy(alpha = 0.25f)),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Pulled up 54dp over the banner, giving the space back to the sheet.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val lift = 54.dp.roundToPx()
                    layout(placeable.width, placeable.height - lift) { placeable.place(0, -lift) }
                }
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                // 4dp surface gap, then a 4dp gold ring for Premium members.
                Box(
                    Modifier.size(116.dp).background(if (premium) Color(0xFFF5B301) else colors.surface, CircleShape)
                        .padding(4.dp).background(colors.surface, CircleShape).padding(4.dp),
                ) {
                    UserAvatar(nickname = user.nickname, photoUrl = user.photoUrl, size = 100.dp, borderColor = Color.Transparent)
                }
                OnlineDot(online = online, size = 18.dp, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-8).dp))
                if (premium) PremiumAvatarMedal(100.dp, Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp))
            }
            Text(
                user.nickname,
                style = displayStyle(26.sp),
                color = if (premium) premiumNameColor() else colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (premium) PremiumPill()
                if (user.level.isNotEmpty()) {
                    Text(
                        user.level,
                        color = colors.neonCyanDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.primarySoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (isModerator) {
                    Text(
                        "Moderator",
                        color = colors.onMintSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.mintSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                // Premium members: the pill already says what they are, so no plain "Learner".
                if (isBlocked || online || !premium) {
                    Text(
                        when {
                            isBlocked -> "Blocked"
                            online -> "Online now"
                            else -> "Learner"
                        },
                        color = when {
                            isBlocked -> colors.error
                            online -> colors.success
                            else -> colors.textMuted
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (onMessage != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.userBubbleGradient)
                        .clickable(role = Role.Button, onClick = onMessage),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SpeakyIcons.Chat, contentDescription = null, tint = colors.onBubble, modifier = Modifier.size(20.dp))
                    Text("Send message", color = colors.onBubble, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (onReport != null || onBlockToggle != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (onReport != null) {
                        SheetAction("Report", colors.goldSoft, colors.onGoldSoft, onReport, Modifier.weight(1f))
                    }
                    if (onBlockToggle != null) {
                        SheetAction(
                            if (isBlocked) "Unblock" else "Block",
                            if (isBlocked) colors.surfaceVariant else colors.error.copy(alpha = 0.12f),
                            if (isBlocked) colors.textPrimary else colors.error,
                            onBlockToggle,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            if (isBanned && onUnban != null) {
                SheetAction("Unban user", colors.mintSoft, colors.onMintSoft, onUnban, Modifier.fillMaxWidth())
            } else if (onBan != null) {
                SheetAction("Ban user", colors.error.copy(alpha = 0.12f), colors.error, onBan, Modifier.fillMaxWidth())
            }
            if (onRemovePhoto != null) {
                SheetAction(
                    "Remove photo & ask to change",
                    colors.error.copy(alpha = 0.12f),
                    colors.error,
                    onRemovePhoto,
                    Modifier.fillMaxWidth(),
                )
            }
            if (onModeratorToggle != null) {
                SheetAction(
                    if (isModerator) "Remove moderator" else "Make moderator",
                    colors.mintSoft,
                    colors.onMintSoft,
                    onModeratorToggle,
                    Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(28.dp))
    }
}

@Composable
private fun SheetAction(label: String, background: Color, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
