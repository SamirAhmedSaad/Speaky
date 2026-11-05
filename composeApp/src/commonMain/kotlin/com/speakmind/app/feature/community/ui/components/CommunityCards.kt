package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.feature.community.data.model.CommunityUser
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors

/** Inner-screen header: round back button, big display title and a small subtitle. */
@Composable
fun CommunityPageHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    titleSize: Int = 24,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpeakyBackButton(onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = displayStyle(titleSize.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = subtitleColor ?: colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = if (subtitleColor != null) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        actions()
    }
}

/** Violet count bubble (unread messages). */
@Composable
fun UnreadBadge(
    count: Int,
    accent: Color = LocalSpeakMindColors.current.neonCyan,
    contentColor: Color = LocalSpeakMindColors.current.let { if (accent == it.neonCyan) it.onPrimary else Color.White },
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 22.dp)
            .height(22.dp)
            .background(accent, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/**
 * A person or private chat as a borderless row: avatar with online dot, name (gold for Premium),
 * subtitle, and time / unread count or a chevron on the right. [background] is what the row sits
 * on, so the online dot's ring matches it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonCard(
    user: CommunityUser,
    online: Boolean,
    subtitle: String,
    onClick: () -> Unit,
    subtitleColor: Color? = null,
    trailingTime: String = "",
    unread: Int = 0,
    modifier: Modifier = Modifier,
    background: Color = LocalSpeakMindColors.current.surface,
    avatarSize: Dp = 54.dp,
    subtitleSize: Int = 14,
    chevron: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalSpeakMindColors.current
    val premium = isPremiumMember(user.uid)
    val bold = unread > 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            UserAvatar(nickname = user.nickname, photoUrl = user.photoUrl, size = avatarSize, borderColor = colors.neonCyan, premium = premium)
            OnlineDot(
                online = online,
                size = 13.dp,
                ringColor = background,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    user.nickname,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (premium) premiumNameStyle(16.sp, if (bold || chevron) FontWeight.ExtraBold else FontWeight.Bold)
                    else TextStyle(color = colors.textPrimary, fontSize = 16.sp, fontWeight = if (bold || chevron) FontWeight.ExtraBold else FontWeight.Bold),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (premium) PremiumNameBadge(size = 18.dp)
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = subtitleColor ?: if (bold) colors.textPrimary else colors.textMuted,
                    fontSize = subtitleSize.sp,
                    fontWeight = when {
                        subtitleColor != null && subtitleColor != colors.textPrimary -> FontWeight.Bold
                        bold -> FontWeight.SemiBold
                        else -> FontWeight.Normal
                    },
                )
            }
        }
        if (chevron) {
            Icon(SpeakyIcons.Chevron, contentDescription = null, tint = colors.faint.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        } else if (trailingTime.isNotEmpty() || unread > 0) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trailingTime.isNotEmpty()) {
                    Text(
                        trailingTime,
                        color = if (bold) colors.neonCyan else colors.faint,
                        fontSize = 12.sp,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (unread > 0) UnreadBadge(unread)
            }
        }
    }
}

/** Search box; [background] is the soft grey on the page or white on the People screen. */
@Composable
fun CommunitySearchField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    radius: Dp = 16.dp,
    background: Color = LocalSpeakMindColors.current.surface,
) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SpeakyIcons.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(colors.neonCyan),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = colors.faint, fontSize = 15.sp)
                    inner()
                }
            },
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Icon(
                SpeakyIcons.Close,
                contentDescription = "Clear search",
                tint = colors.textMuted,
                modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onChange("") }.padding(6.dp),
            )
        }
    }
}

@Composable
fun CommunityEmptyState(emoji: String, title: String, message: String) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 36.sp) }
        Text(title, style = displayStyle(22.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(
            message,
            color = colors.textSecondary,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CommunityLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalSpeakMindColors.current.neonCyan, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** Small uppercase label above a list ("PRIVATE MESSAGES"). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = LocalSpeakMindColors.current.faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier,
    )
}

/** Filter chip: dark when selected, white otherwise (People, Reports). */
@Composable
fun CommunityChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.textPrimary else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) colors.backgroundDark else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Bottom snackbar-style message (errors red, info dark). */
@Composable
fun CommunityToast(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Text(
        text,
        color = if (isError) Color.White else colors.backgroundDark,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isError) DangerRed else colors.textPrimary)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    )
}

/** The design's destructive red (#E5484D) for delete / ban / report buttons. */
val DangerRed = Color(0xFFE5484D)

@Composable
fun GoogleSignInPill(label: String, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.5.dp, colors.textMuted.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable(enabled = !loading, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFF4285F4), strokeWidth = 2.dp)
        } else {
            Text("G  ", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4))
            Text(label, color = Color(0xFF1F1F1F), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
