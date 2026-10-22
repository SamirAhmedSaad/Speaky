package com.speakmind.app.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import kotlin.math.roundToInt

private val Violet = Color(0xFF7C3AED)
private val Pink = Color(0xFFDB2777)
private val Amber = Color(0xFFF59E0B)

/** Shown (from AppRoot) before ads: "watch a video" (free) or Speaky Premium. */
@Composable
fun RemoveAdsHost() {
    val loading by Premium.loadingAd.collectAsState()
    if (loading) LoadingVideoDialog()
    val choice by Premium.adChoice.collectAsState()
    val current = choice ?: return
    val prices by Premium.prices.collectAsState()
    RemoveAdsDialog(
        unlock = current.unlockLesson,
        title = current.title,
        prices = if (Premium.canBuy) prices else emptyMap(),
        onPlan = { plan -> Premium.purchase?.invoke(plan) },
        onWatchAd = Premium::watchAd,
        onDismiss = Premium::dismissChoice,
    )
}

@Composable
private fun RemoveAdsDialog(
    unlock: Boolean,
    title: String,
    prices: Map<PremiumPlan, PlanPrice>,
    onPlan: (PremiumPlan) -> Unit,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    // Back / outside tap: a forced ad still plays; an unlock offer is just declined.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.sdp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.sdp))
                .background(colors.surface)
                .verticalScroll(rememberScrollState()),
        ) {
            Header(
                emoji = if (unlock) "🔓" else "✨",
                title = if (unlock) title.ifBlank { "Unlock" } else "Learn without ads",
                subtitle = if (unlock) "Free with a short video" else "Go Premium or watch a short ad",
                onClose = onDismiss,
            )

            Column(Modifier.padding(horizontal = 20.sdp, vertical = 18.sdp)) {
                WatchButton(
                    title = if (unlock) "Watch a short video" else "Watch ad and continue",
                    hint = "About 30 seconds",
                    onClick = onWatchAd,
                )

                if (prices.isNotEmpty()) {
                    OrDivider()
                    PremiumCard(prices = prices, onPlan = onPlan)
                }

                if (unlock) {
                    Text(
                        "Not now",
                        style = MaterialTheme.typography.titleSmall.copy(color = colors.textMuted, fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.sdp)
                            .clip(RoundedCornerShape(12.sdp))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 8.sdp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(emoji: String, title: String, subtitle: String, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Violet, Pink, Amber)))
            .padding(top = 26.sdp, bottom = 22.sdp, start = 20.sdp, end = 20.sdp),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(32.sdp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.sdp))
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.sdp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(2.sdp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = MaterialTheme.typography.headlineLarge)
            }
            Spacer(Modifier.height(12.sdp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.sdp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.9f)),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WatchButton(title: String, hint: String, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.sdp))
            .background(Brush.horizontalGradient(listOf(colors.neonCyan, colors.neonCyanDark)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp, vertical = 14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.sdp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.sdp))
        }
        Spacer(Modifier.width(12.sdp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            Text(hint, style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.85f)))
        }
        Text(
            "FREE",
            style = MaterialTheme.typography.labelSmall.copy(color = colors.neonCyanDark, fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 8.sdp, vertical = 3.sdp),
        )
    }
}

@Composable
private fun OrDivider() {
    val colors = LocalSpeakMindColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 14.sdp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = colors.textMuted.copy(alpha = 0.25f))
        Text(
            "or",
            style = MaterialTheme.typography.labelLarge.copy(color = colors.textMuted),
            modifier = Modifier.padding(horizontal = 12.sdp),
        )
        HorizontalDivider(Modifier.weight(1f), color = colors.textMuted.copy(alpha = 0.25f))
    }
}

@Composable
private fun PremiumCard(prices: Map<PremiumPlan, PlanPrice>, onPlan: (PremiumPlan) -> Unit) {
    val colors = LocalSpeakMindColors.current
    val monthly = prices[PremiumPlan.MONTHLY]
    val yearly = prices[PremiumPlan.YEARLY]
    val savePercent = if (monthly != null && yearly != null && monthly.micros > 0) {
        (100 - yearly.micros * 100.0 / (monthly.micros * 12)).roundToInt().takeIf { it > 0 }
    } else null
    var selected by remember { mutableStateOf(if (yearly != null) PremiumPlan.YEARLY else PremiumPlan.MONTHLY) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.sdp))
            .background(Amber.copy(alpha = 0.08f))
            .border(1.5.sdp, Amber.copy(alpha = 0.45f), RoundedCornerShape(20.sdp))
            .padding(14.sdp),
        verticalArrangement = Arrangement.spacedBy(10.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👑", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.sdp))
            Column {
                Text(
                    "Speaky Premium",
                    style = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    "Never see an ad again",
                    style = MaterialTheme.typography.labelMedium.copy(color = colors.textSecondary),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.sdp)) {
            listOf("🚫 No ads", "🔓 All unlocked", "❤️ Support us").forEach { Chip(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
            yearly?.let {
                PlanTile(
                    title = "Yearly",
                    price = it.formatted,
                    period = "/ year",
                    badge = savePercent?.let { p -> "Save $p%" } ?: "Best value",
                    selected = selected == PremiumPlan.YEARLY,
                    onClick = { selected = PremiumPlan.YEARLY },
                    modifier = Modifier.weight(1f),
                )
            }
            monthly?.let {
                PlanTile(
                    title = "Monthly",
                    price = it.formatted,
                    period = "/ month",
                    badge = null,
                    selected = selected == PremiumPlan.MONTHLY,
                    onClick = { selected = PremiumPlan.MONTHLY },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.sdp))
                .background(Brush.horizontalGradient(listOf(Violet, Pink, Amber)))
                .clickable { onPlan(selected) }
                .padding(vertical = 14.sdp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue with Premium", style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
        }
        Text(
            "Cancel anytime in Google Play",
            style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Chip(text: String) {
    val colors = LocalSpeakMindColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceVariant)
            .padding(horizontal = 8.sdp, vertical = 4.sdp),
    )
}

@Composable
private fun PlanTile(
    title: String,
    price: String,
    period: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpeakMindColors.current
    val shape = RoundedCornerShape(16.sdp)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.sdp)
                .clip(shape)
                .background(if (selected) Amber.copy(alpha = 0.16f) else colors.surface)
                .border(if (selected) 2.sdp else 1.sdp, if (selected) Amber else colors.textMuted.copy(alpha = 0.3f), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.sdp, vertical = 14.sdp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.sdp))
            Text(price, style = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary, fontWeight = FontWeight.ExtraBold), maxLines = 1)
            Text(period, style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted))
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Pink, Amber)))
                    .padding(horizontal = 8.sdp, vertical = 2.sdp),
            )
        }
    }
}

@Composable
private fun LoadingVideoDialog() {
    val colors = LocalSpeakMindColors.current
    // Back cancels: the user stays on the current screen.
    Dialog(onDismissRequest = { Premium.setLoadingAd(false) }, properties = DialogProperties(dismissOnClickOutside = false)) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(20.sdp)).background(colors.surface).padding(horizontal = 24.sdp, vertical = 20.sdp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(color = colors.neonCyan, modifier = Modifier.size(24.sdp), strokeWidth = 3.sdp)
            Spacer(Modifier.width(14.sdp))
            Text("Loading video…", style = MaterialTheme.typography.titleSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
        }
    }
}
