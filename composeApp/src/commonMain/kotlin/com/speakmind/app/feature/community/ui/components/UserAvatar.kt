package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.abs

@Composable
fun UserAvatar(
    nickname: String,
    photoUrl: String?,
    size: Dp,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!photoUrl.isNullOrEmpty()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = nickname,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, borderColor.copy(alpha = 0.5f), CircleShape),
        )
    } else {
        val gradient = avatarGradientFor(nickname)
        Box(
            modifier = modifier
                .size(size)
                .background(Brush.radialGradient(gradient), CircleShape)
                .border(1.5.dp, borderColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontSize = (size.value * 0.38f).sp,
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
            .clip(CircleShape)
            .border(1.5.dp, borderColor.copy(alpha = 0.5f), CircleShape),
    )
}

fun avatarGradientFor(name: String): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF00E5FF), Color(0xFF0055A5)),
        listOf(Color(0xFFFF2D95), Color(0xFF7B2FFF)),
        listOf(Color(0xFF00E676), Color(0xFF00796B)),
        listOf(Color(0xFFFF7043), Color(0xFFE91E63)),
        listOf(Color(0xFFFFD740), Color(0xFFFF6D00)),
        listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF)),
        listOf(Color(0xFF00BCD4), Color(0xFF1565C0)),
        listOf(Color(0xFFEC407A), Color(0xFF7B1FA2)),
    )
    return palettes[abs(name.hashCode()) % palettes.size]
}
