package com.speakmind.app.config

expect object AppConfig {
    val supabaseUrl: String
    val supabaseAnonKey: String
    /** Google Cloud OAuth "Web application" client ID; empty disables Google sign-in. */
    val googleWebClientId: String
}
