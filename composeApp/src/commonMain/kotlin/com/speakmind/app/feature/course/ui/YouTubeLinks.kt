package com.speakmind.app.feature.course.ui

import androidx.compose.runtime.Composable

/** Opens a YouTube link in the YouTube app when it is installed, otherwise in the browser. */
@Composable
expect fun rememberYouTubeOpener(): (url: String) -> Unit
