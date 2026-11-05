package com.speakmind.app.feature.games.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random

private val ConfettiColors = listOf(
    Color(0xFFFF7A45), Color(0xFFFFC53D), Color(0xFF19B88A), Color(0xFF7C5CFF), Color(0xFFFF5DA2), Color(0xFF7DE3BE),
)

private class Piece(val x: Float, val delay: Float, val speed: Float, val w: Float, val h: Float, val spin: Float, val drift: Float, val color: Color, val round: Boolean)

/** A one-off burst of falling confetti over the whole screen; restarts when [key] changes. */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier, count: Int = 70) {
    val progress = remember(key) { Animatable(0f) }
    val pieces = remember(key) {
        List(count) {
            Piece(
                x = Random.nextFloat(),
                delay = Random.nextFloat() * 0.35f,
                speed = 0.9f + Random.nextFloat() * 0.7f,
                w = 7f + Random.nextFloat() * 8f,
                h = 10f + Random.nextFloat() * 10f,
                spin = 360f + Random.nextFloat() * 540f,
                drift = (Random.nextFloat() - 0.5f) * 0.25f,
                color = ConfettiColors.random(),
                round = Random.nextInt(3) == 0,
            )
        }
    }
    LaunchedEffect(key) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -40f + local * p.speed * (size.height + 80f)
            val x = (p.x + p.drift * local) * size.width
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            rotate(p.spin * local, Offset(x, y)) {
                if (p.round) drawCircle(p.color.copy(alpha = alpha), p.w / 2, Offset(x, y))
                else drawRect(p.color.copy(alpha = alpha), Offset(x - p.w / 2, y - p.h / 2), Size(p.w, p.h))
            }
        }
    }
}

/** Shakes sideways each time [trigger] goes up (a wrong answer). */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        for (x in listOf(-22f, 20f, -14f, 9f, -4f, 0f)) offset.animateTo(x, tween(55))
    }
    return graphicsLayer { translationX = offset.value }
}

/** Grows a little and settles each time [trigger] goes up (a right answer). */
@Composable
fun Modifier.pop(trigger: Int): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        scale.animateTo(1.05f, tween(110))
        scale.animateTo(1f, tween(160))
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** Bounces in from small when first shown (scores, badges on the results). */
@Composable
fun Modifier.bounceIn(): Modifier {
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1.15f, tween(320))
        scale.animateTo(0.95f, tween(140))
        scale.animateTo(1f, tween(120))
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
