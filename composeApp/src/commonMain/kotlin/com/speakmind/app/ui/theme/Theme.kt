package com.speakmind.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
import speaky.composeapp.generated.resources.bricolage_bold
import speaky.composeapp.generated.resources.bricolage_extrabold
import speaky.composeapp.generated.resources.jakarta_bold
import speaky.composeapp.generated.resources.jakarta_extrabold
import speaky.composeapp.generated.resources.jakarta_medium
import speaky.composeapp.generated.resources.jakarta_regular
import speaky.composeapp.generated.resources.jakarta_semibold

// Dark palette (night mode of the Speaky redesign)
object SpeakMindColors {
    val backgroundDark = Color(0xFF0F0D1F)
    val backgroundMid = Color(0xFF0F0D1F)
    val surface = Color(0xFF1C1934)
    val surfaceVariant = Color(0xFF2A2550)
    val cardSurface = Color(0xFF1C1934)

    val neonCyan = Color(0xFFB9AEFF)
    val neonCyanDark = Color(0xFF7B61FF)
    val neonCyanLight = Color(0xFF2A2550)

    val magenta = Color(0xFFFF8A6B)
    val magentaLight = Color(0xFF3A2330)

    val gold = Color(0xFFF5D27A)
    val goldLight = Color(0xFF3A3016)

    val textPrimary = Color(0xFFF3F1FF)
    val textSecondary = Color(0xFFD6D3EA)
    val textMuted = Color(0xFFA9A5C4)

    val userBubbleStart = Color(0xFF7B61FF)
    val userBubbleEnd = Color(0xFF7B61FF)
    val aiBubbleStart = Color(0xFF1C1934)
    val aiBubbleEnd = Color(0xFF1C1934)

    val success = Color(0xFF7EE8C4)
    val successDark = Color(0xFF12302A)
    val error = Color(0xFFFF8A8A)
    val errorDark = Color(0xFF3A2330)
    val warning = Color(0xFFF5D27A)

    // Levels
    val levelA1 = Color(0xFF4CAF50)
    val levelA2 = Color(0xFF8BC34A)
    val levelB1 = Color(0xFFFFEB3B)
    val levelB2 = Color(0xFFFF9800)
    val levelC1 = Color(0xFFF44336)

    val backgroundGradient = Brush.verticalGradient(listOf(backgroundDark, backgroundMid))
    val userBubbleGradient = Brush.horizontalGradient(listOf(userBubbleStart, userBubbleEnd))
    val aiBubbleGradient = Brush.horizontalGradient(listOf(aiBubbleStart, aiBubbleEnd))
    val cardGradient = Brush.verticalGradient(listOf(surface, surface))
    val glowCyan = Brush.radialGradient(listOf(neonCyan.copy(alpha = 0.2f), Color.Transparent))
}

