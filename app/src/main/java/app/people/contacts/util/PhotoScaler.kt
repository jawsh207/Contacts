package app.people.contacts.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Contact photos are stored as squares this size; the provider makes the thumbnail from it. */
private const val PHOTO_SIDE = 720

/** How big a picked image is loaded for cropping: enough to zoom in a little and stay sharp. */
private const val LOAD_LONG_SIDE = 2048

/**
 * Reads a picked image for the cropper, shrunk so its long side is about [LOAD_LONG_SIDE] (a
 * full-size camera photo would use hundreds of megabytes). Null if it can't be read. Call off
 * the main thread.
 */
fun loadPhotoForCrop(context: Context, uri: Uri): Bitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // Software memory, so the bitmap can be cut up and re-encoded afterwards.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longSide = max(info.size.width, info.size.height)
        if (longSide > LOAD_LONG_SIDE) {
            val factor = longSide.toFloat() / LOAD_LONG_SIDE
            decoder.setTargetSize((info.size.width / factor).toInt(), (info.size.height / factor).toInt())
        }
    }
}.getOrNull()

/**
 * Cuts [window] out of [image], scales it to [PHOTO_SIDE] square and encodes it as JPEG for the
 * contacts provider. Null if that fails. Call off the main thread.
 */
fun croppedContactPhoto(image: Bitmap, window: CropWindow): ByteArray? = runCatching {
    val square = Bitmap.createBitmap(image, window.left, window.top, window.side, window.side)
    val scaled = if (window.side > PHOTO_SIDE) Bitmap.createScaledBitmap(square, PHOTO_SIDE, PHOTO_SIDE, true) else square
    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
}.getOrNull()
