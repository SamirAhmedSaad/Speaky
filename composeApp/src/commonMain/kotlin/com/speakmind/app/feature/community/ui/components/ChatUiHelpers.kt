package com.speakmind.app.feature.community.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun OnlineDot(online: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val colors = LocalSpeakMindColors.current
    Box(
        modifier = modifier
            .size(size)
            .background(if (online) colors.success else colors.textMuted.copy(alpha = 0.6f), CircleShape)
            .border(size / 5, colors.surface, CircleShape),
    )
}

fun formatClock(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

/** "14:05" today, otherwise "12/10". */
fun formatListTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val tz = TimeZone.currentSystemDefault()
    val then = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val today = Clock.System.now().toLocalDateTime(tz)
    return if (then.date == today.date) formatClock(epochMillis)
    else "${then.dayOfMonth.toString().padStart(2, '0')}/${then.monthNumber.toString().padStart(2, '0')}"
}

fun formatLastSeen(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Offline"
    val minutes = (Clock.System.now().toEpochMilliseconds() - epochMillis) / 60_000
    return when {
        minutes < 2 -> "Last seen just now"
        minutes < 60 -> "Last seen ${minutes}m ago"
        minutes < 24 * 60 -> "Last seen ${minutes / 60}h ago"
        minutes < 7 * 24 * 60 -> "Last seen ${minutes / (24 * 60)}d ago"
        else -> "Last seen ${formatListTime(epochMillis)}"
    }
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
        0 -> "Today"
        1 -> "Yesterday"
        else -> {
            val weekday = day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val month = day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$weekday, ${day.dayOfMonth} $month" + if (day.year != today.year) " ${day.year}" else ""
        }
    }
}

/** Calendar day key used to group messages under date separators. */
fun dayKey(epochMillis: Long): Int =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays()
