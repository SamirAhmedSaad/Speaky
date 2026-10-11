package com.speakmind.app.config

expect object AppConfig {
    val supabaseUrl: String
    val supabaseAnonKey: String
}
