package com.speakmind.app.feature.community.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.feature.community.ui.components.avatarGradientFor
import com.speakmind.app.navigation.CommunitySetupDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.communitySetupScreen() {
    animatedComposable<CommunitySetupDestination> {
        val viewModel = koinViewModel<CommunitySetupViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        CommunitySetupContent(
            uiState = uiState,
            onNicknameChanged = viewModel::onNicknameChanged,
            onGenderSelected = viewModel::onGenderSelected,
            onJoinClicked = viewModel::onJoinClicked,
        )
    }
}

@Composable
private fun CommunitySetupContent(
    uiState: CommunitySetupUiState,
    onNicknameChanged: (String) -> Unit,
    onGenderSelected: (String) -> Unit,
    onJoinClicked: () -> Unit,
) {
    val colors = LocalSpeakMindColors.current

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
        contentAlignment = Alignment.Center,
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(color = colors.neonCyan)
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.sdp),
        ) {
            // Avatar preview — gradient updates live as nickname is typed
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(110.sdp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.neonCyan.copy(alpha = glowAlpha),
                                    Color.Transparent,
                                )
                            )
                        )
                )
                if (uiState.nickname.length >= 1) {
                    val gradient = avatarGradientFor(uiState.nickname)
                    Box(
                        modifier = Modifier
                            .size(70.sdp)
                            .background(Brush.radialGradient(gradient), CircleShape)
                            .border(2.sdp, colors.neonCyan.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = uiState.nickname.first().uppercaseChar().toString(),
                            color = Color.White,
                            fontSize = 28.ssp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    // Placeholder before typing
                    Box(
                        modifier = Modifier
                            .size(70.sdp)
                            .clip(RoundedCornerShape(22.sdp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.neonCyan.copy(alpha = 0.2f),
                                        colors.surfaceVariant,
                                    )
                                )
                            )
                            .border(
                                1.5.sdp,
                                Brush.verticalGradient(
                                    listOf(
                                        colors.neonCyan.copy(alpha = 0.6f),
                                        colors.neonCyan.copy(alpha = 0.2f),
                                    )
                                ),
                                RoundedCornerShape(22.sdp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "👤", fontSize = 28.ssp)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Join the Community",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.sdp))
                Text(
                    text = "Chat with other English learners",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.textSecondary),
                    textAlign = TextAlign.Center,
                )
            }

            // Nickname field
            Column {
                Text(
                    text = "Your nickname",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(bottom = 8.sdp),
                )
                if (uiState.isNicknameFromOnboarding) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.sdp))
                            .background(colors.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.sdp, colors.neonCyan.copy(alpha = 0.2f), RoundedCornerShape(14.sdp))
                            .padding(horizontal = 16.sdp, vertical = 14.sdp),
                    ) {
                        Text(
                            text = uiState.nickname,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.nickname,
                        onValueChange = onNicknameChanged,
                        placeholder = {
                            Text(
                                "Enter your nickname",
                                style = MaterialTheme.typography.bodyLarge.copy(color = colors.textMuted),
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onJoinClicked() }),
                        shape = RoundedCornerShape(14.sdp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.neonCyan,
                            unfocusedBorderColor = colors.neonCyan.copy(alpha = 0.3f),
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.neonCyan,
                            focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Gender selection
            Column {
                Text(
                    text = "I am",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(bottom = 8.sdp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp),
                ) {
                    GenderCard(
                        label = "Male", emoji = "👨", tag = "M",
                        tagColor = colors.neonCyan,
                        isSelected = uiState.gender == "male",
                        onClick = { onGenderSelected("male") },
                        modifier = Modifier.weight(1f),
                    )
                    GenderCard(
                        label = "Female", emoji = "👩", tag = "F",
                        tagColor = colors.magenta,
                        isSelected = uiState.gender == "female",
                        onClick = { onGenderSelected("female") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AnimatedVisibility(visible = uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.error),
                    textAlign = TextAlign.Center,
                )
            }

            Button(
                onClick = onJoinClicked,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.sdp),
                shape = RoundedCornerShape(16.sdp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                ),
                contentPadding = PaddingValues(0.sdp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = if (uiState.isSaving)
                                    listOf(
                                        colors.neonCyan.copy(alpha = 0.4f),
                                        colors.neonCyanDark.copy(alpha = 0.4f),
                                    )
                                else listOf(colors.neonCyan, colors.neonCyanDark),
                            )
                        )
                        .clip(RoundedCornerShape(16.sdp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = colors.backgroundDark,
                            modifier = Modifier.size(22.sdp),
                            strokeWidth = 2.sdp,
                        )
                    } else {
                        Text(
                            text = "Let's Chat!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.backgroundDark,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenderCard(
    label: String,
    emoji: String,
    tag: String,
    tagColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpeakMindColors.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.sdp))
            .background(
                if (isSelected)
                    Brush.verticalGradient(listOf(tagColor.copy(alpha = 0.15f), colors.surfaceVariant))
                else
                    Brush.verticalGradient(
                        listOf(colors.surfaceVariant.copy(alpha = 0.7f), colors.surface.copy(alpha = 0.5f))
                    )
            )
            .border(
                width = if (isSelected) 1.5.sdp else 1.sdp,
                color = if (isSelected) tagColor.copy(alpha = 0.6f) else colors.neonCyan.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.sdp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.sdp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.sdp),
        ) {
            Text(text = emoji, fontSize = 28.ssp)
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = if (isSelected) tagColor else colors.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.sdp))
                    .background(tagColor.copy(alpha = if (isSelected) 0.2f else 0.1f))
                    .padding(horizontal = 8.sdp, vertical = 2.sdp),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = tagColor,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
