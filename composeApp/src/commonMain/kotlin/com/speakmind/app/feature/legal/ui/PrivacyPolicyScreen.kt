package com.speakmind.app.feature.legal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.PrivacyPolicyDestination
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.theme.LocalSpeakMindColors
import network.chaintech.sdpcomposemultiplatform.sdp
import network.chaintech.sdpcomposemultiplatform.ssp

fun NavGraphBuilder.privacyPolicyScreen(onBack: () -> Unit) {
    animatedComposable<PrivacyPolicyDestination> {
        PrivacyPolicyContent(onBack = onBack)
    }
}

@Composable
private fun PrivacyPolicyContent(onBack: () -> Unit) {
    val colors = LocalSpeakMindColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.sdp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 52.sdp, bottom = 8.sdp)
                        .padding(horizontal = 4.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                        )
                    }
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            item {
                PolicySection(
                    title = "Last updated: March 2026",
                    body = "Speaky (\"we\", \"our\", or \"us\") operates the Speaky Ai mobile application. This page informs you of our policies regarding the collection, use, and disclosure of personal data when you use our app.",
                )
            }

            item {
                PolicySection(
                    title = "Information We Collect",
                    body = "Voice Input: When you use voice features, audio is processed entirely on your device. It is never recorded, uploaded, or sent to our servers or any external service.\n\nUsage Data: We collect anonymous data about how you interact with the app (screens visited, features used, session duration) to improve the experience. This data cannot be used to identify you.\n\nDevice Information: We may collect basic technical information such as device type, operating system version, and app version for crash reporting and performance monitoring.",
                )
            }

            item {
                PolicySection(
                    title = "Advertising",
                    body = "We use Google AdMob to display ads in the app. AdMob may collect device identifiers (such as Android Advertising ID), IP address, and usage data to serve personalized or non-personalized ads.\n\nYou can opt out of personalized ads at any time through your device settings: Settings → Google → Ads → Opt out of Ads Personalization.",
                )
            }

            item {
                PolicySection(
                    title = "Analytics",
                    body = "We use Firebase Analytics to understand how users interact with the app. This service collects anonymized usage statistics. No personally identifiable information is collected through analytics.",
                )
            }

            item {
                PolicySection(
                    title = "Data Storage",
                    body = "All your learning data — flashcards, progress, conversation history, and saved words — is stored locally on your device. We do not upload or store your personal learning data on our servers.",
                )
            }

            item {
                PolicySection(
                    title = "Third-Party Services",
                    body = "Our app uses the following third-party services, each with their own privacy policies:\n\n• Google AdMob — advertising\n• Firebase Analytics — anonymous usage statistics\n• Firebase Crashlytics — crash reporting",
                )
            }

            item {
                PolicySection(
                    title = "Children's Privacy",
                    body = "Speaky is not intended for children under the age of 13. We do not knowingly collect personally identifiable information from children under 13. If you are a parent or guardian and believe your child has used the app, please contact us at privacy@speaky.app and we will take steps to remove any associated data. The voice processing is fully on-device and no audio is transmitted externally.",
                )
            }

            item {
                PolicySection(
                    title = "Your Rights",
                    body = "You may opt out of personalized advertising at any time via device settings. Since all learning data is stored locally on your device, you can delete it at any time by uninstalling the app.",
                )
            }

            item {
                PolicySection(
                    title = "Contact Us",
                    body = "If you have any questions about this Privacy Policy, please contact us at:\n\nprivacy@speaky.app",
                )
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    val colors = LocalSpeakMindColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.sdp)
            .padding(bottom = 16.sdp)
            .clip(RoundedCornerShape(12.sdp))
            .background(colors.surface)
            .padding(16.sdp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = colors.neonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.ssp,
            ),
        )
        Spacer(modifier = Modifier.height(8.sdp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colors.textSecondary,
                fontSize = 12.ssp,
                lineHeight = 20.ssp,
            ),
        )
    }
}
