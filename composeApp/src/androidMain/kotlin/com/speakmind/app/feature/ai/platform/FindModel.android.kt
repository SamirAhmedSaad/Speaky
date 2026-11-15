package com.speakmind.app.feature.ai.platform

/**
 * The on-device AI engine was removed (the Android LlmEngine is a stub), so there is nothing to find.
 * The old version listed whole shared-storage folders looking for .gguf files, on the main thread when
 * the user tapped Free Talk / Discuss, which caused ANRs on phones with large Download folders.
 */
actual fun findModelFile(): String? = null
