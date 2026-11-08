package com.speakmind.app.feature.home.ui

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakmind.app.feature.games.ui.Confetti
import com.speakmind.app.feature.games.ui.bounceIn
import com.speakmind.app.feature.streak.StreakEvent
import com.speakmind.app.feature.streak.StreakState
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors

private val FireTop = Color(0xFFFFE27A)
private val FireMid = Color(0xFFFF8A3D)
private val FireBottom = Color(0xFFFF4D4D)
private val NightA = Color(0xFF2B0F5E)
private val NightB = Color(0xFF7A1E6E)

/** The streak screen: animated flame, the current challenge's days, a celebration when one is done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreakSheet(state: StreakState, event: StreakEvent?, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = null,
    ) {
        if (event is StreakEvent.Completed) Celebration(event, onDismiss) else StreakContent(state, event, onDismiss)
    }
}

@Composable
private fun StreakHeader(count: Int) {
    Box(
        Modifier.fillMaxWidth().height(270.dp).background(Brush.verticalGradient(listOf(NightA, NightB))),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.padding(top = 12.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.4f)))
        AnimatedFlame(Modifier.padding(top = 26.dp).size(128.dp))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", style = displayStyle(56.sp, 58.sp), color = Color.White, modifier = Modifier.bounceIn())
            Text(if (count == 1) "day streak" else "day streak", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun AnimatedFlame(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "flame")
    val flicker by t.animateFloat(0f, 1f, infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "flicker")
    val glow by t.animateFloat(0.55f, 0.95f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val spark by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "spark")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB43C).copy(alpha = glow), Color.Transparent), center = Offset(w / 2, h * 0.6f), radius = w * 0.62f), radius = w * 0.62f, center = Offset(w / 2, h * 0.6f))
        // Sparks rising.
        listOf(0.28f to 0f, 0.72f to 0.35f, 0.55f to 0.7f).forEach { (x, phase) ->
            val p = (spark + phase) % 1f
            drawCircle(Color(0xFFFFD66B).copy(alpha = 1f - p), radius = 5f * (1f - p * 0.6f), center = Offset(w * x, h * (0.85f - 0.5f * p)))
        }
        val sx = 1f + 0.05f * flicker
        val sy = 1f - 0.04f * flicker
        fun flame(scale: Float, top: Float): Path = Path().apply {
            val cx = w / 2
            val fw = w * 0.42f * scale * sx
            val bottom = h * 0.95f
            val tip = h * top / sy
            moveTo(cx + fw * 0.05f, tip)
            cubicTo(cx + fw * 0.4f, tip + h * 0.18f, cx + fw * 1.05f, bottom - h * 0.35f, cx + fw * 0.95f, bottom - h * 0.18f)
            cubicTo(cx + fw * 0.9f, bottom + h * 0.02f, cx - fw * 0.9f, bottom + h * 0.02f, cx - fw * 0.95f, bottom - h * 0.18f)
            cubicTo(cx - fw * 1.0f, bottom - h * 0.38f, cx - fw * 0.55f, bottom - h * 0.48f, cx - fw * 0.35f, bottom - h * 0.6f)
            cubicTo(cx - fw * 0.25f, bottom - h * 0.45f, cx - fw * 0.05f, bottom - h * 0.42f, cx + fw * 0.02f, bottom - h * 0.44f)
            cubicTo(cx - fw * 0.15f, tip + h * 0.25f, cx - fw * 0.1f, tip + h * 0.1f, cx + fw * 0.05f, tip)
            close()
        }
        drawPath(flame(1f, 0.06f), Brush.verticalGradient(listOf(FireTop, FireMid, FireBottom), startY = h * 0.05f, endY = h * 0.95f))
        drawPath(flame(0.48f, 0.5f), Brush.verticalGradient(listOf(Color.White, FireTop), startY = h * 0.5f, endY = h * 0.95f))
    }
}

@Composable
private fun StreakContent(state: StreakState, event: StreakEvent?, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val (headline, sub) = when (state.stage) {
        0 -> "You’re on fire!" to "Days in a row make English stick. Don’t break the chain!"
        1 -> "Two weeks, let’s go!" to "You beat the week. Now prove it’s a habit."
        else -> "Month mode!" to "The big one. Every day counts, keep showing up."
    }
    val (badgeBg, badgeFg) = when (state.stage) {
        0 -> Color(0xFFECE8FF) to Color(0xFF2E1E9E)
        1 -> Color(0xFFE0ECFF) to Color(0xFF1E4FA8)
        else -> Color(0xFFFFF4D6) to Color(0xFF7A4A00)
    }
    Column {
        StreakHeader(state.streak)
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (event is StreakEvent.Saved) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFF1E6)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("⚠️ Streak saved!", color = Color(0xFFC2410C), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text("You missed a day. Miss another one and you start again at the week challenge.", color = Color(0xFF7C2D12), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Text(
                state.stageName, Modifier.clip(RoundedCornerShape(999.dp)).background(badgeBg).padding(horizontal = 14.dp, vertical = 6.dp),
                color = badgeFg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(headline, style = displayStyle(22.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(sub, color = colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
            DayGrid(state)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Day ${state.inStage} of ${state.goal}", Modifier.weight(1f), color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${state.goal - state.inStage} to go", color = Color(0xFFE8590C), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(999.dp)).background(colors.track)) {
                    Box(
                        Modifier.fillMaxWidth(state.inStage / state.goal.toFloat()).fillMaxHeight().clip(RoundedCornerShape(999.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFFB13D), Color(0xFFFF5A5A)))),
                    )
                }
            }
            FireButton("Keep the fire going 🔥", onDismiss)
            Text("Miss a day? You get one more chance.", color = colors.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DayGrid(state: StreakState) {
    val colors = LocalSpeakMindColors.current
    val perRow = if (state.goal == 30) 10 else 7
    val dot = if (state.goal == 30) 26.dp else 34.dp
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.backgroundDark).padding(vertical = 14.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0 until state.goal).chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { i ->
                    val done = i < state.inStage
                    val today = i == state.inStage - 1
                    Box(
                        Modifier.size(dot).clip(CircleShape)
                            .background(if (done) Brush.linearGradient(listOf(FireTop, FireMid)) else Brush.linearGradient(listOf(colors.track, colors.track)))
                            .then(if (today) Modifier.border(2.dp, FireMid, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { if (done) Text("🔥", fontSize = if (state.goal == 30) 11.sp else 15.sp) }
                }
            }
        }
    }
}

@Composable
private fun FireButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF8A3D), Color(0xFFFF5A5A))))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun Celebration(event: StreakEvent.Completed, onDismiss: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    val title = when (event.stage) { 0 -> "Week complete! 🎉"; 1 -> "Two weeks done! 🎉"; else -> "Month complete! 👑" }
    val next = StreakState.nameOf((event.stage + 1).coerceAtMost(2))
    Box {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFFFF4D6), colors.surface)))
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("🏆", fontSize = 88.sp, modifier = Modifier.bounceIn())
            Text(title, style = displayStyle(28.sp), color = colors.textPrimary, textAlign = TextAlign.Center)
            Text("${event.streak} days in a row. That’s a real habit, be proud!", color = colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
            Text(
                "Next: $next", Modifier.clip(RoundedCornerShape(999.dp)).background(colors.textPrimary).padding(horizontal = 14.dp, vertical = 8.dp),
                color = colors.surface, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            FireButton("Start the next challenge", onDismiss)
        }
        Confetti(key = event.streak, modifier = Modifier.matchParentSize())
    }
}
