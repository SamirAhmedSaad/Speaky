package com.speakmind.app.feature.course.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

@Composable
actual fun rememberYouTubeOpener(): (url: String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return { url -> uriHandler.openUri(url) }
}
