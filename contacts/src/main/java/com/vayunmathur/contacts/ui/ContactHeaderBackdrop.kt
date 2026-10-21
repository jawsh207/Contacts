package com.vayunmathur.contacts.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.TopAppBarDefaults
import com.vayunmathur.library.ui.paneContentMargin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How far the backdrop reaches past the window top, so it is never short of the edge. */
private val BACKDROP_OVERDRAW = 48.dp

/** How far the backdrop runs below the header before it has faded into the page. */
private val BACKDROP_TAIL = 32.dp

private val BACKDROP_BLUR = 48.dp

/**
 * The contact page header with Google Contacts' backdrop behind it: the contact's photo,
 * enlarged and heavily blurred, filling the full width from the top of the screen (behind the
 * transparent top bar) down past the name, then fading into the page. A contact without a
 * photo gets a wash of their avatar colour instead, so every header reads the same way.
 *
 * The header is a list item inset by the page margins and pushed below the top bar, so the
 * backdrop is laid out larger than the item and shifted up and out by those amounts. Being
 * part of the item, it scrolls away with the header.
 */
@Composable
internal fun ProfileHeaderWithBackdrop(contact: Contact, decodePhoto: ((String) -> Bitmap?)?) {
    val density = LocalDensity.current
    val sidePx = with(density) { paneContentMargin().roundToPx() }
    val topBarInset = TopAppBarDefaults.windowInsets.getTop(density)
    val topPx = topBarInset + with(density) {
        (TopAppBarDefaults.TopAppBarExpandedHeight + BACKDROP_OVERDRAW).roundToPx()
    }
    val tailPx = with(density) { BACKDROP_TAIL.roundToPx() }

    Layout(
        content = {
            HeaderBackdrop(contact, decodePhoto)
            ProfileHeader(contact, decodePhoto)
        },
    ) { measurables, constraints ->
        val header = measurables[1].measure(constraints)
        val backdrop = measurables[0].measure(
            Constraints.fixed(
                width = header.width + 2 * sidePx,
                height = header.height + topPx + tailPx,
            )
        )
        layout(header.width, header.height) {
            backdrop.place(-sidePx, -topPx)
            header.place(0, 0)
        }
    }
}

@Composable
private fun HeaderBackdrop(contact: Contact, decodePhoto: ((String) -> Bitmap?)?) {
    val photoBase64 = contact.photo?.photo
    val photo by produceState<Bitmap?>(initialValue = null, key1 = photoBase64) {
        value = if (photoBase64 != null) {
            withContext(Dispatchers.IO) { decodePhoto?.invoke(photoBase64) }
        } else null
    }
    val page = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        val bmp = photo
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(BACKDROP_BLUR, BlurredEdgeTreatment.Rectangle),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(avatarColorsFor(contact.id).container)
            )
        }
        // Mutes the colours so the top bar icons and the name stay legible, then fades the
        // backdrop into the page so it has no hard bottom edge.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to page.copy(alpha = 0.3f),
                        0.55f to page.copy(alpha = 0.45f),
                        1f to page,
                    )
                )
        )
    }
}
