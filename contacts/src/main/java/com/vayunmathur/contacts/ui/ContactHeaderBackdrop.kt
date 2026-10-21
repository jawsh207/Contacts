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
import kotlin.math.roundToInt

/** Size of the round photo on the contact page (about 44% of a phone's width, as in Google's). */
internal val PROFILE_AVATAR_SIZE = 176.dp

/** [ProfileHeader]'s own padding above the photo. */
private val PROFILE_HEADER_TOP_PADDING = 16.dp

/** How far the backdrop reaches past the window top, so it is never short of the edge. */
private val BACKDROP_OVERDRAW = 48.dp

/** Blurred, but light enough that shapes and highlights in the photo still show through. */
private val BACKDROP_BLUR = 24.dp

/** Open blurred area between the top bar and the photo, as a fraction of the page width. */
private const val HERO_GAP_FRACTION = 0.42f

/**
 * The contact page header in Google Contacts' style: the contact's photo, enlarged and blurred,
 * fills the full width from the top of the screen (behind the see-through top bar) down to a
 * hard edge through the middle of the round photo, which straddles that edge. A contact
 * without a photo gets a wash of their avatar colour in the same place.
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
    val aboveItemPx = topBarInset + with(density) {
        (TopAppBarDefaults.TopAppBarExpandedHeight + BACKDROP_OVERDRAW).roundToPx()
    }
    val photoHalfPx = with(density) {
        (PROFILE_HEADER_TOP_PADDING + PROFILE_AVATAR_SIZE / 2).roundToPx()
    }

    Layout(
        content = {
            HeaderBackdrop(contact, decodePhoto)
            ProfileHeader(contact, decodePhoto)
        },
    ) { measurables, constraints ->
        val header = measurables[1].measure(constraints)
        val pageWidth = header.width + 2 * sidePx
        val heroGapPx = (pageWidth * HERO_GAP_FRACTION).roundToInt()
        val backdrop = measurables[0].measure(
            Constraints.fixed(
                width = pageWidth,
                height = aboveItemPx + heroGapPx + photoHalfPx,
            )
        )
        layout(header.width, heroGapPx + header.height) {
            backdrop.place(-sidePx, -aboveItemPx)
            header.place(0, heroGapPx)
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
            // A light veil of the page colour, softening the photo the way Google's does.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = BACKDROP_VEIL))
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(avatarColorsFor(contact.id).container)
            )
        }
    }
}

private const val BACKDROP_VEIL = 0.2f
