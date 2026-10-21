package com.vayunmathur.contacts.ui

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.data.RecentEntry
import com.vayunmathur.contacts.data.RecentKind
import com.vayunmathur.contacts.util.ContactsActions
import com.vayunmathur.library.ui.ExternalIntents
import com.vayunmathur.library.ui.IconPerson
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.ListItemDefaults
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.Text

/**
 * One Recents row, as in Google Contacts: photo, name (or the number, for someone not in the
 * address book), and what happened when, e.g. "Missed call · 2 hr. ago". Tapping a contact
 * opens it; tapping an unknown number opens it in the dialer.
 */
@Composable
internal fun RecentRow(entry: RecentEntry, actions: ContactsActions) {
    val context = LocalContext.current
    val contact = entry.contact
    val title = contact?.name?.value?.ifBlank { null }
        ?: entry.number?.let { formatPhoneNumber(it) }
        ?: ""
    val ago = DateUtils.getRelativeTimeSpanString(
        entry.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    ListItem(
        modifier = Modifier.clickable {
            when {
                contact != null -> actions.openContact(contact)
                entry.number != null ->
                    ExternalIntents.launch(context, Intent(Intent.ACTION_DIAL, "tel:${entry.number}".toUri()))
            }
        },
        supportingContent = {
            Text(
                text = "${stringResource(entry.kind.labelRes())} · $ago",
                color = if (entry.kind == RecentKind.CALL_MISSED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            if (contact != null) {
                ContactAvatar(contact, actions::decodePhoto, Modifier.size(40.dp))
            } else {
                UnknownNumberAvatar()
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun UnknownNumberAvatar() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconPerson(tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun RecentKind.labelRes(): Int = when (this) {
    RecentKind.VIEWED -> R.string.recent_viewed
    RecentKind.CALL_INCOMING -> R.string.recent_call_incoming
    RecentKind.CALL_OUTGOING -> R.string.recent_call_outgoing
    RecentKind.CALL_MISSED -> R.string.recent_call_missed
    RecentKind.MESSAGE_RECEIVED -> R.string.recent_message_received
    RecentKind.MESSAGE_SENT -> R.string.recent_message_sent
}
