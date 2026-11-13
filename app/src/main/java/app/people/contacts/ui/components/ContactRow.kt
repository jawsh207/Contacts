package app.people.contacts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.ContactSummary

/** Size of contact photos in lists. */
val LIST_AVATAR_SIZE = 48.dp

/**
 * A contact in a list: photo and name on a card, 72dp tall (48dp photo, 12dp above and below).
 * [shape] places it in its group; see [segmentShape]. With [onLongClick] it can be selected:
 * [selected] tints the card and shows a tick in place of the photo.
 */
@Composable
fun ContactRow(
    contact: ContactSummary,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (selected) scheme.secondaryContainer else scheme.surfaceBright
    if (onLongClick == null) {
        Surface(onClick = onClick, shape = shape, color = color, modifier = modifier.fillMaxWidth()) {
            ContactRowContent(contact, supporting, selected)
        }
    } else {
        Surface(
            shape = shape, color = color,
            modifier = modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            ContactRowContent(contact, supporting, selected)
        }
    }
}

@Composable
private fun ContactRowContent(contact: ContactSummary, supporting: String?, selected: Boolean) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selected) {
            Box(
                Modifier.size(LIST_AVATAR_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary) }
        } else {
            Avatar(contact.name, contact.photoUri, contact.id, LIST_AVATAR_SIZE)
        }
        Spacer(Modifier.width(16.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The "Search contacts" pill at the top of a tab; tapping it opens search. */
@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(R.string.search_contacts),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
