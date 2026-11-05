package com.speakmind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.ui.theme.LocalDisplayFont
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.SpeakMindThemeColors
import kotlinx.coroutines.flow.MutableStateFlow

// Building blocks of the Speaky redesign (sizes follow the 390pt-wide design one to one).

// ---------- Brand fills ----------

/**
 * Deep brand violet used for hero cards and decorative fills. Violet and Dark keep the approved
 * #5B3DF5; the Premium themes (Gold Luxe, Rose Gold) use their own brand colour.
 */
val SpeakMindThemeColors.brandFill: Color get() = if (isGold) neonCyan else Color(0xFF5B3DF5)

/** Darker end of [brandFill] for gradients. */
val SpeakMindThemeColors.brandFillDeep: Color get() = if (isGold) neonCyanDark else Color(0xFF4129D6)

/** Text and icons on [brandFill]. */
val SpeakMindThemeColors.onBrandFill: Color get() = if (isGold) onPrimary else Color.White

// ---------- Main tabs ----------

enum class MainTab(val label: String) { Home("Home"), Learn("Learn"), Words("Words"), Chat("Chat") }

/** The tab shown on the home destination; Chat opens the chats screen, which shows the same bar. */
object MainTabs {
    val selected = MutableStateFlow(MainTab.Home)
}

/** Space the floating bar covers at the bottom of a tab screen, for scrolling content. */
val BottomBarClearance = 112.dp

/**
 * The floating tab bar. With [onGames], a raised Games button sits in the middle; [gamesWaiting]
 * gives it a soft glow and a "1" while today's free game level is unplayed.
 */
@Composable
fun SpeakyBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    chatBadge: Int = 0,
    gamesWaiting: Boolean = false,
    onGames: (() -> Unit)? = null,
) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            .fillMaxWidth()
            .height(72.dp),
    ) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .shadow(30.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x1F17142B), spotColor = Color(0x1F17142B))
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            if (tab == MainTab.Words && onGames != null) {
                // The raised button is drawn over this slot; only its label sits in the bar.
                Column(
                    Modifier.widthIn(min = 60.dp).fillMaxHeight().clickable(role = Role.Button, onClick = onGames)
                        .padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text("Games", color = if (colors.isGold) colors.neonCyan else Color(0xFFE2552F), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            val on = tab == selected
            val tint = if (on) colors.neonCyan else colors.textMuted
            Column(
                modifier = Modifier
                    .widthIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    Icon(tab.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    if (tab == MainTab.Chat && chatBadge > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding().size(10.dp)
                                .clip(CircleShape).background(colors.surface).padding(2.dp)
                                .clip(CircleShape).background(colors.coral),
                        )
                    }
                }
                Text(
                    tab.label,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
        if (onGames != null) GamesButton(gamesWaiting, onGames, Modifier.align(Alignment.TopCenter).offset(y = (-18).dp))
    }
}

/** Orange (gold on Premium themes) round button with a game controller. */
@Composable
private fun GamesButton(waiting: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalSpeakMindColors.current
    val gold = colors.isGold
    val fill = if (gold) listOf(Color(0xFFF7D774), Color(0xFFC9962B)) else listOf(Color(0xFFFF9A4D), Color(0xFFFF5A5F))
    val glow = if (waiting) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "games")
        transition.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3000)),
            label = "glow",
        ).value
    } else 1f
    Box(modifier.size(62.dp), contentAlignment = Alignment.Center) {
        // Soft ring growing out of the button while a free level waits.
        if (waiting) {
            Box(
                Modifier.size(54.dp + 16.dp * glow).clip(CircleShape)
                    .background(fill.last().copy(alpha = 0.28f * (1f - glow))),
            )
        }
        Box(
            Modifier.size(62.dp).clip(CircleShape).background(colors.backgroundDark).padding(4.dp)
                .shadow(8.dp, CircleShape, ambientColor = fill.last(), spotColor = fill.last())
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(fill))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "Idiom games" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(SpeakyIcons.Gamepad, null, tint = if (gold) Color(0xFF2A1E05) else Color.White, modifier = Modifier.size(28.dp))
        }
        if (waiting) {
            Text(
                "1",
                Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(colors.backgroundDark).padding(2.dp)
                    .clip(CircleShape).background(Color(0xFFF04438)),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
            )
        }
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.Home -> SpeakyIcons.Home
    MainTab.Learn -> SpeakyIcons.Book
    MainTab.Words -> SpeakyIcons.Cards
    MainTab.Chat -> SpeakyIcons.Chat
}

