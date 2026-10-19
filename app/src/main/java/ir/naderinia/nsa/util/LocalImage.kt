package ir.naderinia.nsa.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * A tiny local-image loader (content:// or file:// URIs only) — enough for the attachment
 * thumbnail and the receipt preview. It replaces Coil, which pulled OkHttp, Okio and AppCompat
 * resources into the APK just to show two local pictures.
 *
 * Decodes a down-sampled copy (so a 12-megapixel photo never lands in memory at full size) and
 * honours the camera's EXIF rotation. Returns null for anything that is not a decodable image
 * (e.g. a PDF), in which case the caller shows a placeholder.
 */
object LocalImage {

    fun load(context: Context, uri: Uri, maxSidePx: Int): ImageBitmap? = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded: Bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null

        val degrees = resolver.openInputStream(uri)?.use { rotationOf(it) } ?: 0
        val result = if (degrees != 0) {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(degrees.toFloat()) },
                true
            )
        } else decoded
        result.asImageBitmap()
    }.getOrNull()

    private fun rotationOf(stream: java.io.InputStream): Int =
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}

/** The decoded picture for [uri], or null while loading / when it cannot be decoded. */
@Composable
fun rememberLocalBitmap(uri: Uri, maxSidePx: Int = 1024): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri, maxSidePx) {
        value = withContext(Dispatchers.IO) { LocalImage.load(context, uri, maxSidePx) }
    }
}
