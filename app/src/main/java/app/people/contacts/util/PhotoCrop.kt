package app.people.contacts.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The square of an image (in image pixels) that becomes the contact photo. */
data class CropWindow(val left: Int, val top: Int, val side: Int)

/**
 * The photo cropper's maths, kept apart from the screen so it can be tested. The image is shown
 * in a square viewport [viewport] pixels wide, scaled to just cover it at [zoom] 1 and
 * enlarged by [zoom] beyond that, then moved by an offset from centre. The viewport must stay
 * covered, so offsets are limited.
 */
object PhotoCrop {
    /** How far the photo can be enlarged past filling the square. */
    const val MAX_ZOOM = 5f

    /** Image pixels to screen pixels: enough to cover the viewport, times [zoom]. */
    fun scale(width: Int, height: Int, viewport: Float, zoom: Float): Float =
        viewport / min(width, height) * zoom.coerceIn(1f, MAX_ZOOM)

    /** The offset from centre moved back within bounds, so no gap shows at an edge. */
    fun clampOffset(width: Int, height: Int, viewport: Float, zoom: Float, x: Float, y: Float): Pair<Float, Float> {
        val s = scale(width, height, viewport, zoom)
        val maxX = max(0f, (width * s - viewport) / 2)
        val maxY = max(0f, (height * s - viewport) / 2)
        return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
    }

    /** The image's top-left corner on screen, relative to the viewport's. */
    fun imageOrigin(width: Int, height: Int, viewport: Float, zoom: Float, x: Float, y: Float): Pair<Float, Float> {
        val s = scale(width, height, viewport, zoom)
        val (cx, cy) = clampOffset(width, height, viewport, zoom, x, y)
        return (viewport - width * s) / 2 + cx to (viewport - height * s) / 2 + cy
    }

    /** The part of the image inside the viewport, kept within the image. */
    fun window(width: Int, height: Int, viewport: Float, zoom: Float, x: Float, y: Float): CropWindow {
        val s = scale(width, height, viewport, zoom)
        val (ox, oy) = imageOrigin(width, height, viewport, zoom, x, y)
        val side = (viewport / s).roundToInt().coerceIn(1, min(width, height))
        val left = (-ox / s).roundToInt().coerceIn(0, width - side)
        val top = (-oy / s).roundToInt().coerceIn(0, height - side)
        return CropWindow(left, top, side)
    }
}
