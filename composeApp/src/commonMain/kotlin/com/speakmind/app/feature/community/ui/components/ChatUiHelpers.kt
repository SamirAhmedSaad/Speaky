package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Green [size] dot in a 3dp ring of [ringColor] (the background behind the avatar); nothing when offline. */
@Composable
fun OnlineDot(online: Boolean, size: Dp, modifier: Modifier = Modifier, ringColor: Color = LocalSpeakMindColors.current.surface) {
    if (!online) return
    Box(
        modifier = modifier
            .size(size + 6.dp)
            .background(ringColor, CircleShape)
            .padding(3.dp)
            .background(OnlineGreen, CircleShape),
    )
}

/** The design's online green (#19B88A). */
val OnlineGreen = Color(0xFF19B88A)

fun formatClock(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = if (local.hour % 12 == 0) 12 else local.hour % 12
    return "$hour:${local.minute.toString().padStart(2, '0')} ${if (local.hour < 12) "AM" else "PM"}"
}

/** "2:05 PM" today, otherwise "12/10". */
fun formatListTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val tz = TimeZone.currentSystemDefault()
    val then = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val today = Clock.System.now().toLocalDateTime(tz)
    return if (then.date == today.date) formatClock(epochMillis)
    else "${then.dayOfMonth.toString().padStart(2, '0')}/${then.monthNumber.toString().padStart(2, '0')}"
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** "Today", "Yesterday", or e.g. "Mon, 12 Oct" for chat date separators. */
fun formatDayLabel(epochMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val day = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date
    return when (today.toEpochDays() - day.toEpochDays()) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> {
            val weekday = day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val month = day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$weekday, ${day.dayOfMonth} $month" + if (day.year != today.year) " ${day.year}" else ""
        }
    }
}

/** Calendar day key used to group messages under date separators. */
fun dayKey(epochMillis: Long): Long =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays()
