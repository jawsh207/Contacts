package com.vayunmathur.contacts.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.data.RecentEntry
import com.vayunmathur.contacts.data.RecentKind
import com.vayunmathur.contacts.util.ContactsActions
import com.vayunmathur.library.ui.ExternalIntents
import com.vayunmathur.library.ui.IconCallMade
import com.vayunmathur.library.ui.IconCallMissed
import com.vayunmathur.library.ui.IconCallReceived
import com.vayunmathur.library.ui.IconChatBubble
import com.vayunmathur.library.ui.IconSend
import com.vayunmathur.library.ui.IconVisible
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.ListItemDefaults
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.Text

private val KIND_ICON_SIZE = 16.dp

/**
 * One Recents row, as in Google Contacts: the contact's photo and name, then a small icon for
 * what happened (missed, outgoing or incoming call, received or sent text, or viewed), the
 * message text or a label cut to one line, and how long ago. Tapping a call opens the dialer
 * with its number, a text opens the conversation in the messaging app, and a view the contact.
 */
@Composable
internal fun RecentRow(entry: RecentEntry, actions: ContactsActions) {
    val contact = entry.contact
    val context = LocalContext.current
    val isMissed = entry.kind == RecentKind.CALL_MISSED
    val detailColor = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val ago = DateUtils.getRelativeTimeSpanString(
        entry.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    ListItem(
        modifier = Modifier.clickable { openRecent(context, entry, actions) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecentKindIcon(entry.kind, detailColor)
                Spacer(Modifier.width(6.dp))
                // Shrinks and ellipsizes first, so the time after it always stays visible.
                Text(
                    text = entry.snippet ?: stringResource(entry.kind.labelRes()),
                    color = detailColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(text = " · $ago", color = detailColor, maxLines = 1)
            }
        },
        leadingContent = { ContactAvatar(contact, actions::decodePhoto, Modifier.size(48.dp)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(contact.name.value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentKindIcon(kind: RecentKind, tint: Color) {
    val modifier = Modifier.size(KIND_ICON_SIZE)
    when (kind) {
        RecentKind.CALL_MISSED -> IconCallMissed(modifier, tint)
        RecentKind.CALL_OUTGOING -> IconCallMade(modifier, tint)
        RecentKind.CALL_INCOMING -> IconCallReceived(modifier, tint)
        RecentKind.MESSAGE_RECEIVED -> IconChatBubble(modifier, tint)
        RecentKind.MESSAGE_SENT -> IconSend(modifier, tint)
        RecentKind.VIEWED -> IconVisible(modifier, tint)
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

private fun openRecent(context: android.content.Context, entry: RecentEntry, actions: ContactsActions) {
    val number = entry.address
    val opened = number != null && when (entry.kind) {
        RecentKind.CALL_INCOMING, RecentKind.CALL_OUTGOING, RecentKind.CALL_MISSED ->
            ExternalIntents.dial(context, number)
        RecentKind.MESSAGE_RECEIVED, RecentKind.MESSAGE_SENT -> ExternalIntents.sendSms(context, number)
        RecentKind.VIEWED -> false
    }
    if (!opened) actions.openContact(entry.contact)
}
