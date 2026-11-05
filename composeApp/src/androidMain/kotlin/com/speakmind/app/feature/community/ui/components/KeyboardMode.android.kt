package com.speakmind.app.feature.community.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun ResizeWindowForKeyboard() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        if (resizeUsers++ == 0) {
            modeBeforeResize = window?.attributes?.softInputMode
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        onDispose {
            // Going from one chat to another, the old screen leaves after the new one arrived. Restoring
            // "pan" then would pan the window as well as imePadding(): the input bar jumps to the top.
            if (--resizeUsers == 0) modeBeforeResize?.let { window?.setSoftInputMode(it) }
        }
    }
}

/** Chat screens currently on screen (two while navigating between chats). */
private var resizeUsers = 0
private var modeBeforeResize: Int? = null

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
