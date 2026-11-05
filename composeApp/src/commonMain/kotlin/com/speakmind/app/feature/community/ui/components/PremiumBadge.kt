package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.feature.community.data.PremiumMembers
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.theme.LocalSpeakMindColors

/** Gold of the Premium badge, light to deep, for rings and pills. */
private val GoldLight = Color(0xFFFFE27A)
private val GoldMid = Color(0xFFF5B301)
private val GoldDeep = Color(0xFFE3A80C)
/** Icon/text on gold: dark enough to read in both themes. */
private val OnGold = Color(0xFF4A2F00)

/** Metallic ring around a Premium member's photo. */
val PremiumRingBrush: Brush = Brush.sweepGradient(listOf(GoldLight, GoldMid, GoldDeep, GoldMid, GoldLight))

/** Gold fill for badges and pills. */
val PremiumFillBrush: Brush = Brush.linearGradient(listOf(GoldLight, GoldDeep))

/** Whether [userId] has Speaky Premium (updates as users load). */
@Composable
fun isPremiumMember(userId: String?): Boolean {
    val ids by PremiumMembers.ids.collectAsState()
    return userId != null && userId in ids
}

/** Gold gradient text for a Premium member's name; lighter golds on dark backgrounds. */
@Composable
fun premiumNameStyle(fontSize: TextUnit, fontWeight: FontWeight = FontWeight.ExtraBold): TextStyle {
    val dark = LocalSpeakMindColors.current.surface.luminance() < 0.5f
    val brush = if (dark) Brush.linearGradient(listOf(Color(0xFFF5D27A), Color(0xFFFFE9A8), Color(0xFFE3A80C)))
    else Brush.linearGradient(listOf(Color(0xFFB7791F), Color(0xFFE3A80C), Color(0xFF9A6200)))
    return TextStyle(brush = brush, fontSize = fontSize, fontWeight = fontWeight)
}

/** Solid gold used for Premium names where a gradient would be too busy (sender labels, headers). */
@Composable
fun premiumNameColor(): Color =
    if (LocalSpeakMindColors.current.surface.luminance() < 0.5f) Color(0xFFF5D27A) else Color(0xFF9A6200)

/** Rounded gold tile with a crown next to a Premium member's name. */
@Composable
fun PremiumNameBadge(size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).background(PremiumFillBrush, RoundedCornerShape(size / 3)),
    ) {
        Icon(SpeakyIcons.Crown, contentDescription = "Premium", tint = Color(0xFF5A3A00), modifier = Modifier.size(size * 0.61f))
    }
}

/** Bare gold crown (Hub sender names). */
@Composable
fun PremiumCrown(size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Icon(SpeakyIcons.Crown, contentDescription = "Premium", tint = GoldMid, modifier = modifier.size(size))
}

/** "PREMIUM" pill for profiles, chat headers and the account menu. */
@Composable
fun PremiumPill(modifier: Modifier = Modifier, text: String = "Premium", crown: Boolean = true, fontSize: TextUnit = 12.sp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(GoldMid, RoundedCornerShape(999.dp))
            .padding(horizontal = if (crown) 10.dp else 7.dp, vertical = if (crown) 4.dp else 2.dp),
    ) {
        if (crown) Icon(SpeakyIcons.Crown, contentDescription = null, tint = OnGold, modifier = Modifier.size(12.dp))
        Text(text.uppercase(), color = OnGold, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
    }
}

/** Crown medal on the corner of a Premium member's photo (big enough avatars only). */
@Composable
internal fun PremiumAvatarMedal(avatarSize: Dp, modifier: Modifier = Modifier) {
    val size = (avatarSize * 0.3f).coerceIn(16.dp, 30.dp)
    // Edged in the surface colour so it reads as cut out of the photo.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(GoldMid, CircleShape)
            .border(if (size > 24.dp) 3.dp else 2.dp, LocalSpeakMindColors.current.surface, CircleShape),
    ) {
        Icon(SpeakyIcons.Crown, contentDescription = "Premium", tint = OnGold, modifier = Modifier.size(size * 0.55f))
    }
}
