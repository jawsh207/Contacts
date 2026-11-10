package app.people.contacts.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Contact photos are stored as squares this size; the provider makes the thumbnail from it. */
private const val PHOTO_SIDE = 720

/**
 * Reads a picked image, crops it to the centre square, scales it to [PHOTO_SIDE] and encodes it
 * as JPEG for the contacts provider. Null if the image can't be read. Call off the main thread.
 */
fun scaledContactPhoto(context: Context, uri: Uri): ByteArray? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        // Decode no larger than needed: the short side down to about PHOTO_SIDE.
        val shortSide = min(info.size.width, info.size.height)
        if (shortSide > PHOTO_SIDE * 2) {
            val factor = shortSide / PHOTO_SIDE
            decoder.setTargetSize(info.size.width / factor, info.size.height / factor)
        }
    }
    val side = min(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val scaled = if (side > PHOTO_SIDE) Bitmap.createScaledBitmap(square, PHOTO_SIDE, PHOTO_SIDE, true) else square
    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
}.getOrNull()
