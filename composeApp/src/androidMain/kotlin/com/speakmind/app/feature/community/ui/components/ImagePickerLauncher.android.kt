package com.speakmind.app.feature.community.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onResult(uri?.let { compressImageUri(context, it) })
    }
    return remember(launcher) {
        object : ImagePickerLauncher {
            override fun launch() = launcher.launch("image/*")
        }
    }
}

private fun compressImageUri(context: Context, uri: Uri): ByteArray? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(input)
        input.close()
        if (original == null) return null
        val maxDim = 256
        val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        val w = (original.width * scale).toInt().coerceAtLeast(1)
        val h = (original.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(original, w, h, true) else original
        if (scaled !== original) original.recycle()
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
        if (scaled !== original) scaled.recycle()
        out.toByteArray()
    } catch (_: Exception) {
        null
    }
}
