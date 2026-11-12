package com.speakmind.app.feature.community.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * The whole app runs in "resize" mode (set on MainActivity in the manifest): the window never pans,
 * screens lift their own content with imePadding(). This only re-asserts it, never switches back to
 * "pan" — pan + imePadding() pushed the chat input bar to the top with a keyboard-high gap under it.
 */
@Composable
actual fun ResizeWindowForKeyboard() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        @Suppress("DEPRECATION")
        val resize = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (window != null && (window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) != resize) {
            window.setSoftInputMode(resize)
        }
        onDispose { }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
