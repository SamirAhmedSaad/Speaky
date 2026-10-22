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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/** Shown (from AppRoot) whenever a full-screen ad is about to play and Premium can be bought. */
@Composable
fun RemoveAdsHost() {
    val choice by Premium.adChoice.collectAsState()
    if (choice == null) return
    val prices by Premium.prices.collectAsState()
    RemoveAdsDialog(
        unlockLesson = choice?.unlockLesson == true,
        prices = if (Premium.canBuy) prices else emptyMap(),
        onPlan = { plan -> Premium.purchase?.invoke(plan) },
        onWatchAd = Premium::watchAd,
        onDismiss = Premium::dismissChoice,
    )
}

@Composable
private fun RemoveAdsDialog(
    unlockLesson: Boolean,
    prices: Map<PremiumPlan, PlanPrice>,
    onPlan: (PremiumPlan) -> Unit,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val monthly = prices[PremiumPlan.MONTHLY]
    val yearly = prices[PremiumPlan.YEARLY]
    val savePercent = if (monthly != null && yearly != null && monthly.micros > 0) {
        (100 - yearly.micros * 100.0 / (monthly.micros * 12)).roundToInt().takeIf { it > 0 }
    } else null

    // Back / outside tap: a forced ad still plays; the lesson unlock offer is just declined.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.sdp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.sdp))
                .background(colors.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(colors.magenta, colors.gold)))
                    .padding(vertical = 22.sdp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.sdp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (unlockLesson) "🔓" else "✨", style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(10.sdp))
                    Text(
                        if (unlockLesson) "Unlock this lesson" else "Learn without ads",
                        style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        if (unlockLesson) "Watch a short video, or go Premium" else "Speaky Premium",
                        style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.85f)),
                    )
                }
            }

            Column(Modifier.padding(20.sdp), verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                if (unlockLesson) {
                    WatchButton(onWatchAd)
                    if (prices.isNotEmpty()) {
                        Spacer(Modifier.height(4.sdp))
                        Text(
                            "Or unlock every lesson, with no ads:",
                            style = MaterialTheme.typography.labelLarge.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold),
                        )
                    }
                } else {
                    listOf("No full-screen ads", "No banners", "Lessons open instantly", "Support a small developer ❤️")
                        .forEach { Benefit(it) }
                    Spacer(Modifier.height(6.sdp))
                }
                if (yearly != null) {
                    PlanButton(
                        title = "Yearly",
                        price = "${yearly.formatted} / year",
                        badge = savePercent?.let { "Save $it%" },
                        highlighted = true,
                        onClick = { onPlan(PremiumPlan.YEARLY) },
                    )
                }
                if (monthly != null) {
                    PlanButton(
                        title = "Monthly",
                        price = "${monthly.formatted} / month",
                        badge = null,
                        highlighted = yearly == null,
                        onClick = { onPlan(PremiumPlan.MONTHLY) },
                    )
                }
                if (prices.isNotEmpty()) {
                    Text(
                        "Cancel anytime in Google Play.",
                        style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = if (unlockLesson) onDismiss else onWatchAd, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (unlockLesson) "Not now" else "Watch ad and continue",
                        style = MaterialTheme.typography.titleSmall.copy(color = colors.textSecondary, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchButton(onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.neonCyan)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp, vertical = 16.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("▶", style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
        Spacer(Modifier.width(10.sdp))
        Text(
            "Watch a short video to unlock",
            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun Benefit(text: String) {
    val colors = LocalSpeakMindColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.sdp).clip(CircleShape).background(colors.success.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = MaterialTheme.typography.labelLarge.copy(color = colors.success, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.width(10.sdp))
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary))
    }
}

@Composable
private fun PlanButton(title: String, price: String, badge: String?, highlighted: Boolean, onClick: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val shape = RoundedCornerShape(16.sdp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (highlighted) Modifier.background(Brush.horizontalGradient(listOf(colors.magenta, colors.gold)))
                else Modifier.background(colors.surfaceVariant).border(1.5.sdp, colors.gold.copy(alpha = 0.5f), shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.sdp, vertical = 14.sdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = if (highlighted) Color.White else colors.textPrimary
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(color = text, fontWeight = FontWeight.Bold))
            Text(price, style = MaterialTheme.typography.bodyMedium.copy(color = text.copy(alpha = 0.9f)))
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium.copy(color = colors.magenta, fontWeight = FontWeight.Bold),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.sdp, vertical = 4.sdp),
            )
        }
    }
}
