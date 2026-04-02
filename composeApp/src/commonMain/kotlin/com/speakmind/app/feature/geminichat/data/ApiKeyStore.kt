package com.speakmind.app.feature.geminichat.data

expect class ApiKeyStore {
    fun saveKey(key: String)
    fun getKey(): String?
    fun hasKey(): Boolean
    fun clearKey()
}
