package app.people.contacts.ui.edit

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.util.CropWindow
import app.people.contacts.util.PhotoCrop
import kotlin.math.roundToInt

/**
 * Choosing which part of a picked photo becomes the contact photo: pinch to zoom and drag to
 * move it under a round frame (contact photos show as circles), then Done. The photo always
 * fills the square, so there are no empty edges. Back or Cancel keeps the old photo.
 */
@Composable
fun PhotoCropper(image: Bitmap, onDone: (CropWindow) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val picture = remember(image) { image.asImageBitmap() }
    var viewport by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    val w = image.width
    val h = image.height

    Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text(
                stringResource(R.string.crop_photo),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            )
            Text(
                stringResource(R.string.crop_photo_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .aspectRatio(1f)
                        .onSizeChanged { viewport = it.width }
                        .pointerInput(image) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val v = viewport.toFloat()
                                if (v == 0f) return@detectTransformGestures
                                zoom = (zoom * gestureZoom).coerceIn(1f, PhotoCrop.MAX_ZOOM)
                                val (cx, cy) = PhotoCrop.clampOffset(w, h, v, zoom, x + pan.x, y + pan.y)
                                x = cx
                                y = cy
                            }
                        },
                ) {
                    val v = size.width
                    val s = PhotoCrop.scale(w, h, v, zoom)
                    val (ox, oy) = PhotoCrop.imageOrigin(w, h, v, zoom, x, y)
                    clipRect {
                        drawImage(
                            picture,
                            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                            dstSize = IntSize((w * s).roundToInt(), (h * s).roundToInt()),
                        )
                    }
                    // Darken outside the circle the photo will show as.
                    val outside = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addOval(Rect(Offset.Zero, size))
                    }
                    drawPath(outside, Color.Black.copy(alpha = 0.55f))
                    drawCircle(Color.White.copy(alpha = 0.8f), radius = v / 2, style = Stroke(width = 2.dp.toPx()))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = Color.White) }
                Button(onClick = {
                    if (viewport > 0) onDone(PhotoCrop.window(w, h, viewport.toFloat(), zoom, x, y))
                }) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