// Light palette of the Speaky redesign: flat soft-grey background, white cards, violet brand
object SpeakMindLightColors {
    val backgroundDark = Color(0xFFF6F5FB)
    val backgroundMid  = Color(0xFFF6F5FB)
    val surface        = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFF1EFF8)
    val cardSurface    = Color(0xFFFFFFFF)

    val neonCyan       = Color(0xFF5B3DF5)
    val neonCyanDark   = Color(0xFF4129D6)
    val neonCyanLight  = Color(0xFFECE8FF)

    val magenta        = Color(0xFFFF6B4A)
    val magentaLight   = Color(0xFFFFE9E2)

    val gold           = Color(0xFFB7791F)
    val goldLight      = Color(0xFFFFF4D6)

    val textPrimary    = Color(0xFF17142B)
    val textSecondary  = Color(0xFF3B3852)
    val textMuted      = Color(0xFF6B6880)

    val userBubbleStart = Color(0xFF5B3DF5)
    val userBubbleEnd   = Color(0xFF5B3DF5)
    val aiBubbleStart   = Color(0xFFFFFFFF)
    val aiBubbleEnd     = Color(0xFFFFFFFF)
    val aiBubbleBorder  = Color.Transparent

    val success     = Color(0xFF0B7A57)
    val successDark = Color(0xFFDDF6EC)
    val error       = Color(0xFFB42318)
    val errorDark   = Color(0xFFFDE2E2)
    val warning     = Color(0xFF7A5200)

    val backgroundGradient = Brush.verticalGradient(listOf(backgroundDark, backgroundMid))
    val userBubbleGradient = Brush.horizontalGradient(listOf(userBubbleStart, userBubbleEnd))
    val aiBubbleGradient = Brush.horizontalGradient(listOf(aiBubbleStart, aiBubbleEnd))
    val cardGradient = Brush.verticalGradient(listOf(surface, surface))
    val glowCyan = Brush.radialGradient(listOf(neonCyan.copy(alpha = 0.12f), Color.Transparent))
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
    val aiBubbleBorder: Color = Color.Transparent,
    val backgroundGradient: Brush = SpeakMindColors.backgroundGradient,
    val userBubbleGradient: Brush = SpeakMindColors.userBubbleGradient,
    val aiBubbleGradient: Brush = SpeakMindColors.aiBubbleGradient,
    val cardGradient: Brush = SpeakMindColors.cardGradient,
    val glowCyan: Brush = SpeakMindColors.glowCyan,
    /** Soft tint of the brand colour (chips, icon circles). */
    val primarySoft: Color = SpeakMindColors.neonCyanLight,
    /** Text/icons on [primarySoft]. */
    val onPrimarySoft: Color = Color(0xFFD9D2FF),
    val coral: Color = SpeakMindColors.magenta,
    val coralSoft: Color = SpeakMindColors.magentaLight,
    val onCoralSoft: Color = Color(0xFFFFB4A1),
    val mintSoft: Color = SpeakMindColors.successDark,
    val onMintSoft: Color = SpeakMindColors.success,
    val goldSoft: Color = SpeakMindColors.goldLight,
    val onGoldSoft: Color = SpeakMindColors.gold,
    val track: Color = SpeakMindColors.surfaceVariant,
    val faint: Color = Color(0xFF8A87A0),
    /** Text and icons on a brand-filled background (buttons, my bubbles). */
    val onPrimary: Color = Color.White,
    /** Text on my chat bubbles ([userBubbleGradient]). */
    val onBubble: Color = Color.White,
    val isDark: Boolean = true,
    /** Gold Luxe / Rose Gold, the Premium themes. */
    val isGold: Boolean = false,
)

val LocalSpeakMindColors = staticCompositionLocalOf { SpeakMindThemeColors() }

/** Bricolage Grotesque, for big titles and numbers. */
val LocalDisplayFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

