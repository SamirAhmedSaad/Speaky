package com.speakmind.app.feature.geminichat.data

import platform.Foundation.NSUserDefaults

actual class ApiKeyStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun saveKey(key: String) {
        defaults.setObject(key.trim(), forKey = "gemini_api_key")
    }

    actual fun getKey(): String? {
        return (defaults.stringForKey("gemini_api_key"))?.takeIf { it.isNotBlank() }
    }

    actual fun hasKey(): Boolean = getKey() != null

    actual fun clearKey() {
        defaults.removeObjectForKey("gemini_api_key")
    }
}