// ---------- Surfaces ----------

/** White rounded card on the soft background; no borders. */
@Composable
fun SpeakyCard(
    modifier: Modifier = Modifier,
    color: Color = LocalSpeakMindColors.current.surface,
    radius: Dp = 24.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Rounded pill label (level, tags, counters). */
@Composable
fun SpeakyPill(
    text: String,
    background: Color,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    leading: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leading?.invoke(this)
        Text(text, color = color, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/** 44pt round icon button (back, search, menu). */
@Composable
fun SpeakyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    background: Color = LocalSpeakMindColors.current.surface,
    tint: Color = LocalSpeakMindColors.current.textPrimary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun SpeakyBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    SpeakyIconButton(SpeakyIcons.Back, "Back", onClick, modifier)

/** Big screen title in Bricolage Grotesque. */
@Composable
fun SpeakyTitle(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 30.sp, color: Color = LocalSpeakMindColors.current.textPrimary) {
    Text(
        text,
        modifier = modifier,
        style = displayStyle(fontSize),
        color = color,
    )
}

/** Bricolage Grotesque ExtraBold at [fontSize]. */
@Composable
fun displayStyle(fontSize: TextUnit, lineHeight: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = LocalDisplayFont.current,
    fontWeight = FontWeight.ExtraBold,
    fontSize = fontSize,
    lineHeight = lineHeight,
)

/** Small uppercase label above a section ("WORD OF THE DAY"). */
@Composable
fun SpeakySectionLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalSpeakMindColors.current.textMuted) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

/** Section heading with an optional action on the right ("Video courses · See all"). */
@Composable
fun SpeakySectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val colors = LocalSpeakMindColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = displayStyle(18.sp), color = colors.textPrimary)
        if (action != null && onAction != null) {
            Text(
                action,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp),
                color = colors.neonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Full-width primary button (56pt, radius 18). */
@Composable
fun SpeakyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = LocalSpeakMindColors.current.neonCyan,
    color: Color = LocalSpeakMindColors.current.let { if (background == it.neonCyan) it.onPrimary else Color.White },
    height: Dp = 56.dp,
    radius: Dp = 18.dp,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { Icon(it, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)) }
        Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        trailing?.let { Spacer(Modifier.size(8.dp)); Icon(it, null, tint = color, modifier = Modifier.size(20.dp)) }
    }
}

/** Slim progress bar (track #EFEDF7). */
@Composable
fun SpeakyProgress(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val colors = LocalSpeakMindColors.current
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp)).background(colors.track)) {
        Box(
            Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height)
                .clip(RoundedCornerShape(999.dp)).background(color),
        )
    }
}

// ---------- English + Arabic meaning ----------

/**
 * The meaning in both languages: an "EN" row and an Arabic "ع" row (word big, meaning under it).
 * [framed] puts both rows on a soft tray (sheets and lookup); otherwise they sit on a white card.
 */
