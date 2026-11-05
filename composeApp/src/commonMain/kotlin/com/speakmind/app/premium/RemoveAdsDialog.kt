package com.speakmind.app.premium

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.speakmind.app.ui.components.SpeakyIconButton
import com.speakmind.app.ui.components.SpeakyIcons
import com.speakmind.app.ui.components.SpeakyPrimaryButton
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

// Premium's own gold look (same in light and dark).
private val Ink = Color(0xFF17142B)
private val Gold = Color(0xFFF5B301)
private val OnGold = Color(0xFF4A2F00)
private val Mint = Color(0xFF3EE0AE)
private val SoftLilac = Color(0xFFD3CFEA)

/** What Premium really includes (see [Premium]). */
private val PERKS = listOf(
    "Every lesson and course unlocked",
    "Unlimited private messages",
    "See who is online now",
    "Fix and translate all your messages",
    "No ads, ever",
    "Gold Premium badge in chats",
)

// ---------------- Before an ad: "watch" or Premium ----------------

/** Shown (from AppRoot) before ads: "watch a video" (free) or Speaky Premium. */
@Composable
fun RemoveAdsHost() {
    val choice by Premium.adChoice.collectAsState()
    val current = choice ?: return
    val prices by Premium.prices.collectAsState()
    val canBuy = Premium.canBuy && prices.isNotEmpty()
    // "Premium" opens the plans over the choice; closing them goes back to the choice.
    var showPlans by remember(current) { mutableStateOf(false) }
    if (showPlans) {
        PremiumPlansScreen(subtitle = null, prices = prices, onClose = { showPlans = false })
        return
    }
    if (current.unlockLesson) {
        UnlockSheet(
            title = current.title.ifBlank { "Unlock" },
            canBuy = canBuy,
            onWatch = Premium::watchAd,
            onPremium = { showPlans = true },
            onDismiss = Premium::dismissChoice,
        )
    } else {
        AdChoiceDialog(canBuy = canBuy, onWatch = Premium::watchAd, onRemoveAds = { showPlans = true }, onDismiss = Premium::dismissChoice)
    }
}

