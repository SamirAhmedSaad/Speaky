package com.speakmind.app.feature.ai.platform

import java.io.File

actual fun findModelFile(): String? {
    val searchPaths = listOf(
        "/sdcard/Download",
        "/storage/emulated/0/Download",
        "/sdcard",
        "/storage/emulated/0",
    )

    val modelNames = listOf(
        "model.gguf",
        "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
        "gemma-3-1b-it-Q4_K_M.gguf",
        "llama.gguf",
        "llama-3.2-3b-q4_k_m.gguf",
        "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
    )

    // Check exact names first
    for (dir in searchPaths) {
        for (name in modelNames) {
            val file = File(dir, name)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
        }
    }

    // Fallback: find any .gguf file in Download
    for (dir in searchPaths) {
        val folder = File(dir)
        if (folder.isDirectory) {
            val ggufFile = folder.listFiles()?.firstOrNull { it.name.endsWith(".gguf") }
            if (ggufFile != null && ggufFile.length() > 0) {
                return ggufFile.absolutePath
            }
        }
    }

    return null
}
