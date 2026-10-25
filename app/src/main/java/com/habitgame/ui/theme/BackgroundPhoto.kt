package com.habitgame.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * The photo shown behind the app while Glass is on. It's stored already shrunk and
 * blurred, so it loads instantly and looks the same on every Android version.
 */
object BackgroundPhoto {
    private const val WIDTH = 240        // small on purpose: it's blurred and stretched to fill the screen
    private const val BLUR_RADIUS = 9
    private const val BLUR_PASSES = 3    // three box blurs ≈ a smooth Gaussian blur

    private fun dir(context: Context) = File(context.filesDir, "background").apply { mkdirs() }

    /** Reads a picked photo the right way up. */
    fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = 1080f / maxOf(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    /** Shrinks and blurs [photo], saves it, and returns the saved file's path. */
    fun save(context: Context, photo: Bitmap): String? = runCatching {
        val height = (WIDTH * photo.height.toFloat() / photo.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(photo.copy(Bitmap.Config.ARGB_8888, false), WIDTH, height, true)
        val pixels = IntArray(WIDTH * height)
        small.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, height)
        repeat(BLUR_PASSES) {
            boxBlur(pixels, WIDTH, height, BLUR_RADIUS, horizontal = true)
            boxBlur(pixels, WIDTH, height, BLUR_RADIUS, horizontal = false)
        }
        val blurred = Bitmap.createBitmap(pixels, WIDTH, height, Bitmap.Config.ARGB_8888)
        // A new name each time so the app notices the change; older photos are removed
        dir(context).listFiles()?.forEach { it.delete() }
        val file = File(dir(context), "background_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { blurred.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    }.getOrNull()

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    /** The photo's overall colour: every pixel averaged. */
    fun averageColor(bitmap: Bitmap): Color = Color(Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0))

    /** One box-blur pass along rows or columns, in place. */
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val out = IntArray(length)
        for (line in 0 until lines) {
            fun at(i: Int) = pixels[if (horizontal) line * width + i.coerceIn(0, length - 1) else i.coerceIn(0, length - 1) * width + line]
            var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val c = at(i); r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            val size = radius * 2 + 1
            for (i in 0 until length) {
                out[i] = (0xFF shl 24) or ((r / size) shl 16) or ((g / size) shl 8) or (b / size)
                val add = at(i + radius + 1)
                val drop = at(i - radius)
                r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
                b += (add and 0xFF) - (drop and 0xFF)
            }
            for (i in 0 until length) {
                pixels[if (horizontal) line * width + i else i * width + line] = out[i]
            }
        }
    }
}

/** Loads the saved background photo (off the main thread) along with its main colour. */
@Composable
fun rememberBackgroundPhoto(path: String): Pair<ImageBitmap, Color>? {
    val loaded by produceState<Pair<ImageBitmap, Color>?>(null, path) {
        value = if (path.isBlank()) null else withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.let { it.asImageBitmap() to BackgroundPhoto.averageColor(it) }
        }
    }
    return loaded
}
