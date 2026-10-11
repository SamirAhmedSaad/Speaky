package com.speakmind.app.feature.community.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
            onModeChanged = viewModel::onModeChanged,
            onEmailChanged = viewModel::onEmailChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
            onCodeChanged = viewModel::onCodeChanged,
            onSubmit = viewModel::onSubmit,
            onResendCode = viewModel::onResendCode,
            onGoogle = { viewModel.onGoogleSignIn(googleSignIn) },
        )
    }
}

@Composable
private fun CommunityAuthContent(
    uiState: CommunityAuthUiState,
    onBack: () -> Unit,
    onModeChanged: (AuthMode) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onResendCode: () -> Unit,
    onGoogle: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedBorderColor = colors.neonCyan,
        unfocusedBorderColor = colors.neonCyan.copy(alpha = 0.3f),
        focusedLabelColor = colors.neonCyan,
        unfocusedLabelColor = colors.textMuted,
        cursorColor = colors.neonCyan,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.sdp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 6.sdp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        Spacer(Modifier.height(8.sdp))
        Text("Join the community", style = MaterialTheme.typography.headlineSmall.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
        Text(
            "Sign in to chat with learners around the world. Your account keeps the community safe.",
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.textMuted),
            modifier = Modifier.padding(top = 6.sdp, bottom = 20.sdp),
        )

        // Google
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.sdp)
                .clip(RoundedCornerShape(24.sdp))
                .background(Color.White)
                .clickable(enabled = !uiState.isLoading) { onGoogle() },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
                Text("G", fontSize = 18.ssp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                Text("Continue with Google", color = Color(0xFF1F1F1F), fontWeight = FontWeight.SemiBold)
            }
        }
        if (!uiState.googleEnabled) {
            Text(
                "Google sign-in needs setup (see AppConfig.googleWebClientId).",
                style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted),
                modifier = Modifier.padding(top = 4.sdp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 18.sdp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = colors.textMuted.copy(alpha = 0.3f))
            Text("  or use email  ", style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted))
            HorizontalDivider(Modifier.weight(1f), color = colors.textMuted.copy(alpha = 0.3f))
        }

        // Mode switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.sdp))
                .border(1.sdp, colors.neonCyan.copy(alpha = 0.25f), RoundedCornerShape(20.sdp)),
        ) {
            AuthMode.entries.forEach { mode ->
                val selected = uiState.mode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.sdp))
                        .background(if (selected) colors.neonCyan else Color.Transparent)
                        .clickable { onModeChanged(mode) }
                        .padding(vertical = 9.sdp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (mode) {
                            AuthMode.SIGN_IN -> "Sign in"
                            AuthMode.SIGN_UP -> "Sign up"
                            AuthMode.EMAIL_CODE -> "Email code"
                        },
                        color = if (selected) colors.backgroundDark else colors.textMuted,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.sdp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = { Text("Email") },
            singleLine = true,
            enabled = !uiState.awaitingCode,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!uiState.awaitingCode && uiState.mode != AuthMode.EMAIL_CODE) {
            Spacer(Modifier.height(10.sdp))
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChanged,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.mode == AuthMode.SIGN_UP) {
                Spacer(Modifier.height(10.sdp))
                OutlinedTextField(
                    value = uiState.confirmPassword,
                    onValueChange = onConfirmPasswordChanged,
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (uiState.awaitingCode) {
            Spacer(Modifier.height(10.sdp))
            OutlinedTextField(
                value = uiState.code,
                onValueChange = onCodeChanged,
                label = { Text("6-digit code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onResendCode, enabled = !uiState.isLoading) {
                Text("Resend code", color = colors.neonCyan)
            }
        }

        uiState.info?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(color = colors.neonCyan), modifier = Modifier.padding(top = 10.sdp))
        }
        uiState.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(color = colors.error), modifier = Modifier.padding(top = 10.sdp))
        }

        Spacer(Modifier.height(18.sdp))
        Button(
            onClick = onSubmit,
            enabled = !uiState.isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = colors.neonCyan, contentColor = colors.backgroundDark),
            modifier = Modifier.fillMaxWidth().height(48.sdp),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(Modifier.size(20.sdp), color = colors.backgroundDark, strokeWidth = 2.sdp)
            } else {
                Text(
                    when {
                        uiState.awaitingCode -> "Verify code"
                        uiState.mode == AuthMode.SIGN_IN -> "Sign in"
                        uiState.mode == AuthMode.SIGN_UP -> "Create account"
                        else -> "Send me a code"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            "By continuing you agree to keep the community friendly. Abuse can be reported and leads to removal.",
            style = MaterialTheme.typography.labelSmall.copy(color = colors.textMuted, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.sdp),
        )
    }
}
