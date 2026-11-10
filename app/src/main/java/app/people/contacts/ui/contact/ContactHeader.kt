package app.people.contacts.ui.contact

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.people.contacts.data.ContactDetails
import app.people.contacts.ui.components.Avatar
import app.people.contacts.ui.components.avatarColors
import app.people.contacts.ui.components.rememberPhoto

/** The round photo on the contact page. */
private val PHOTO_SIZE = 180.dp

/** Ring in the page colour around the photo, separating it from the backdrop. */
private val PHOTO_RING = 6.dp

/** Height of the floating top bar the header sits under. */
val CONTACT_TOP_BAR_HEIGHT = 64.dp

/** Open space between the top bar and the photo, as a share of the screen width. */
private const val BACKDROP_GAP = 0.46f

/** How light a veil of the page colour lies over the blurred photo. */
private const val BACKDROP_VEIL = 0.2f

/**
 * The top of the contact page, in Google Contacts' style: the contact's photo, enlarged and
 * blurred, fills the width from the top of the screen down to the middle of the round photo,
 * which sits on that edge inside a ring of the page colour. Without a photo the backdrop is a
 * gradient of the contact's colour, fading down into the page. The name goes underneath.
 */
@Composable
fun ContactHeader(contact: ContactDetails) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val photoTop = topInset + CONTACT_TOP_BAR_HEIGHT + maxWidth * BACKDROP_GAP
        Backdrop(contact, Modifier.fillMaxWidth().height(photoTop + PHOTO_RING + PHOTO_SIZE / 2))
        Column(
            Modifier.fillMaxWidth().padding(top = photoTop),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(PHOTO_SIZE + PHOTO_RING * 2)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(contact.name, contact.photoUri ?: contact.thumbnailUri, contact.id, PHOTO_SIZE)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                contact.name,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            val work = listOfNotNull(contact.jobTitle, contact.company).joinToString(", ")
            if (work.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    work,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Backdrop(contact: ContactDetails, modifier: Modifier) {
    val photo = rememberPhoto(contact.photoUri ?: contact.thumbnailUri)
    val page = MaterialTheme.colorScheme.background
    Box(modifier) {
        if (photo != null) {
            Image(
                photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(24.dp, BlurredEdgeTreatment.Rectangle),
            )
            Box(Modifier.fillMaxSize().background(page.copy(alpha = BACKDROP_VEIL)))
        } else {
            val (container, _) = avatarColors(contact.id)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(container, page))))
        }
    }
}
