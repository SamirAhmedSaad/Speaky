package com.speakmind.app.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp

/** Update prompts drawn above every screen: a bottom banner (optional) or a full screen (mandatory). */
@Composable
fun BoxScope.AppUpdateOverlay() {
    val ui by AppUpdate.ui.collectAsState()
    val message by AppUpdate.message.collectAsState()

    if (ui == AppUpdate.Ui.Required) {
        RequiredUpdateScreen(message)
        return
    }

    val bannerUi = ui.takeIf { it != AppUpdate.Ui.None }
    AnimatedVisibility(
        visible = bannerUi != null,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        // Keep the last state while the exit animation runs.
        val shown = bannerUi ?: AppUpdate.Ui.Available
        UpdateBanner(shown, message)
    }
}

@Composable
private fun UpdateBanner(ui: AppUpdate.Ui, message: String?) {
    val colors = LocalSpeakMindColors.current
    Column(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.sdp, vertical = 12.sdp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp))
            .background(colors.surface)
            .border(1.sdp, colors.track, RoundedCornerShape(16.sdp))
            .padding(start = 14.sdp, end = 6.sdp, top = 10.sdp, bottom = 10.sdp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.sdp).clip(CircleShape).background(colors.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = colors.onPrimarySoft, modifier = Modifier.size(18.sdp))
            }
            Spacer(Modifier.width(10.sdp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (ui) {
                        is AppUpdate.Ui.Downloading -> "Downloading update… ${ui.percent}%"
                        AppUpdate.Ui.ReadyToInstall -> "Update ready"
                        else -> "New version available"
                    },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.ssp,
                )
                Text(
                    text = when (ui) {
                        is AppUpdate.Ui.Downloading -> "You can keep using Speaky"
                        AppUpdate.Ui.ReadyToInstall -> "Restart to finish (a few seconds)"
                        else -> message ?: "Get the latest fixes and features"
                    },
                    color = colors.textSecondary,
                    fontSize = 11.ssp,
                    maxLines = 2,
                )
            }
            when (ui) {
                AppUpdate.Ui.Available, AppUpdate.Ui.ReadyToInstall -> {
                    Button(
                        onClick = AppUpdate::onUpdateClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.neonCyan, contentColor = colors.onPrimary),
                        shape = RoundedCornerShape(12.sdp),
                    ) {
                        Text(if (ui == AppUpdate.Ui.ReadyToInstall) "Restart" else "Update", fontSize = 12.ssp, fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> Unit
            }
            if (ui == AppUpdate.Ui.Available) {
                IconButton(onClick = AppUpdate::dismissOptional) {
                    Icon(Icons.Filled.Close, contentDescription = "Not now", tint = colors.textMuted)
                }
            } else {
                Spacer(Modifier.width(8.sdp))
            }
        }
        if (ui is AppUpdate.Ui.Downloading) {
            Spacer(Modifier.height(8.sdp))
            LinearProgressIndicator(
                progress = { ui.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(end = 8.sdp).clip(RoundedCornerShape(4.sdp)),
                color = colors.neonCyan,
                trackColor = colors.track,
            )
        }
    }
}

@Composable
private fun RequiredUpdateScreen(message: String?) {
    val colors = LocalSpeakMindColors.current
    // Mandatory: back does nothing and touches never reach the screens underneath.
    BackHandler(enabled = true) {}
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.backgroundDark)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 28.sdp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(84.sdp).clip(CircleShape).background(colors.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = colors.onPrimarySoft, modifier = Modifier.size(42.sdp))
            }
            Spacer(Modifier.height(20.sdp))
            Text(
                "Update required",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.ssp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.sdp))
            Text(
                message ?: "This version of Speaky is no longer supported. Update now to keep learning; it only takes a few seconds.",
                color = colors.textSecondary,
                fontSize = 13.ssp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.sdp))
            Button(
                onClick = AppUpdate::onUpdateClicked,
                modifier = Modifier.fillMaxWidth().height(46.sdp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.neonCyan, contentColor = colors.onPrimary),
                shape = RoundedCornerShape(14.sdp),
            ) {
                Text("Update now", fontSize = 14.ssp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
