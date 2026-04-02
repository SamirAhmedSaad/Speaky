package com.speakmind.app.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import network.chaintech.sdpcomposemultiplatform.sdp
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.TtsSpeedManager
import org.koin.compose.koinInject

enum class TtsSpeedButtonStyle { Default, Pill, Chip }

private val TTS_SPEEDS = listOf(
    0.75f to "Slow",
    1.0f  to "Normal",
    1.25f to "Fast",
    1.5f  to "Faster",
)

@Composable
fun TtsSpeedButton(
    modifier: Modifier = Modifier,
    style: TtsSpeedButtonStyle = TtsSpeedButtonStyle.Default,
    ttsSpeedManager: TtsSpeedManager = koinInject(),
) {
    val colors = LocalSpeakMindColors.current
    val speed by ttsSpeedManager.speed.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val label = "${speed}×".replace(".0×", "×")

    if (showPicker) {
        TtsSpeedPickerDialog(
            currentSpeed = speed,
            onSpeedSelected = {
                ttsSpeedManager.setSpeed(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    when (style) {
        TtsSpeedButtonStyle.Pill -> Row(
            modifier = modifier
                .clip(RoundedCornerShape(20.sdp))
                .background(colors.neonCyan.copy(alpha = 0.12f))
                .clickable { showPicker = true }
                .padding(horizontal = 12.sdp, vertical = 6.sdp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.sdp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.neonCyan,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }

        TtsSpeedButtonStyle.Chip -> Row(
            modifier = modifier
                .clip(RoundedCornerShape(12.sdp))
                .background(colors.neonCyan.copy(alpha = 0.1f))
                .border(1.sdp, colors.neonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.sdp))
                .clickable { showPicker = true }
                .padding(horizontal = 16.sdp, vertical = 10.sdp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.sdp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = colors.neonCyan,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        TtsSpeedButtonStyle.Default -> Box(
            modifier = modifier
                .height(48.sdp)
                .clip(RoundedCornerShape(14.sdp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .border(1.sdp, colors.neonCyan.copy(alpha = 0.3f), RoundedCornerShape(14.sdp))
                .clickable { showPicker = true }
                .padding(horizontal = 14.sdp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = colors.neonCyan,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
fun TtsSpeedPickerDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        title = {
            Text(
                text = "Speech Speed",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                TTS_SPEEDS.forEach { (speed, label) ->
                    val isSelected = currentSpeed == speed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.sdp))
                            .background(
                                if (isSelected) colors.neonCyan.copy(alpha = 0.12f)
                                else Color.Transparent,
                            )
                            .border(
                                width = if (isSelected) 1.5.sdp else 1.sdp,
                                color = if (isSelected) colors.neonCyan.copy(alpha = 0.5f)
                                        else colors.surfaceVariant,
                                shape = RoundedCornerShape(12.sdp),
                            )
                            .clickable { onSpeedSelected(speed) }
                            .padding(horizontal = 16.sdp, vertical = 12.sdp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.sdp)
                                .clip(RoundedCornerShape(10.sdp))
                                .background(colors.neonCyan.copy(alpha = 0.15f)),
                        ) {
                            Text(
                                text = "${speed}×".replace(".0×", "×"),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = colors.neonCyan,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.sdp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}
