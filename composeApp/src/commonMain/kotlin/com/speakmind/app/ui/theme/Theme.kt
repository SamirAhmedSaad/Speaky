package com.speakmind.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.speakmind.app.feature.home.domain.model.getLevelColor
import com.speakmind.app.feature.home.domain.model.getLevelColorLight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import network.chaintech.sdpcomposemultiplatform.ssp
import org.jetbrains.compose.resources.Font
import speaky.composeapp.generated.resources.Res
import speaky.composeapp.generated.resources.roboto_bold
import speaky.composeapp.generated.resources.roboto_medium
import speaky.composeapp.generated.resources.roboto_regular

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

// Light Color Palette
object SpeakMindLightColors {
    val backgroundDark = Color(0xFFF4F2FF)   // subtle lavender white
    val backgroundMid = Color(0xFFEAE6FF)    // slightly deeper lavender
    val surface = Color(0xFFFFFFFF)           // pure white
    val surfaceVariant = Color(0xFFDDD6F3)    // visible but soft lavender card bg
    val cardSurface = Color(0xFFDDD6F3)

    val neonCyan = Color(0xFF006D7A)          // deep teal — high contrast on white
    val neonCyanDark = Color(0xFF005560)
    val neonCyanLight = Color(0xFF4DD0E1)

    val magenta = Color(0xFFC0185A)           // deepened rose/magenta
    val magentaLight = Color(0xFFE84080)

    val gold = Color(0xFF9A6800)              // dark amber
    val goldLight = Color(0xFFC88A00)

    val textPrimary = Color(0xFF1A1A2E)       // near-black with slight purple tint
    val textSecondary = Color(0xFF4A4365)     // medium purple-gray
    val textMuted = Color(0xFF726B90)         // softer muted text

    val userBubbleStart = Color(0xFF006D7A)
    val userBubbleEnd = Color(0xFF0097A7)
    val aiBubbleStart = Color(0xFFDBD4F7)
    val aiBubbleEnd = Color(0xFFC9BFF0)

