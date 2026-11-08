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
        resizeUsers++
        // Also when the activity was recreated (theme or dark-mode switch): the new window starts in its
        // default "pan" mode, and pan + imePadding() leaves a keyboard-high gap under the input bar.
        if (window != null && window !== resizedWindow) {
            modeBeforeResize = window.attributes.softInputMode
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            resizedWindow = window
        }
        onDispose {
            // Going from one chat to another, the old screen leaves after the new one arrived. Restoring
            // "pan" then would pan the window as well as imePadding(): the input bar jumps to the top.
            resizeUsers = (resizeUsers - 1).coerceAtLeast(0)
            if (resizeUsers == 0) {
                resizedWindow?.let { w -> modeBeforeResize?.let { w.setSoftInputMode(it) } }
                resizedWindow = null
            }
        }
    }
}

/** Chat screens currently on screen (two while navigating between chats). */
private var resizeUsers = 0
private var modeBeforeResize: Int? = null
private var resizedWindow: android.view.Window? = null

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
