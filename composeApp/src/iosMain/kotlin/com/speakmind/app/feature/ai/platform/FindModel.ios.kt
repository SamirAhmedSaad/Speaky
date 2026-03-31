package com.speakmind.app.feature.ai.platform

import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

actual fun findModelFile(): String? {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true
    )
    val documentsDir = paths.firstOrNull() as? String ?: return null

    val fileManager = NSFileManager.defaultManager
    val contents = fileManager.contentsOfDirectoryAtPath(documentsDir, null) as? List<*>

    contents?.forEach { item ->
        val name = item as? String ?: return@forEach
        if (name.endsWith(".gguf")) {
            return "$documentsDir/$name"
        }
    }

    return null
}
