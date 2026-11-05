package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Round photo, or the initial on a flat colour. [premium]: a gold ring with a gap and a soft glow,
 * drawn outside the circle so layouts don't shift; [medal] adds the crown medal on the corner.
 * [borderColor] is kept for callers; the redesign draws no ring on regular avatars.
 */
@Composable
fun UserAvatar(
    nickname: String,
    photoUrl: String?,
    size: Dp,
    borderColor: Color,
    modifier: Modifier = Modifier,
    premium: Boolean = false,
    medal: Boolean = false,
) {
    if (!premium) {
        AvatarCircle(nickname, photoUrl, size, modifier)
        return
    }
    Box(modifier = modifier.size(size)) {
        AvatarCircle(nickname, photoUrl, size, Modifier.premiumRing(size))
        if (medal && size >= 32.dp) {
            PremiumAvatarMedal(size, Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
        }
    }
}

/** Gold ring 2.5dp outside the photo (2dp wide) plus a faint gold glow, like the design's box-shadow. */
private fun Modifier.premiumRing(size: Dp): Modifier = drawBehind {
    val gap = 2.5.dp.toPx()
    val ring = 2.dp.toPx()
    val r = this.size.minDimension / 2
    drawCircle(
        brush = Brush.radialGradient(
            listOf(PremiumGlow, Color.Transparent),
            center = center.copy(y = center.y + 6.dp.toPx()),
            radius = r + 14.dp.toPx(),
        ),
        radius = r + 14.dp.toPx(),
        center = center.copy(y = center.y + 6.dp.toPx()),
    )
    drawCircle(PremiumRingColor, radius = r + gap + ring / 2, style = Stroke(ring))
}

private val PremiumRingColor = Color(0xFFE3A80C)
private val PremiumGlow = Color(0x55E3A80C)

@Composable
private fun AvatarCircle(nickname: String, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    // A picked fun avatar, or (no photo at all) the fun avatar that goes with the name.
    var letterImage by androidx.compose.runtime.remember(photoUrl) {
        androidx.compose.runtime.mutableStateOf(photoUrl?.let { LetterImageCache.results[it] } ?: false)
    }
    val funKey = FunAvatars.keyOf(photoUrl)
        ?: if (photoUrl.isNullOrEmpty() || letterImage) FunAvatars.fallbackFor(nickname) else null
    val funRes = funKey?.let { FunAvatars.resource(it) }
    if (funRes != null) {
        androidx.compose.foundation.Image(
            painter = org.jetbrains.compose.resources.painterResource(funRes),
            contentDescription = nickname,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    if (!photoUrl.isNullOrEmpty()) {
        Box(modifier.size(size)) {
            AsyncImage(
                model = photoUrl,
                contentDescription = nickname,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
                onSuccess = { success ->
                    val plain = LetterImageCache.results.getOrPut(photoUrl) { isPlainLetterImage(success.result.image) }
                    if (plain) letterImage = true
                },
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(avatarColorFor(nickname), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontSize = (size.value * 0.37f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun UserAvatarFromBytes(
    bytes: ByteArray,
    size: Dp,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = bytes,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}

/** The design's flat avatar colours; the same name always gets the same one. */
private val AvatarColors = listOf(
    Color(0xFF5B3DF5),
    Color(0xFFE0408A),
    Color(0xFFFF6B4A),
    Color(0xFF19B88A),
    Color(0xFF3B82F6),
    Color(0xFF9C7BD3),
    Color(0xFFD97706),
    Color(0xFF0E9DB0),
)

fun avatarColorFor(name: String): Color = AvatarColors[abs(name.hashCode()) % AvatarColors.size]

/** Kept for callers that paint their own circle (a flat colour as a two-stop gradient). */
fun avatarGradientFor(name: String): List<Color> = avatarColorFor(name).let { listOf(it, it) }