/** "Unlock this lesson": a free short video, or every lesson with Premium. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockSheet(title: String, canBuy: Boolean, onWatch: () -> Unit, onPremium: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = colors.surface,
        scrimColor = Ink.copy(alpha = 0.5f),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(width = 40.dp, height = 5.dp).clip(CircleShape).background(colors.track))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(colors.coralSoft),
                    contentAlignment = Alignment.Center,
                ) { Icon(SpeakyIcons.Lock, null, tint = colors.onCoralSoft, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.size(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = displayStyle(22.sp), color = colors.textPrimary)
                    Text("Free with a short video", color = colors.textSecondary, fontSize = 14.sp)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.coral)
                    .clickable(role = Role.Button, onClick = onWatch),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SpeakyIcons.Play, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("Watch a short video", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Free · about 30 seconds", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
            // No subscribe offer here: Premium is offered once a free allowance is used up.
            TextAction("Not now", colors.textSecondary, onDismiss)
        }
    }
}

/** Before a full-screen ad: remove ads with Premium, or watch it. Back still plays the ad. */
@Composable
private fun AdChoiceDialog(canBuy: Boolean, onWatch: () -> Unit, onRemoveAds: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(colors.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(colors.goldSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(SpeakyIcons.Video, null, tint = colors.onGoldSoft, modifier = Modifier.size(30.dp)) }
            Text("A short ad is next", style = displayStyle(24.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(
                "Ads keep Speaky free. Go Premium to learn without any ads.",
                color = colors.textSecondary, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            )
            if (canBuy) InkButton("Remove ads", onRemoveAds)
            SpeakyPrimaryButton(
                "Watch the ad", onClick = onWatch,
                background = colors.surfaceVariant, color = colors.textPrimary, height = 50.dp, radius = 16.dp,
            )
        }
    }
}

// ---------------- Premium-only feature or free allowance used up ----------------

/** Shown (from AppRoot) when a Premium-only feature is opened: subscribe, or wait for the free allowance. */
@Composable
fun PaywallHost() {
    PremiumWelcomeHost()
    val wall by Premium.paywall.collectAsState()
    val current = wall ?: return
    val prices by Premium.prices.collectAsState()
    val canBuy = Premium.canBuy && prices.isNotEmpty()
    var showPlans by remember(current) { mutableStateOf(false) }
    val waitMs = current.waitMs
    when {
        // No countdown to show: straight to the plans, with the reason as the subtitle.
        waitMs == null -> PremiumPlansScreen(subtitle = current.message, prices = prices, onClose = Premium::dismissPaywall)
        showPlans -> PremiumPlansScreen(subtitle = null, prices = prices, onClose = { showPlans = false })
        else -> LimitSheet(current, waitMs, prices, canBuy, onShowPlans = { showPlans = true })
    }
}

/**
 * "You've finished today's free lessons" (coral, then the plans screen) or the same for messages and
 * AI corrections (dark, plans right here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitSheet(
    wall: Paywall,
    waitMs: Long,
    prices: Map<PremiumPlan, PlanPrice>,
    canBuy: Boolean,
    onShowPlans: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val lessons = wall.emoji == "📚"
    ModalBottomSheet(
        onDismissRequest = Premium::dismissPaywall,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = colors.surface,
        scrimColor = Ink.copy(alpha = 0.5f),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(if (lessons) colors.coral else Ink),
            ) {
                if (lessons) {
                    Blob(140.dp, Color.White.copy(alpha = 0.15f), Modifier.align(Alignment.TopEnd).offset(30.dp, (-30).dp))
                } else {
                    Blob(140.dp, colors.neonCyan.copy(alpha = 0.55f), Modifier.align(Alignment.TopEnd).offset(30.dp, (-30).dp))
                    Blob(40.dp, Gold.copy(alpha = 0.9f), Modifier.align(Alignment.TopEnd).offset((-50).dp, 40.dp))
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = if (lessons) 0.22f else 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(iconFor(wall.emoji), null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    Text(wall.title, style = displayStyle(26.sp, lineHeight = 30.sp), color = Color.White)
                    Text(
                        wall.message,
                        color = if (lessons) Color.White.copy(alpha = 0.92f) else SoftLilac,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FreeCountdown(waitMs, wall.freeThing)
                when {
                    !canBuy -> Text(
                        "Subscriptions are not available right now. Please try again later.",
                        color = colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    lessons -> InkButton("Continue with Premium", onShowPlans)
                    else -> {
                        var selected by remember { mutableStateOf(defaultPlan(prices)) }
                        PlanTiles(prices, selected, onSelect = { selected = it })
                        SpeakyPrimaryButton(
                            "Continue with Premium",
                            onClick = { Premium.buy(selected) },
                            leading = SpeakyIcons.Crown,
                        )
                    }
                }
                TextAction("I'll wait", colors.textSecondary, Premium::dismissPaywall)
            }
        }
    }
}

private fun iconFor(emoji: String): ImageVector = when (emoji) {
    "📚" -> SpeakyIcons.Book
    "💬" -> SpeakyIcons.Chat
    "✨" -> SpeakyIcons.Sparkle
    else -> SpeakyIcons.Crown
}

/** "Next free lesson in 05:12:40", ticking every second (on a clock the user can't change). */
@Composable
private fun FreeCountdown(waitMs: Long, freeThing: String) {
    val colors = LocalSpeakMindColors.current
    val shownAt = remember(waitMs) { TimeSource.Monotonic.markNow() }
    var leftMs by remember(waitMs) { mutableStateOf(waitMs) }
    LaunchedEffect(waitMs) {
        while (leftMs > 0) {
            delay(1_000)
            leftMs = waitMs - shownAt.elapsedNow().inWholeMilliseconds
        }
    }
    val left = (leftMs / 1000).coerceAtLeast(0)
    val two = { n: Long -> n.toString().padStart(2, '0') }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.backgroundDark).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (left > 0) "Next ${freeThing.removeSuffix("s")} in" else "Your $freeThing are back!",
            Modifier.weight(1f),
            color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
        if (left > 0) {
            Text("${two(left / 3600)}:${two(left / 60 % 60)}:${two(left % 60)}", style = displayStyle(22.sp), color = colors.textPrimary)
        }
    }
}

