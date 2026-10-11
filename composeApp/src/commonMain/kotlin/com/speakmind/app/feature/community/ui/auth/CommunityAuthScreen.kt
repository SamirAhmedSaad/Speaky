package com.speakmind.app.feature.community.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.config.AppConfig
import com.speakmind.app.navigation.CommunityAuthDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.communityAuthScreen() {
    animatedComposable<CommunityAuthDestination> {
        val viewModel = koinViewModel<CommunityAuthViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val googleSignIn = rememberGoogleSignIn(AppConfig.googleWebClientId)
        CommunityAuthContent(
            uiState = uiState,
            onBack = viewModel::onBack,
            onGoogle = { viewModel.onGoogleSignIn(googleSignIn) },
            onGuest = viewModel::onContinueAsGuest,
        )
    }
}

@Composable
private fun CommunityAuthContent(
    uiState: CommunityAuthUiState,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onGuest: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.sdp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 6.sdp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        Spacer(Modifier.weight(1f))
        Text("🌍", fontSize = 48.ssp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.sdp))
        Text(
            "Join the community",
            style = MaterialTheme.typography.headlineSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Practise English with learners around the world.",
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(top = 6.sdp, bottom = 28.sdp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.sdp)
                .clip(RoundedCornerShape(25.sdp))
                .background(Color.White)
                .clickable(enabled = !uiState.isLoading) { onGoogle() },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                Text("G", fontSize = 18.ssp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                Text("Continue with Google", color = Color(0xFF1F1F1F), fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Full access: private chats, groups, and voice messages.",
            style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.sdp, bottom = 18.sdp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.sdp)
                .clip(RoundedCornerShape(25.sdp))
                .border(1.sdp, colors.neonCyan.copy(alpha = 0.6f), RoundedCornerShape(25.sdp))
                .clickable(enabled = !uiState.isLoading) { onGuest() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue as guest", color = colors.neonCyan, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Guests can read everything and chat in Speaky Hub.",
            style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.sdp),
        )

        Box(Modifier.fillMaxWidth().height(48.sdp), contentAlignment = Alignment.Center) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.size(24.sdp), color = colors.neonCyan, strokeWidth = 2.sdp)
                uiState.error != null -> Text(
                    uiState.error,
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.error, textAlign = TextAlign.Center),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Keep it friendly. Abuse can be reported and leads to removal.",
            style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.sdp),
        )
    }
}
