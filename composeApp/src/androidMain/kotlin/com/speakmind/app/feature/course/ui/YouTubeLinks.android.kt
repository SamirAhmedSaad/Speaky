package com.speakmind.app.feature.course.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

@Composable
actual fun rememberYouTubeOpener(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            val uri = Uri.parse(url)
            val app = Intent(Intent.ACTION_VIEW, uri).setPackage(YOUTUBE_PACKAGE)
            try {
                context.startActivity(app)
            } catch (_: ActivityNotFoundException) {
                // YouTube app missing or disabled: open the link in the browser.
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
    }
}
