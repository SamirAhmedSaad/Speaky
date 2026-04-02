package com.speakmind.app.feature.aisetup.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.AiSetupDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import com.speakmind.app.ui.theme.SpeakMindThemeColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

fun NavGraphBuilder.aiSetupScreen() {
    animatedComposable<AiSetupDestination> { backStackEntry ->
        val scenarioId = backStackEntry.arguments?.getString("scenarioId")
        val viewModel = koinViewModel<AiSetupViewModel> { parametersOf(scenarioId) }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        AiSetupContent(
            uiState = uiState,
            onKeyInputChanged = viewModel::onKeyInputChanged,
            onSaveGeminiKey = viewModel::onSaveGeminiKey,
            onContinueWithGemini = viewModel::onContinueWithGemini,
            onChangeKey = viewModel::onChangeKey,
            onCancelChangeKey = viewModel::onCancelChangeKey,
            onDownloadLocalModel = viewModel::onDownloadLocalModel,
            onUseLocalModel = viewModel::onUseLocalModel,
            onBack = viewModel::onBack,
        )
    }
}

@Composable
private fun AiSetupContent(
    uiState: AiSetupUiState,
    onKeyInputChanged: (String) -> Unit,
    onSaveGeminiKey: () -> Unit,
    onContinueWithGemini: () -> Unit,
    onChangeKey: () -> Unit,
    onCancelChangeKey: () -> Unit,
    onDownloadLocalModel: () -> Unit,
    onUseLocalModel: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.sdp)
                .padding(top = 100.sdp, bottom = 40.sdp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Choose Your AI",
                fontSize = 28.ssp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(6.sdp))
            Text(
                text = "Power your English tutor",
                fontSize = 15.ssp,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(32.sdp))

            // ─── Gemini API Card ───
            GeminiCard(
                uiState = uiState,
                onKeyInputChanged = onKeyInputChanged,
                onSaveKey = onSaveGeminiKey,
                onContinue = onContinueWithGemini,
                onChangeKey = onChangeKey,
                onCancelChangeKey = onCancelChangeKey,
                onPaste = {
                    val text = clipboardManager.getText()?.text ?: ""
                    onKeyInputChanged(text)
                },
                onOpenKeyUrl = {
                    uriHandler.openUri("https://aistudio.google.com/app/apikey")
                },
            )

            Spacer(Modifier.height(20.sdp))

            // ─── OR Divider ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = colors.surfaceVariant)
                Text(
                    "  or  ",
                    color = colors.textSecondary,
                    fontSize = 13.ssp,
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = colors.surfaceVariant)
            }

            Spacer(Modifier.height(20.sdp))

            // ─── Local AI Card ───
            LocalAiCard(
                localModelExists = uiState.localModelExists,
                onDownload = onDownloadLocalModel,
                onUse = onUseLocalModel,
            )
        }

        // Back button drawn last so it sits on top of the scrollable column
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(top = 48.sdp, start = 8.sdp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.textPrimary
            )
        }
    }
}

