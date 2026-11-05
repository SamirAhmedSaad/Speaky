package com.speakmind.app.platform

import androidx.compose.runtime.Composable

/** The installed app's version name ("1.4.0"), for Settings. */
@Composable
expect fun rememberAppVersion(): String

/** The installed build number (versionCode); 0 where unknown. Sent with the push token. */
expect fun appVersionCode(): Int
