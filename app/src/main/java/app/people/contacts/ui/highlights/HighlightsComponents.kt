package app.people.contacts.ui.highlights

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.people.contacts.R
import app.people.contacts.data.ContactSummary
import app.people.contacts.data.RecentEntry
import app.people.contacts.data.RecentKind
import app.people.contacts.data.UpcomingBirthday
import app.people.contacts.ui.components.Avatar
import app.people.contacts.ui.components.LIST_AVATAR_SIZE
import app.people.contacts.util.Reach
import java.time.format.DateTimeFormatter

/** Favorites' avatars, in their Expressive shapes, as in Dialtone. */
private val FAVORITE_SIZE = 68.dp

/** Share of a birthday card's width the cake takes. */
private const val CAKE_FRACTION = 0.25f

/** The cake glyph's size within its square. */
private const val CAKE_GLYPH = 0.8f

/** Favorites in a row: each contact's photo in its own Expressive shape, name underneath. */
@Composable
fun FavoritesRow(favorites: List<ContactSummary>, onOpenContact: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(favorites, key = { it.id }) { contact ->
            Column(
                Modifier.width(FAVORITE_SIZE + 8.dp).clickable { onOpenContact(contact.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(contact.name, contact.photoUri, contact.id, FAVORITE_SIZE, expressive = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    contact.name,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A "For you" birthday card: photo, "Name's birthday", when, and Call and Message buttons on
 * the left; a large cake filling the right quarter.
 */
@Composable
fun BirthdayCard(birthday: UpcomingBirthday, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val contact = birthday.contact
    val number = contact.numbers.firstOrNull()
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f - CAKE_FRACTION)) {
                Row(verticalAlignment = Alignment.Top) {
                    Avatar(contact.name, contact.photoUri, contact.id, LIST_AVATAR_SIZE)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.birthday_title, contact.name), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(birthdayWhen(birthday), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (number != null) {
                    Spacer(Modifier.height(16.dp))
                    val colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Reach.dial(context, number) }, colors = colors) {
                            Icon(Icons.Outlined.Call, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_call))
                        }
                        Button(onClick = { Reach.message(context, number) }, colors = colors) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_message))
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(CAKE_FRACTION).aspectRatio(1f), contentAlignment = Alignment.Center) {
                val glyph = with(LocalDensity.current) { (maxWidth * CAKE_GLYPH).toSp() }
                Text("🎂", fontSize = glyph, lineHeight = glyph, maxLines = 1)
            }
        }
    }
}

/** "Today", "Tomorrow" or "Mar 15 · In 5 days", then "· Turns 34" when the year is known. */
@Composable
private fun birthdayWhen(birthday: UpcomingBirthday): String {
    val day = when (birthday.daysAway) {
        0 -> stringResource(R.string.today)
        1 -> stringResource(R.string.tomorrow)
        else -> birthday.date.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "MMMd"))) +
            " · " + pluralStringResource(R.plurals.in_days, birthday.daysAway, birthday.daysAway)
    }
    val age = birthday.turningAge?.let { stringResource(R.string.turns_age, it) }
    return listOfNotNull(day, age).joinToString(" · ")
}

/**
 * A Recents entry: photo, name, and what happened (a call icon or the message) and when.
 * Tapping a call opens the dialer with its number, a text the conversation, a view the contact.
 */
@Composable
fun RecentRow(entry: RecentEntry, shape: Shape, onOpenContact: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val number = entry.number
    val onClick = {
        when {
            number == null || entry.kind == RecentKind.Viewed -> onOpenContact()
            entry.kind == RecentKind.TextReceived || entry.kind == RecentKind.TextSent -> Reach.message(context, number)
            else -> Reach.dial(context, number)
        }
    }
    Surface(onClick = onClick, shape = shape, color = scheme.surfaceBright, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(entry.contact.name, entry.contact.photoUri, entry.contact.id, LIST_AVATAR_SIZE)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.contact.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val missed = entry.kind == RecentKind.CallMissed
                    Icon(
                        kindIcon(entry.kind), null, Modifier.size(16.dp),
                        tint = if (missed) scheme.error else scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    val time = DateUtils.getRelativeTimeSpanString(entry.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    Text(
                        listOfNotNull(entry.snippet?.takeIf { it.isNotBlank() } ?: kindText(entry.kind), time).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (missed) scheme.error else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun kindIcon(kind: RecentKind): ImageVector = when (kind) {
    RecentKind.Viewed -> Icons.Rounded.History
    RecentKind.CallMissed -> Icons.AutoMirrored.Rounded.CallMissed
    RecentKind.CallIncoming -> Icons.AutoMirrored.Rounded.CallReceived
    RecentKind.CallOutgoing -> Icons.AutoMirrored.Rounded.CallMade
    RecentKind.TextReceived, RecentKind.TextSent -> Icons.Rounded.ChatBubble
}

@Composable
private fun kindText(kind: RecentKind): String = stringResource(
    when (kind) {
        RecentKind.Viewed -> R.string.recent_viewed
        RecentKind.CallMissed -> R.string.recent_missed
        RecentKind.CallIncoming -> R.string.recent_incoming
        RecentKind.CallOutgoing -> R.string.recent_outgoing
        RecentKind.TextReceived -> R.string.recent_text_received
        RecentKind.TextSent -> R.string.recent_text_sent
    }
)