@Composable
private fun GeminiCard(
    uiState: AiSetupUiState,
    onKeyInputChanged: (String) -> Unit,
    onSaveKey: () -> Unit,
    onContinue: () -> Unit,
    onChangeKey: () -> Unit,
    onCancelChangeKey: () -> Unit,
    onPaste: () -> Unit,
    onOpenKeyUrl: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.sdp))
            .border(1.sdp, colors.neonCyan.copy(alpha = 0.35f), RoundedCornerShape(20.sdp)),
        color = colors.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.sdp),
    ) {
        Column(modifier = Modifier.padding(20.sdp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.sdp)
                        .clip(RoundedCornerShape(12.sdp))
                        .background(colors.neonCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.neonCyan,
                        modifier = Modifier.size(22.sdp),
                    )
                }
                Spacer(Modifier.width(12.sdp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Gemini API",
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 17.ssp,
                    )
                    Text(
                        "Smarter · Always up to date",
                        color = colors.textSecondary,
                        fontSize = 12.ssp,
                    )
                }
                // Recommended badge
                Surface(
                    shape = RoundedCornerShape(8.sdp),
                    color = colors.neonCyan.copy(alpha = 0.15f),
                ) {
                    Text(
                        "Recommended",
                        color = colors.neonCyan,
                        fontSize = 11.ssp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.sdp, vertical = 4.sdp),
                    )
                }
            }

            Spacer(Modifier.height(16.sdp))

            val showConnected = uiState.geminiKeyIsSet && !uiState.isChangingKey
            AnimatedContent(
                targetState = showConnected,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { keySet ->
                if (keySet) {
                    // Connected state
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colors.neonCyan,
                                modifier = Modifier.size(18.sdp),
                            )
                            Spacer(Modifier.width(8.sdp))
                            Text(
                                "API key connected",
                                color = colors.neonCyan,
                                fontSize = 14.ssp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = onChangeKey,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.sdp, vertical = 4.sdp),
                            ) {
                                Text(
                                    "Change key",
                                    color = colors.textSecondary,
                                    fontSize = 12.ssp,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.sdp))
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.neonCyan),
                            shape = RoundedCornerShape(14.sdp),
                        ) {
                            Text(
                                "Continue with Gemini",
                                color = Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.sdp),
                            )
                        }
                    }
                } else {
                    // Key entry state (fresh setup OR changing existing key)
                    Column {
                        if (uiState.isChangingKey) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Enter new API key",
                                    color = colors.textSecondary,
                                    fontSize = 13.ssp,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = onCancelChangeKey,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.sdp, vertical = 4.sdp),
                                ) {
                                    Text(
                                        "Cancel",
                                        color = colors.textSecondary,
                                        fontSize = 12.ssp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.sdp))
                        }
                        HowToGetKeySteps(onOpenKeyUrl = onOpenKeyUrl, colors = colors)
                        Spacer(Modifier.height(10.sdp))
                        OutlinedTextField(
                            value = uiState.geminiKeyInput,
                            onValueChange = onKeyInputChanged,
                            placeholder = { Text("Paste your API key here", color = colors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (uiState.geminiKeyInput.length > 8)
                                PasswordVisualTransformation() else VisualTransformation.None,
                            trailingIcon = {
                                IconButton(onClick = onPaste) {
                                    Icon(
                                        Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = colors.textSecondary,
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = colors.textSecondary)
                            },
                            isError = uiState.keyError != null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSaveKey() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.neonCyan,
                                unfocusedBorderColor = colors.surfaceVariant,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.neonCyan,
                            ),
                            shape = RoundedCornerShape(12.sdp),
                        )
                        if (uiState.keyError != null) {
                            Spacer(Modifier.height(6.sdp))
                            Text(
                                uiState.keyError,
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.ssp,
                            )
                        }
                        Spacer(Modifier.height(12.sdp))
                        Button(
                            onClick = onSaveKey,
                            enabled = uiState.geminiKeyInput.isNotBlank() && !uiState.isValidatingKey,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.neonCyan),
                            shape = RoundedCornerShape(14.sdp),
                        ) {
                            if (uiState.isValidatingKey) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.sdp),
                                    color = Color.Black,
                                    strokeWidth = 2.sdp,
                                )
                            } else {
                                Text(
                                    "Save & Continue",
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 4.sdp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HowToGetKeySteps(
    onOpenKeyUrl: () -> Unit,
    colors: SpeakMindThemeColors,
) {
    val steps = listOf(
        Triple("1", "Open Google AI Studio", "Tap the button below — it's free, no credit card needed"),
        Triple("2", "Sign in with Google", "Use any Google account to log in"),
        Triple("3", "Create an API Key", "Tap \"Create API key\" → copy the key it shows you"),
        Triple("4", "Paste it here", "Stored only on your device — never sent to our servers"),
    )

    var expanded by remember { mutableStateOf(false) }

    Column {
        // Collapsed header — always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "How to get your free API key",
                color = colors.neonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = colors.neonCyan,
                modifier = Modifier.size(20.dp),
            )
        }

        // Expandable content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))

                steps.forEach { (number, title, subtitle) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(colors.neonCyan.copy(alpha = 0.15f))
                                .border(1.dp, colors.neonCyan.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                number,
                                color = colors.neonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                subtitle,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onOpenKeyUrl,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.neonCyan.copy(alpha = 0.15f),
                        contentColor = colors.neonCyan,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Open Google AI Studio",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Free • No credit card • Stored only on your device • Never sent to our servers",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalAiCard(
    localModelExists: Boolean,
    onDownload: () -> Unit,
    onUse: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current
    val accentColor = Color(0xFFBB86FC)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.sdp))
            .border(1.sdp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(20.sdp)),
        color = colors.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.sdp),
    ) {
        Column(modifier = Modifier.padding(20.sdp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.sdp)
                        .clip(RoundedCornerShape(12.sdp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.sdp),
                    )
                }
                Spacer(Modifier.width(12.sdp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Local AI",
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 17.ssp,
                    )
                    Text(
                        "Works offline · Private · Free",
                        color = colors.textSecondary,
                        fontSize = 12.ssp,
                    )
                }
                if (localModelExists) {
                    Surface(
                        shape = RoundedCornerShape(8.sdp),
                        color = accentColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "Ready",
                            color = accentColor,
                            fontSize = 11.ssp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.sdp, vertical = 4.sdp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.sdp))

            if (localModelExists) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.sdp),
                    )
                    Spacer(Modifier.width(8.sdp))
                    Text(
                        "Model downloaded",
                        color = accentColor,
                        fontSize = 14.ssp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(16.sdp))
                Button(
                    onClick = onUse,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(14.sdp),
                ) {
                    Text(
                        "Use Local AI",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.sdp),
                    )
                }
            } else {
                Text(
                    "One-time download (~1 GB). Works without internet after.",
                    color = colors.textSecondary,
                    fontSize = 13.ssp,
                )
                Spacer(Modifier.height(12.sdp))
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(14.sdp),
                ) {
                    Text(
                        "Download & Use (~1 GB)",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.sdp),
                    )
                }
            }
        }
    }
}
