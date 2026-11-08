package com.speakmind.app.feature.community.moderation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.tensorflow.lite.Interpreter
import speaky.composeapp.generated.resources.Res
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual object PhotoSafety {
    private const val SIZE = 224
    private var interpreter: Interpreter? = null
    private val lock = Mutex()

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun interpreter(): Interpreter = interpreter ?: run {
        val model = Res.readBytes("files/photo_safety.tflite")
        val buffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder()).apply { put(model); rewind() }
        Interpreter(buffer, Interpreter.Options().setNumThreads(2)).also { interpreter = it }
    }

    actual suspend fun check(imageBytes: ByteArray): PhotoScores? = withContext(Dispatchers.Default) {
        try {
            lock.withLock {
                val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return@withLock null
                // Transparent parts (PNG) on white, then 224x224.
                val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
                Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
                val scaled = Bitmap.createScaledBitmap(flat, SIZE, SIZE, true)
                val pixels = IntArray(SIZE * SIZE).also { scaled.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
                // RGB in 0..1 (the model scales to -1..1 itself).
                val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
                for (p in pixels) {
                    input.putFloat(((p shr 16) and 0xFF) / 255f)
                    input.putFloat(((p shr 8) and 0xFF) / 255f)
                    input.putFloat((p and 0xFF) / 255f)
                }
                input.rewind()
                val out = Array(1) { FloatArray(5) }
                interpreter().run(input, out)
                val s = out[0]
                PhotoScores(drawings = s[0], hentai = s[1], neutral = s[2], porn = s[3], sexy = s[4])
            }
        } catch (e: Throwable) {
            Napier.w("PhotoSafety failed", e)
            null
        }
    }
}
