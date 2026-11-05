package com.speakmind.app.feature.legal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import com.speakmind.app.navigation.PrivacyPolicyDestination
import com.speakmind.app.ui.components.SpeakyBackButton
import com.speakmind.app.ui.components.animatedComposable
import com.speakmind.app.ui.components.displayStyle
import com.speakmind.app.ui.theme.LocalSpeakMindColors

fun NavGraphBuilder.privacyPolicyScreen(onBack: () -> Unit) {
    animatedComposable<PrivacyPolicyDestination> {
        PrivacyPolicyContent(onBack = onBack)
    }
}

private const val LAST_UPDATED = "October 15, 2026"
private const val INTRO = "Speaky (\"we\", \"our\", or \"us\") operates the Speaky Ai mobile application. This page informs you of our policies regarding the collection, use, and disclosure of personal data when you use our app."

private val SECTIONS = listOf(
    "Information We Collect" to
        "AI tutor: the AI model runs on your device, and your tutor conversations are saved only on your device.\n\nSpeech: speech-to-text uses your phone's built-in speech recognition service (on most phones, Google's), which may process audio on the device or on its provider's servers. Speaky does not keep or upload these recordings itself.\n\nUsage Data: We collect anonymous data about how you interact with the app (screens visited, features used, session duration) to improve the experience. This data cannot be used to identify you.\n\nDevice Information: We may collect basic technical information such as device type, operating system version, and app version for crash reporting and performance monitoring.",
    "Community Chat" to
        "The Community (Speaky Hub and private chats) is optional. If you join it, we store the following on our servers (Supabase):\n\n• Account: your Google account ID, email address and name (from Google sign-in).\n• Profile: the nickname, gender and profile photo you choose, your English level, when you were last online, and when you accepted the community rules.\n• Messages: the text and voice messages you send, replies, reactions, and who you block or report.\n• Notifications: a push-notification token and your Speaky Hub alert setting.\n\nWho can see it: your nickname, photo, level, online status and Speaky Hub messages are visible to other community members. Private chat messages and voice messages are visible only to the people in that chat. Your email is never shown to other users.\n\nModeration: reports you file, and messages that are reported, are reviewed by us to keep the community safe. Accounts that break the rules can be suspended.\n\nRetention: to keep storage limited, the oldest voice messages are deleted automatically when voice storage is nearly full, and the oldest chat messages are deleted once the message limit is reached. Recent messages are also kept on your device so you can read them offline.",
    "Advertising" to
        "We use Unity LevelPlay (Unity Ads and its partner ad networks) to display ads in the app. These services may collect device identifiers (such as the Android Advertising ID), IP address, and usage data to serve ads. In the EEA, UK and Switzerland we only request non-personalized ads.\n\nYou can opt out of personalized ads at any time through your device settings: Settings → Google → Ads → Opt out of Ads Personalization.",
    "Analytics" to
        "We use Firebase Analytics to understand how users interact with the app. This service collects anonymized usage statistics. No personally identifiable information is collected through analytics.",
    "Data Storage" to
        "All your learning data — flashcards, progress, AI tutor conversation history, and saved words — is stored locally on your device. We do not upload or store your personal learning data on our servers. Only Community Chat data (described above) is stored on our servers.",
    "Third-Party Services" to
        "Our app uses the following third-party services, each with their own privacy policies:\n\n• Unity LevelPlay / Unity Ads — advertising\n• Firebase Analytics — anonymous usage statistics\n• Firebase Crashlytics — crash reporting\n• Firebase Cloud Messaging — chat notifications\n• Supabase — community accounts, messages and voice messages\n• Google Sign-In — community sign-in",
    "Children's Privacy" to
        "Speaky is not intended for children under the age of 13. We do not knowingly collect personally identifiable information from children under 13. If you are a parent or guardian and believe your child has used the app, please contact us at samir.hayan@gmail.com and we will delete any associated Community account.",
    "Your Rights" to
        "You may opt out of personalized advertising at any time via device settings. Learning data is stored locally on your device and is deleted when you uninstall the app.\n\nYou can permanently delete your community account at any time in the app: Community → ⋮ → Delete account. This removes your profile, photo, messages, voice messages, reactions and device tokens from our servers. You can also email us to request deletion.",
    "Contact Us" to
        "If you have any questions about this Privacy Policy, please contact us at:\n\nsamir.hayan@gmail.com"
)

@Composable
private fun PrivacyPolicyContent(onBack: () -> Unit) {
    val colors = LocalSpeakMindColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.backgroundDark),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeakyBackButton(onBack)
                Text("Privacy policy", Modifier.padding(start = 10.dp), style = displayStyle(26.sp), color = colors.textPrimary)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Last updated $LAST_UPDATED", color = colors.textMuted, fontSize = 13.sp)
                Text(INTRO, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        itemsIndexed(SECTIONS) { i, (title, body) -> PolicySection(i + 1, title, body) }
        item {
            Text(
                "Questions? samir.hayan@gmail.com",
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PolicySection(number: Int, title: String, body: String) {
    val colors = LocalSpeakMindColors.current
    val ink = if (colors.surface.luminance() < 0.5f) colors.onPrimarySoft else colors.neonCyanDark
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(body, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}
