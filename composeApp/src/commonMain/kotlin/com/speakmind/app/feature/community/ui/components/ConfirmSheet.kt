package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors

/** White bottom sheet (radius 28-30, grey handle) used by every chat dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalSpeakMindColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        scrimColor = Color(0x7317142B),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp).size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp)).background(colors.track),
            )
        },
        contentWindowInsets = { BottomSheetDefaults.windowInsets },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/**
 * Confirmation sheet from the design (Delete chat, Sign out, Block...): optional icon circle,
 * centred display title, body, a red (or violet) main button and a grey Cancel.
 */
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    destructive: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = LocalSpeakMindColors.current
    ChatSheet(onDismiss) {
        if (icon != null) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).size(56.dp).clip(CircleShape)
                    .background(if (destructive) colors.error.copy(alpha = 0.12f) else colors.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (destructive) colors.error else colors.neonCyan, modifier = Modifier.size(26.dp))
            }
        }
        Text(title, style = displayStyle(22.sp), color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            message,
            color = colors.textSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        extra?.invoke(this)
        SheetButton(confirmLabel, if (destructive) DangerRed else colors.neonCyan, if (destructive) Color.White else colors.onPrimary, 54, onConfirm)
        SheetButton("Cancel", colors.surfaceVariant, colors.textPrimary, 50, onDismiss)
    }
}

/** Full-width rounded (16) button used inside chat sheets. */
@Composable
fun SheetButton(
    label: String,
    background: Color,
    color: Color,
    height: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Checkbox row on a soft tray ("Also block Hunter"). */
@Composable
fun SheetCheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.backgroundDark)
            .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                .background(if (checked) colors.neonCyan else colors.surface)
                .then(if (checked) Modifier else Modifier.border(2.dp, colors.faint.copy(alpha = 0.6f), RoundedCornerShape(5.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(SpeakyIcons.Check, null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
        }
        Text(label, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}


/** Admins: confirm banning [name] directly, optionally removing all their messages. */
@Composable
fun AdminBanSheet(name: String, onConfirm: (removeMessages: Boolean) -> Unit, onDismiss: () -> Unit) {
    var removeMessages by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    ConfirmSheet(
        title = "Ban $name?",
        message = "They won't be able to post in the Hub or send private messages. You can unban them later from their profile.",
        confirmLabel = "Ban user",
        icon = com.speakmind.app.ui.components.SpeakyIcons.Shield,
        onConfirm = { onConfirm(removeMessages) },
        onDismiss = onDismiss,
        extra = { SheetCheckRow("Also remove all their messages", removeMessages) { removeMessages = !removeMessages } },
    )
}
