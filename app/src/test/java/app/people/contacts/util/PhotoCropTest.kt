package app.people.contacts.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** The photo cropper always picks a square inside the image, matching what's on screen. */
class PhotoCropTest {
    private val viewport = 1000f

    @Test fun `unmoved landscape photo crops its centre square`() =
        assertEquals(CropWindow(left = 500, top = 0, side = 1000), PhotoCrop.window(2000, 1000, viewport, 1f, 0f, 0f))

    @Test fun `unmoved portrait photo crops its centre square`() =
        assertEquals(CropWindow(left = 0, top = 250, side = 500), PhotoCrop.window(500, 1000, viewport, 1f, 0f, 0f))

    @Test fun `zooming in crops a smaller square`() =
        assertEquals(CropWindow(left = 750, top = 250, side = 500), PhotoCrop.window(2000, 1000, viewport, 2f, 0f, 0f))

    @Test fun `dragging right shows more of the left side`() {
        // At zoom 1 a 2000x1000 photo is shown at its own size; moving it 300px right moves the crop 300px left.
        assertEquals(CropWindow(left = 200, top = 0, side = 1000), PhotoCrop.window(2000, 1000, viewport, 1f, 300f, 0f))
    }

    @Test fun `dragging too far stops at the edge`() {
        assertEquals(CropWindow(left = 0, top = 0, side = 1000), PhotoCrop.window(2000, 1000, viewport, 1f, 5000f, 0f))
        assertEquals(CropWindow(left = 1000, top = 0, side = 1000), PhotoCrop.window(2000, 1000, viewport, 1f, -5000f, 0f))
    }

    @Test fun `a square photo can't move until zoomed`() {
        val (x, y) = PhotoCrop.clampOffset(800, 800, viewport, 1f, 200f, -200f)
        assertEquals(0f, x, 0.001f)
        assertEquals(0f, y, 0.001f)
        assertEquals(200f to -200f, PhotoCrop.clampOffset(800, 800, viewport, 2f, 200f, -200f))
    }

    @Test fun `zoom is kept within limits`() {
        assertEquals(PhotoCrop.scale(1000, 1000, viewport, 1f), PhotoCrop.scale(1000, 1000, viewport, 0.2f))
        assertEquals(PhotoCrop.scale(1000, 1000, viewport, PhotoCrop.MAX_ZOOM), PhotoCrop.scale(1000, 1000, viewport, 50f))
    }
}