@Composable
fun EnglishArabicMeaning(
    english: String?,
    arabicWord: String?,
    arabicMeaning: String? = null,
    modifier: Modifier = Modifier,
    englishTitle: String? = null,
    framed: Boolean = false,
) {
    val colors = LocalSpeakMindColors.current
    val dark = colors.surface.luminance() < 0.5f
    val arTint = if (dark) Color(0xFF12302A) else Color(0xFFF3FBF7)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (framed) 18.dp else 24.dp))
            .background(if (framed) colors.backgroundDark else colors.surface)
            .padding(if (framed) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!english.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(if (framed) 14.dp else 16.dp))
                    .background(if (framed) colors.surface else Color.Transparent)
                    .padding(horizontal = if (framed) 12.dp else 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LangBadge("EN", colors.primarySoft, colors.onPrimarySoft, 12.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!englishTitle.isNullOrBlank()) {
                        Text(englishTitle, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text(english, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    } else {
                        Text(english, color = colors.textPrimary, fontSize = 16.sp, lineHeight = 24.sp)
                    }
                }
            }
        }
        if (!arabicWord.isNullOrBlank() || !arabicMeaning.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(if (framed) 14.dp else 16.dp))
                    .background(if (framed) colors.surface else arTint)
                    .padding(horizontal = if (framed) 12.dp else 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LangBadge("ع", colors.mintSoft, colors.onMintSoft, 15.sp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!arabicWord.isNullOrBlank()) {
                        Text(
                            arabicWord,
                            color = colors.onMintSoft,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.End,
                            style = TextStyle(textDirection = TextDirection.Rtl),
                        )
                    }
                    if (!arabicMeaning.isNullOrBlank()) {
                        Text(
                            arabicMeaning,
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.End,
                            style = TextStyle(textDirection = TextDirection.Rtl),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LangBadge(text: String, background: Color, color: Color, fontSize: TextUnit) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------- Icons (the design's own 24×24 strokes) ----------

object SpeakyIcons {
    val Home by lazy { strokeIcon("M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val Book by lazy { strokeIcon("M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z", "M4 19V5") }
    val Cards by lazy { strokeIcon(rect(3f, 6f, 14f, 14f, 3f), "M7 3h11a3 3 0 0 1 3 3v11") }
    val Chat by lazy { strokeIcon("M21 12a8 8 0 0 1-12 7l-5 1 1-4a8 8 0 1 1 16-4z") }
    val Back by lazy { strokeIcon("M15 6l-6 6 6 6", width = 2.2f) }
    val Chevron by lazy { strokeIcon("M9 6l6 6-6 6") }
    val ArrowRight by lazy { strokeIcon("M5 12h14M13 6l6 6-6 6", width = 2.4f) }
    val Search by lazy { strokeIcon(circle(11f, 11f, 7f), "M20 20l-4-4", width = 2.2f) }
    val Speaker by lazy { strokeIcon("M4 9v6h4l5 4V5L8 9z", "M16 9a4 4 0 0 1 0 6", width = 2.2f) }
    val Check by lazy { strokeIcon("M5 12l5 5 9-10", width = 2.4f) }
    val Close by lazy { strokeIcon("M6 6l12 12M18 6L6 18", width = 2.2f) }
    val Globe by lazy { strokeIcon(circle(12f, 12f, 9f), "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18") }
    val Flame by lazy { strokeIcon("M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-3 2-4 2-7 2 1 3 2 3 4") }
    val Mic by lazy { strokeIcon(rect(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3") }
    val Lock by lazy { strokeIcon(rect(5f, 11f, 14f, 10f, 2f), "M8 11V7a4 4 0 0 1 8 0v4") }
    val Trash by lazy { strokeIcon("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3") }
    val Sparkle by lazy { strokeIcon("M12 3l1.8 4.6L18 9.5l-4.2 1.9L12 16l-1.8-4.6L6 9.5l4.2-1.9z") }
    val AddPerson by lazy { strokeIcon(circle(9f, 8f, 4f), "M2 21a7 7 0 0 1 14 0M19 8v6M16 11h6") }
    val Person by lazy { strokeIcon(circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0") }
    val Bell by lazy { strokeIcon("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21a2 2 0 0 0 4 0") }
    val Bookmark by lazy { strokeIcon("M6 3h12v18l-6-4-6 4z", width = 2.2f) }
    val Pencil by lazy { strokeIcon("M4 20h4L19 9l-4-4L4 16z") }
    val SignOut by lazy { strokeIcon("M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 16l-4-4 4-4M6 12h10") }
    val Video by lazy { strokeIcon(rect(3f, 5f, 18f, 14f, 3f), "M10 9l5 3-5 3z") }
    val Shield by lazy { strokeIcon("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z") }
    val Moon by lazy { strokeIcon("M21 13A9 9 0 1 1 11 3a7 7 0 0 0 10 10z") }
    val Send by lazy { strokeIcon("M4 12l16-8-6 16-2-7z") }
    val Plus by lazy { strokeIcon("M12 5v14M5 12h14", width = 2.2f) }
    val Translate by lazy { strokeIcon("M4 5h8M8 3v2M6 5c0 4 3 7 6 8M10 5c0 3-3 7-6 8", "M13 21l4-9 4 9M14.5 18h5") }
    val Trophy by lazy { strokeIcon("M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0z", "M17 6h3a3 3 0 0 1-3 4M7 6H4a3 3 0 0 0 3 4") }
    val Reply by lazy { strokeIcon("M9 14L4 9l5-5", "M4 9h11a5 5 0 0 1 5 5v5") }
    val Copy by lazy { strokeIcon(rect(9f, 9f, 12f, 12f, 2f), "M5 15V5a2 2 0 0 1 2-2h10") }
    val Smile by lazy { strokeIcon(circle(12f, 12f, 9f), "M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01") }
    val Dots by lazy { fillIcon(circle(12f, 5f, 2f) + circle(12f, 12f, 2f) + circle(12f, 19f, 2f)) }
    val Play by lazy { fillIcon("M8 5v14l11-7z") }
    val Crown by lazy { fillIcon("M3 8l4 4 5-7 5 7 4-4-2 11H5z") }
    val Phone by lazy { strokeIcon("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2") }
    val MicOff by lazy { strokeIcon(rect(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3M4 4l16 16") }
    val ChevronDown by lazy { strokeIcon("M6 9l6 6 6-6", width = 2.2f) }
    val Ban by lazy { strokeIcon(circle(12f, 12f, 9f), "M5.6 5.6l12.8 12.8") }
    /** Filled controller; the d-pad and buttons are holes, so the button colour shows through. */
    val Gamepad by lazy {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(
                    "M7 7h10a5 5 0 0 1 4.9 6l-.7 3.4a2.8 2.8 0 0 1-4.9 1.3L14.5 15.5h-5l-1.8 2.2a2.8 2.8 0 0 1-4.9-1.3L2.1 13A5 5 0 0 1 7 7z" +
                        "M6.85 10h1.3v1.1h1.1v1.3h-1.1v1.1h-1.3v-1.1h-1.1v-1.3h1.1z" +
                        circle(16f, 10.6f, 1.1f) + circle(18.2f, 12.8f, 1.1f),
                ),
                pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
            .build()
    }
    val Sun by lazy { strokeIcon(circle(12f, 12f, 4f), "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4") }
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

private fun strokeIcon(vararg paths: String, width: Float = 2f): ImageVector {
    val builder = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    paths.forEach { d ->
        builder.addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private fun fillIcon(d: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        .build()

// ---------------- Status bar ----------------

/** A screen whose header is coloured (the Hub) paints the status bar strip to match; null = page background. */
object StatusBarTint {
    val color = androidx.compose.runtime.mutableStateOf<Color?>(null)
}

/** Paints the status bar [color] while this screen is shown. */
@Composable
fun TintStatusBar(color: Color?) {
    androidx.compose.runtime.DisposableEffect(color) {
        StatusBarTint.color.value = color
        onDispose { if (StatusBarTint.color.value == color) StatusBarTint.color.value = null }
    }
}
