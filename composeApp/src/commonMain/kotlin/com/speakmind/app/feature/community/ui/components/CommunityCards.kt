package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp

/** Page header matching the app's inner screens (back arrow + bold title). */
@Composable
fun CommunityPageHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.sdp, bottom = 8.sdp)
            .padding(horizontal = 4.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted))
            }
        }
        actions()
    }
}

/** Card in the style of the Home "Study Hub" boxes. */
@Composable
fun CommunityCard(
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.05f), LocalSpeakMindColors.current.surfaceVariant.copy(alpha = 0.7f))
                )
            )
            .border(1.sdp, accent.copy(alpha = 0.2f), RoundedCornerShape(16.sdp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp, vertical = 14.sdp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Navigation card: icon tile, title, subtitle, optional badge, chevron. */
@Composable
fun CommunityNavCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
) {
    val colors = LocalSpeakMindColors.current
    CommunityCard(accent = accent, onClick = onClick, modifier = modifier) {
        IconTile(icon, accent)
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
            Text(
                subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
            )
        }
        if (badge > 0) {
            UnreadBadge(badge, accent)
            Spacer(Modifier.width(6.sdp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = accent.copy(alpha = 0.5f),
            modifier = Modifier.size(20.sdp),
        )
    }
}

@Composable
fun IconTile(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(44.sdp)
            .clip(RoundedCornerShape(12.sdp))
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.sdp))
    }
}

@Composable
fun UnreadBadge(count: Int, accent: Color) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 20.sdp, minHeight = 20.sdp)
            .background(accent, CircleShape)
            .padding(horizontal = 6.sdp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                color = LocalSpeakMindColors.current.backgroundDark,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** A person or private chat as a card: avatar with online dot, name, subtitle, trailing info. */
@Composable
fun PersonCard(
    user: CommunityUser,
    online: Boolean,
    subtitle: String,
    onClick: () -> Unit,
    subtitleColor: Color? = null,
    trailingTime: String = "",
    unread: Int = 0,
) {
    val colors = LocalSpeakMindColors.current
    CommunityCard(accent = colors.neonCyan, onClick = onClick) {
        Box {
            UserAvatar(nickname = user.nickname, photoUrl = user.photoUrl, size = 44.sdp, borderColor = colors.neonCyan)
            OnlineDot(online = online, size = 13.sdp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(
                user.nickname,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            )
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = subtitleColor ?: colors.textMuted,
                    fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.sdp)) {
            if (trailingTime.isNotEmpty()) {
                Text(trailingTime, style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted, fontSize = 9.ssp))
            }
            if (unread > 0) UnreadBadge(unread, colors.neonCyan)
        }
    }
}

@Composable
fun CommunitySearchField(value: String, placeholder: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(1.sdp, colors.neonCyan.copy(alpha = 0.2f), RoundedCornerShape(16.sdp))
            .padding(horizontal = 14.sdp, vertical = 12.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.sdp))
        Spacer(Modifier.width(8.sdp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted))
                inner()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun CommunityEmptyState(emoji: String, title: String, message: String) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.sdp, vertical = 48.sdp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 40.ssp)
        Spacer(Modifier.height(10.sdp))
        Text(title, style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 4.sdp),
        )
    }
}

@Composable
fun CommunityLoading() {
    Box(Modifier.fillMaxWidth().padding(32.sdp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalSpeakMindColors.current.neonCyan, modifier = Modifier.size(28.sdp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(color = LocalSpeakMindColors.current.textPrimary, fontWeight = FontWeight.Bold),
        modifier = modifier,
    )
}