    val success = Color(0xFF2E7D32)
    val successDark = Color(0xFF1B5E20)
    val error = Color(0xFFB71C1C)             // darker red for better contrast
    val errorDark = Color(0xFF7F0000)
    val warning = Color(0xFF8D4E00)           // dark amber-brown for contrast

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
            neonCyan.copy(alpha = 0.15f),
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
    val neonCyanDark: Color = SpeakMindColors.neonCyanDark,
    val magenta: Color = SpeakMindColors.magenta,
    val gold: Color = SpeakMindColors.gold,
    val textPrimary: Color = SpeakMindColors.textPrimary,
    val textSecondary: Color = SpeakMindColors.textSecondary,
    val textMuted: Color = SpeakMindColors.textMuted,
    val success: Color = SpeakMindColors.success,
    val error: Color = SpeakMindColors.error,
    val warning: Color = SpeakMindColors.warning,
    val backgroundGradient: Brush = SpeakMindColors.backgroundGradient,
    val userBubbleGradient: Brush = SpeakMindColors.userBubbleGradient,
    val aiBubbleGradient: Brush = SpeakMindColors.aiBubbleGradient,
    val cardGradient: Brush = SpeakMindColors.cardGradient,
    val glowCyan: Brush = SpeakMindColors.glowCyan,
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

private val LightColorScheme = lightColorScheme(
    primary = SpeakMindLightColors.neonCyan,
    onPrimary = Color.White,
    primaryContainer = SpeakMindLightColors.neonCyanLight,
    secondary = SpeakMindLightColors.magenta,
    onSecondary = Color.White,
    tertiary = SpeakMindLightColors.gold,
    background = SpeakMindLightColors.backgroundDark,
    onBackground = SpeakMindLightColors.textPrimary,
    surface = SpeakMindLightColors.surface,
    onSurface = SpeakMindLightColors.textPrimary,
    surfaceVariant = SpeakMindLightColors.surfaceVariant,
    onSurfaceVariant = SpeakMindLightColors.textSecondary,
    error = SpeakMindLightColors.error,
    onError = Color.White,
)

private val DarkSpeakMindThemeColors = SpeakMindThemeColors(
    backgroundDark = SpeakMindColors.backgroundDark,
    backgroundMid = SpeakMindColors.backgroundMid,
    surface = SpeakMindColors.surface,
    surfaceVariant = SpeakMindColors.surfaceVariant,
    neonCyan = SpeakMindColors.neonCyan,
    neonCyanDark = SpeakMindColors.neonCyanDark,
    magenta = SpeakMindColors.magenta,
    gold = SpeakMindColors.gold,
    textPrimary = SpeakMindColors.textPrimary,
    textSecondary = SpeakMindColors.textSecondary,
    textMuted = SpeakMindColors.textMuted,
    success = SpeakMindColors.success,
    error = SpeakMindColors.error,
    warning = SpeakMindColors.warning,
    backgroundGradient = SpeakMindColors.backgroundGradient,
    userBubbleGradient = SpeakMindColors.userBubbleGradient,
    aiBubbleGradient = SpeakMindColors.aiBubbleGradient,
    cardGradient = SpeakMindColors.cardGradient,
    glowCyan = SpeakMindColors.glowCyan,
)

private val LightSpeakMindThemeColors = SpeakMindThemeColors(
    backgroundDark = SpeakMindLightColors.backgroundDark,
    backgroundMid = SpeakMindLightColors.backgroundMid,
    surface = SpeakMindLightColors.surface,
    surfaceVariant = SpeakMindLightColors.surfaceVariant,
    neonCyan = SpeakMindLightColors.neonCyan,
    neonCyanDark = SpeakMindLightColors.neonCyanDark,
    magenta = SpeakMindLightColors.magenta,
    gold = SpeakMindLightColors.gold,
    textPrimary = SpeakMindLightColors.textPrimary,
    textSecondary = SpeakMindLightColors.textSecondary,
    textMuted = SpeakMindLightColors.textMuted,
    success = SpeakMindLightColors.success,
    error = SpeakMindLightColors.error,
    warning = SpeakMindLightColors.warning,
    backgroundGradient = SpeakMindLightColors.backgroundGradient,
    userBubbleGradient = SpeakMindLightColors.userBubbleGradient,
    aiBubbleGradient = SpeakMindLightColors.aiBubbleGradient,
    cardGradient = SpeakMindLightColors.cardGradient,
    glowCyan = SpeakMindLightColors.glowCyan,
)

@Composable
fun SpeakMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val fontFamily = FontFamily(
        Font(Res.font.roboto_regular, FontWeight.Normal),
        Font(Res.font.roboto_medium, FontWeight.Medium),
        Font(Res.font.roboto_bold, FontWeight.Bold),
    )

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val speakMindColors = if (darkTheme) DarkSpeakMindThemeColors else LightSpeakMindThemeColors

    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.ssp,
            lineHeight = 40.ssp,
            color = speakMindColors.textPrimary
        ),
        headlineLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.ssp,
            lineHeight = 36.ssp,
            color = speakMindColors.textPrimary
        ),
        headlineMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.ssp,
            lineHeight = 32.ssp,
            color = speakMindColors.textPrimary
        ),
        headlineSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.ssp,
            lineHeight = 28.ssp,
            color = speakMindColors.textPrimary
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.ssp,
            lineHeight = 24.ssp,
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.ssp,
            lineHeight = 22.ssp,
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.ssp,
            lineHeight = 20.ssp,
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.ssp,
            lineHeight = 24.ssp,
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.ssp,
            lineHeight = 20.ssp,
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.ssp,
            lineHeight = 16.ssp,
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.ssp,
            lineHeight = 20.ssp,
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.ssp,
            lineHeight = 16.ssp,
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.ssp,
            lineHeight = 14.ssp,
        ),
    )

    CompositionLocalProvider(LocalSpeakMindColors provides speakMindColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

/** Returns the appropriate level badge color for the current theme. */
@Composable
fun levelColorOf(level: String): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(getLevelColor(level)) else Color(getLevelColorLight(level))
}