private val DarkColorScheme = darkColorScheme(
    primary = SpeakMindColors.neonCyan,
    onPrimary = SpeakMindColors.backgroundDark,
    primaryContainer = SpeakMindColors.neonCyanLight,
    onPrimaryContainer = SpeakMindColors.textPrimary,
    secondary = SpeakMindColors.magenta,
    onSecondary = Color.White,
    tertiary = SpeakMindColors.gold,
    background = SpeakMindColors.backgroundDark,
    onBackground = SpeakMindColors.textPrimary,
    surface = SpeakMindColors.surface,
    onSurface = SpeakMindColors.textPrimary,
    surfaceVariant = SpeakMindColors.surfaceVariant,
    onSurfaceVariant = SpeakMindColors.textSecondary,
    surfaceContainer = SpeakMindColors.surface,
    surfaceContainerHigh = SpeakMindColors.surface,
    surfaceContainerLow = SpeakMindColors.surface,
    outline = Color(0xFF4A4570),
    outlineVariant = Color(0xFF2E2A4A),
    error = SpeakMindColors.error,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = SpeakMindLightColors.neonCyan,
    onPrimary = Color.White,
    primaryContainer = SpeakMindLightColors.neonCyanLight,
    onPrimaryContainer = Color(0xFF2E1E9E),
    surfaceContainer = SpeakMindLightColors.surface,
    surfaceContainerHigh = SpeakMindLightColors.surface,
    surfaceContainerLow = SpeakMindLightColors.surface,
    outline = Color(0xFFC9C5E0),
    outlineVariant = Color(0xFFEAE7F4),
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
    onPrimary = Color(0xFF17142B),
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
    aiBubbleBorder = SpeakMindLightColors.aiBubbleBorder,
    backgroundGradient = SpeakMindLightColors.backgroundGradient,
    userBubbleGradient = SpeakMindLightColors.userBubbleGradient,
    aiBubbleGradient = SpeakMindLightColors.aiBubbleGradient,
    cardGradient = SpeakMindLightColors.cardGradient,
    glowCyan = SpeakMindLightColors.glowCyan,
    primarySoft = SpeakMindLightColors.neonCyanLight,
    onPrimarySoft = Color(0xFF2E1E9E),
    coral = SpeakMindLightColors.magenta,
    coralSoft = SpeakMindLightColors.magentaLight,
    onCoralSoft = Color(0xFF9A3412),
    mintSoft = Color(0xFFDDF6EC),
    onMintSoft = Color(0xFF0B5E45),
    goldSoft = SpeakMindLightColors.goldLight,
    onGoldSoft = SpeakMindLightColors.warning,
    track = Color(0xFFEFEDF7),
    faint = Color(0xFF8A87A0),
    isDark = false,
)

// Gold Luxe: warm black with gold (Premium)
private val GoldLuxeThemeColors = SpeakMindThemeColors(
    backgroundDark = Color(0xFF121008),
    backgroundMid = Color(0xFF121008),
    surface = Color(0xFF1E1A10),
    surfaceVariant = Color(0xFF2A2312),
    neonCyan = Color(0xFFD4A017),
    neonCyanDark = Color(0xFFF5D27A),
    magenta = Color(0xFFFF8A6B),
    gold = Color(0xFFF5D27A),
    textPrimary = Color(0xFFF7F1DE),
    textSecondary = Color(0xFFD9CFAE),
    textMuted = Color(0xFFB8AE8E),
    success = Color(0xFF7EE8C4),
    error = Color(0xFFFF8A8A),
    warning = Color(0xFFF5D27A),
    backgroundGradient = Brush.verticalGradient(listOf(Color(0xFF121008), Color(0xFF121008))),
    userBubbleGradient = Brush.linearGradient(listOf(Color(0xFFF5D27A), Color(0xFFD4A017), Color(0xFFA87A0C))),
    aiBubbleGradient = Brush.horizontalGradient(listOf(Color(0xFF1E1A10), Color(0xFF1E1A10))),
    cardGradient = Brush.verticalGradient(listOf(Color(0xFF1E1A10), Color(0xFF1E1A10))),
    glowCyan = Brush.radialGradient(listOf(Color(0x33D4A017), Color.Transparent)),
    primarySoft = Color(0xFF2E2714),
    onPrimarySoft = Color(0xFFF5D27A),
    coral = Color(0xFFFF8A6B),
    coralSoft = Color(0xFF3A2318),
    onCoralSoft = Color(0xFFFFB4A1),
    mintSoft = Color(0xFF12302A),
    onMintSoft = Color(0xFF7EE8C4),
    goldSoft = Color(0xFF3A3016),
    onGoldSoft = Color(0xFFF5D27A),
    track = Color(0xFF2A2312),
    faint = Color(0xFF8F866B),
    onPrimary = Color(0xFF2B1D00),
    onBubble = Color(0xFF2B1D00),
    isDark = true,
    isGold = true,
)