// ---------------- Plans ----------------

private fun defaultPlan(prices: Map<PremiumPlan, PlanPrice>) =
    if (prices[PremiumPlan.YEARLY] != null) PremiumPlan.YEARLY else PremiumPlan.MONTHLY

private fun savePercent(prices: Map<PremiumPlan, PlanPrice>): Int? {
    val monthly = prices[PremiumPlan.MONTHLY] ?: return null
    val yearly = prices[PremiumPlan.YEARLY] ?: return null
    if (monthly.micros <= 0) return null
    return (100 - yearly.micros * 100.0 / (monthly.micros * 12)).roundToInt().takeIf { it > 0 }
}

/** Yearly and monthly side by side; the chosen one has the violet frame. */
@Composable
private fun PlanTiles(prices: Map<PremiumPlan, PlanPrice>, selected: PremiumPlan, onSelect: (PremiumPlan) -> Unit) {
    val save = savePercent(prices)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        prices[PremiumPlan.YEARLY]?.let {
            PlanTile(
                "Yearly", it.formatted, if (save != null) "Save $save% vs monthly" else "Best value",
                badge = save?.let { p -> "SAVE $p%" },
                selected = selected == PremiumPlan.YEARLY,
                onClick = { onSelect(PremiumPlan.YEARLY) },
                modifier = Modifier.weight(1f),
            )
        }
        prices[PremiumPlan.MONTHLY]?.let {
            PlanTile(
                "Monthly", it.formatted, "Cancel anytime",
                badge = null,
                selected = selected == PremiumPlan.MONTHLY,
                onClick = { onSelect(PremiumPlan.MONTHLY) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlanTile(
    name: String,
    price: String,
    note: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpeakMindColors.current
    val shape = RoundedCornerShape(18.dp)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (selected) colors.primarySoft.copy(alpha = 0.6f) else colors.surface)
                .border(2.dp, if (selected) colors.neonCyan else colors.track, shape)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(name, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(price, style = displayStyle(22.sp), color = colors.textPrimary, maxLines = 1)
            Text(note, color = colors.textMuted, fontSize = 12.sp)
        }
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .offset(x = 12.dp, y = (-11).dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.coral)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Full-screen Speaky Premium: what it includes, then the plans and the store purchase. */
@Composable
private fun PremiumPlansScreen(subtitle: String?, prices: Map<PremiumPlan, PlanPrice>, onClose: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val canBuy = Premium.canBuy && prices.isNotEmpty()
    var selected by remember { mutableStateOf(defaultPlan(prices)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            Blob(220.dp, colors.neonCyan.copy(alpha = 0.5f), Modifier.offset((-60).dp, (-40).dp))
            Blob(120.dp, Gold.copy(alpha = 0.25f), Modifier.align(Alignment.TopEnd).offset(40.dp, 90.dp))
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SpeakyIconButton(SpeakyIcons.Close, "Close", onClose, background = Color.White.copy(alpha = 0.1f), tint = Color.White)
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Gold),
                        contentAlignment = Alignment.Center,
                    ) { Icon(SpeakyIcons.Crown, null, tint = OnGold, modifier = Modifier.size(34.dp)) }
                    Text("Speaky Premium", style = displayStyle(34.sp, lineHeight = 36.sp), color = Color.White)
                    Text(
                        subtitle ?: "Learn without limits and stand out in the community.",
                        color = SoftLilac, fontSize = 15.sp, lineHeight = 22.sp,
                    )
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PERKS.forEach { perk ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x2E19B88A)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(SpeakyIcons.Check, null, tint = Mint, modifier = Modifier.size(16.dp)) }
                                Spacer(Modifier.size(12.dp))
                                Text(perk, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .background(colors.surface)
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (canBuy) {
                        PlanTiles(prices, selected, onSelect = { selected = it })
                        val price = prices[selected]?.formatted
                        SpeakyPrimaryButton(
                            if (selected == PremiumPlan.YEARLY) "Start yearly" + (price?.let { " · $it" } ?: "")
                            else "Start monthly" + (price?.let { " · $it" } ?: ""),
                            onClick = { Premium.buy(selected) },
                        )
                        Text(
                            "Cancel anytime in Google Play",
                            color = colors.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            "Subscriptions are not available right now. Please try again later.",
                            color = colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        TextAction("Close", colors.textSecondary, onClose)
                    }
                }
            }
        }
    }
}

// ---------------- After buying ----------------

@Composable
private fun PremiumWelcomeHost() {
    val show by Premium.welcome.collectAsState()
    if (show) PremiumWelcome(Premium::dismissWelcome)
}

/** "You're Premium": confetti and a glowing crown right after a purchase. */
@Composable
private fun PremiumWelcome(onDone: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val transition = rememberInfiniteTransition()
    val glow by transition.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)))
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            Confetti(listOf(Gold, colors.neonCyan, colors.coral, Color(0xFF19B88A), Color.White, Color(0xFFE0408A)))
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxSize().scale(glow).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.55f), Gold.copy(alpha = 0f)))),
                        )
                        Canvas(Modifier.fillMaxSize().rotate(spin).alpha(0.6f)) {
                            val c = size.width / 2
                            repeat(8) { i ->
                                rotate(i * 45f) {
                                    drawLine(Gold, Offset(c, 6.dp.toPx()), Offset(c, 22.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                                }
                            }
                        }
                        Box(
                            Modifier.size(120.dp).clip(CircleShape).background(Gold.copy(alpha = 0.25f)).padding(8.dp)
                                .clip(CircleShape).background(Gold),
                            contentAlignment = Alignment.Center,
                        ) { Icon(SpeakyIcons.Crown, null, tint = OnGold, modifier = Modifier.size(54.dp)) }
                    }
                    Text("CONGRATULATIONS!", color = Color(0xFFF5C94A), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("You're Premium", style = displayStyle(36.sp, lineHeight = 38.sp), color = Color.White, textAlign = TextAlign.Center)
                    Text(
                        "Thank you for supporting Speaky. Everything is unlocked.",
                        color = SoftLilac, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                    )
                    val unlocked = listOf("Unlimited messages", "Who is online", "All lessons", "No ads")
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        unlocked.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { item ->
                                    Row(
                                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(SpeakyIcons.Check, null, tint = Mint, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(8.dp))
                                        Text(item, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                SpeakyPrimaryButton(
                    "Let's go", onClick = onDone, background = Gold, color = Color(0xFF3A2500),
                    modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
                )
            }
        }
    }
}

/** 40 falling pieces, as in the design (positions spread by index, not random, so they never clump). */
@Composable
private fun BoxScope.Confetti(palette: List<Color>) {
    val transition = rememberInfiniteTransition()
    val time by transition.animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing)))
    Canvas(Modifier.matchParentSize()) {
        repeat(40) { i ->
            val duration = 3.5f + (i % 5) * 0.6f
            val t = ((time + (i % 10) * 0.35f) % duration) / duration
            val w = (6 + (i % 4) * 2).dp.toPx()
            val round = i % 3 == 0
            val h = if (round) w else w * 1.8f
            val x = ((i * 37) % 390) / 390f * size.width
            val y = -60.dp.toPx() + t * (size.height + 120.dp.toPx())
            rotate(t * 720f, Offset(x + w / 2, y + h / 2)) {
                drawRoundRect(
                    color = palette[i % palette.size],
                    topLeft = Offset(x, y),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(if (round) w / 2 else 2.dp.toPx()),
                )
            }
        }
    }
}

// ---------------- Small pieces ----------------

/** Dark button with the gold crown (inverted in dark mode so it still stands out). */
@Composable
private fun InkButton(text: String, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.textPrimary)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SpeakyIcons.Crown, null, tint = Gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, color = colors.surface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TextAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Blob(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
