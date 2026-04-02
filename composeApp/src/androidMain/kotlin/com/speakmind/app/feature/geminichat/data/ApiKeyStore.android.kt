package com.speakmind.app.feature.geminichat.data

import android.content.Context

actual class ApiKeyStore(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("speaky_prefs", Context.MODE_PRIVATE)
    }

    actual fun saveKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    actual fun getKey(): String? {
        return prefs.getString("gemini_api_key", null)?.takeIf { it.isNotBlank() }
    }

    actual fun hasKey(): Boolean = getKey() != null

    actual fun clearKey() {
        prefs.edit().remove("gemini_api_key").apply()
    }
}