// Rose Gold: warm light with copper accents (Premium)
private val RoseGoldThemeColors = SpeakMindThemeColors(
    backgroundDark = Color(0xFFFFF5F1),
    backgroundMid = Color(0xFFFFF5F1),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF8EBE5),
    neonCyan = Color(0xFFB0664F),
    neonCyanDark = Color(0xFF8E4B37),
    magenta = Color(0xFFE5674A),
    gold = Color(0xFFB7791F),
    textPrimary = Color(0xFF2A1A15),
    textSecondary = Color(0xFF4A3530),
    textMuted = Color(0xFF7D6660),
    success = Color(0xFF0B7A57),
    error = Color(0xFFB42318),
    warning = Color(0xFF7A5200),
    backgroundGradient = Brush.verticalGradient(listOf(Color(0xFFFFF5F1), Color(0xFFFFF5F1))),
    userBubbleGradient = Brush.linearGradient(listOf(Color(0xFFC27A63), Color(0xFFA85F49))),
    aiBubbleGradient = Brush.horizontalGradient(listOf(Color.White, Color.White)),
    cardGradient = Brush.verticalGradient(listOf(Color.White, Color.White)),
    glowCyan = Brush.radialGradient(listOf(Color(0x1FC27A63), Color.Transparent)),
    primarySoft = Color(0xFFF8E4DC),
    onPrimarySoft = Color(0xFF7A3E2C),
    coral = Color(0xFFE5674A),
    coralSoft = Color(0xFFFFE9E2),
    onCoralSoft = Color(0xFF9A3412),
    mintSoft = Color(0xFFDDF6EC),
    onMintSoft = Color(0xFF0B5E45),
    goldSoft = Color(0xFFFFF4D6),
    onGoldSoft = Color(0xFF7A5200),
    track = Color(0xFFF3E6E0),
    faint = Color(0xFF9C8780),
    isDark = false,
    isGold = true,
)

@Composable
fun SpeakMindTheme(
    appTheme: AppTheme = if (isSystemInDarkTheme()) AppTheme.Dark else AppTheme.Violet,
    content: @Composable () -> Unit,
) {
    val fontFamily = FontFamily(
        Font(Res.font.jakarta_regular, FontWeight.Normal),
        Font(Res.font.jakarta_medium, FontWeight.Medium),
        Font(Res.font.jakarta_semibold, FontWeight.SemiBold),
        Font(Res.font.jakarta_bold, FontWeight.Bold),
        Font(Res.font.jakarta_extrabold, FontWeight.ExtraBold),
    )
    val displayFamily = FontFamily(
        Font(Res.font.bricolage_bold, FontWeight.Bold),
        Font(Res.font.bricolage_extrabold, FontWeight.ExtraBold),
    )

    val speakMindColors = when (appTheme) {
        AppTheme.Dark -> DarkSpeakMindThemeColors
        AppTheme.GoldLuxe -> GoldLuxeThemeColors
        AppTheme.RoseGold -> RoseGoldThemeColors
        else -> LightSpeakMindThemeColors
    }
    val base = if (speakMindColors.isDark) DarkColorScheme else LightColorScheme
    val colorScheme = if (!speakMindColors.isGold) base else base.copy(
        primary = speakMindColors.neonCyan,
        onPrimary = speakMindColors.onPrimary,
        primaryContainer = speakMindColors.primarySoft,
        onPrimaryContainer = speakMindColors.onPrimarySoft,
        background = speakMindColors.backgroundDark,
        onBackground = speakMindColors.textPrimary,
        surface = speakMindColors.surface,
        onSurface = speakMindColors.textPrimary,
        surfaceVariant = speakMindColors.surfaceVariant,
        onSurfaceVariant = speakMindColors.textSecondary,
        surfaceContainer = speakMindColors.surface,
        surfaceContainerHigh = speakMindColors.surface,
        surfaceContainerLow = speakMindColors.surface,
    )

    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.ssp,
            lineHeight = 40.ssp,
            color = speakMindColors.textPrimary
        ),
        headlineLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.ssp,
            lineHeight = 36.ssp,
            color = speakMindColors.textPrimary
        ),
        headlineMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.ExtraBold,
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

    CompositionLocalProvider(
        LocalSpeakMindColors provides speakMindColors,
        LocalDisplayFont provides displayFamily,
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
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
