package com.speakmind.app.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

@Composable
actual fun rememberAppVersion(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

/** iOS builds don't report a build number yet (no voice calls there). */
actual fun appVersionCode(): Int = 0
