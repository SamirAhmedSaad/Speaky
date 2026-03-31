package com.speakmind.app.platform

import android.content.pm.ApplicationInfo
import org.koin.core.context.GlobalContext

actual val isDebugMode: Boolean by lazy {
    try {
        val context = GlobalContext.get().get<android.content.Context>()
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    } catch (e: Exception) {
        false
    }
}
