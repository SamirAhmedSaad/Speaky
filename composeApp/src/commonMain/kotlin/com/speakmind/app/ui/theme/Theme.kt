package com.speakmind.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import speakmind.composeapp.generated.resources.Res
import speakmind.composeapp.generated.resources.roboto_bold
import speakmind.composeapp.generated.resources.roboto_medium
import speakmind.composeapp.generated.resources.roboto_regular

// Fantasy Dark Color Palette
object SpeakMindColors {
    // Backgrounds
    val backgroundDark = Color(0xFF0D0B1E)
    val backgroundMid = Color(0xFF1A1147)
    val surface = Color(0xFF1E1A3A)
    val surfaceVariant = Color(0xFF2A2450)
    val cardSurface = Color(0xFF2A2450)

    // Primary accents
    val neonCyan = Color(0xFF00E5FF)
    val neonCyanDark = Color(0xFF00B8D4)
    val neonCyanLight = Color(0xFF80F0FF)

    // Secondary accents
    val magenta = Color(0xFFFF2D95)
    val magentaLight = Color(0xFFFF6DB5)

    // Tertiary accents
    val gold = Color(0xFFFFD700)
    val goldLight = Color(0xFFFFE566)

    // Text
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFB0A8D0)
    val textMuted = Color(0xFF7B7394)

    // Chat bubbles
    val userBubbleStart = Color(0xFF00B8D4)
    val userBubbleEnd = Color(0xFF00E5FF)
    val aiBubbleStart = Color(0xFF3D2C6E)
    val aiBubbleEnd = Color(0xFF5A3E9E)

    // Status
    val success = Color(0xFF00FF88)
    val successDark = Color(0xFF00CC6A)
    val error = Color(0xFFFF4444)
    val errorDark = Color(0xFFCC3636)
    val warning = Color(0xFFFFB020)

    // Levels
    val levelA1 = Color(0xFF4CAF50)
    val levelA2 = Color(0xFF8BC34A)
    val levelB1 = Color(0xFFFFEB3B)
    val levelB2 = Color(0xFFFF9800)
    val levelC1 = Color(0xFFF44336)

    // Gradients
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(backgroundDark, backgroundMid)
    )
    val userBubbleGradient = Brush.horizontalGradient(
        colors = listOf(userBubbleStart, userBubbleEnd)
    )
    val aiBubbleGradient = Brush.horizontalGradient(
        colors = listOf(aiBubbleStart, aiBubbleEnd)
    )
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            surfaceVariant.copy(alpha = 0.8f),
            surface.copy(alpha = 0.6f)
        )
    )
    val glowCyan = Brush.radialGradient(
        colors = listOf(
            neonCyan.copy(alpha = 0.3f),
            Color.Transparent
        )
    )
}

@Immutable
data class SpeakMindThemeColors(
    val backgroundDark: Color = SpeakMindColors.backgroundDark,
    val backgroundMid: Color = SpeakMindColors.backgroundMid,
    val surface: Color = SpeakMindColors.surface,
    val surfaceVariant: Color = SpeakMindColors.surfaceVariant,
    val neonCyan: Color = SpeakMindColors.neonCyan,
    val magenta: Color = SpeakMindColors.magenta,
    val gold: Color = SpeakMindColors.gold,
    val textPrimary: Color = SpeakMindColors.textPrimary,
    val textSecondary: Color = SpeakMindColors.textSecondary,
    val textMuted: Color = SpeakMindColors.textMuted,
    val success: Color = SpeakMindColors.success,
    val error: Color = SpeakMindColors.error,
    val warning: Color = SpeakMindColors.warning,
)

val LocalSpeakMindColors = staticCompositionLocalOf { SpeakMindThemeColors() }

private val DarkColorScheme = darkColorScheme(
    primary = SpeakMindColors.neonCyan,
    onPrimary = SpeakMindColors.backgroundDark,
    primaryContainer = SpeakMindColors.neonCyanDark,
    secondary = SpeakMindColors.magenta,
    onSecondary = Color.White,
    tertiary = SpeakMindColors.gold,
    background = SpeakMindColors.backgroundDark,
    onBackground = SpeakMindColors.textPrimary,
    surface = SpeakMindColors.surface,
    onSurface = SpeakMindColors.textPrimary,
    surfaceVariant = SpeakMindColors.surfaceVariant,
    onSurfaceVariant = SpeakMindColors.textSecondary,
    error = SpeakMindColors.error,
    onError = Color.White,
)

@Composable
fun SpeakMindTheme(content: @Composable () -> Unit) {
    val fontFamily = FontFamily(
        Font(Res.font.roboto_regular, FontWeight.Normal),
        Font(Res.font.roboto_medium, FontWeight.Medium),
        Font(Res.font.roboto_bold, FontWeight.Bold),
    )

    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            color = SpeakMindColors.textPrimary
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            color = SpeakMindColors.textPrimary
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            color = SpeakMindColors.textPrimary
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            color = SpeakMindColors.textPrimary
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        ),
    )

    CompositionLocalProvider(LocalSpeakMindColors provides SpeakMindThemeColors()) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = typography,
            content = content
        )
    }
}
