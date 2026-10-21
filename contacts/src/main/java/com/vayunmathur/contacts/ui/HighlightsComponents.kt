package com.vayunmathur.contacts.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.contacts.util.ContactsActions
import com.vayunmathur.library.ui.Button
import com.vayunmathur.library.ui.ButtonDefaults
import com.vayunmathur.library.ui.ExternalIntents
import com.vayunmathur.library.ui.IconCallOutlined
import com.vayunmathur.library.ui.IconChatBubbleOutlined
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.R as UiR
import com.vayunmathur.library.ui.Surface
import com.vayunmathur.library.ui.Text
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.ExperimentalTime

private val FAVORITE_AVATAR_SIZE = 82.dp

/** A section heading on the Highlights tab ("Favorites", "For you"). */
@Composable
internal fun HighlightsSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 16.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Google Contacts' favorites strip: large round photos in a row with names underneath. */
@Composable
internal fun FavoritesRow(favorites: List<Contact>, actions: ContactsActions) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(favorites, key = { it.id }) { contact ->
            Column(
                modifier = Modifier
                    .width(FAVORITE_AVATAR_SIZE + 8.dp)
                    .clickable { actions.openContact(contact) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContactAvatar(
                    contact = contact,
                    decodePhoto = actions::decodePhoto,
                    modifier = Modifier.size(FAVORITE_AVATAR_SIZE),
                    initialsStyle = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = contact.name.value,
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
 * A "For you" birthday card, as in Google Contacts: the contact's photo, "🎂 Name's birthday",
 * when it is, and Call / Message buttons when they have a number.
 */
@Composable
internal fun BirthdayCard(birthday: UpcomingBirthday, actions: ContactsActions) {
    val context = LocalContext.current
    val contact = birthday.contact
    val number = contact.details.phoneNumbers.firstOrNull()?.number
    Surface(
        onClick = { actions.openContact(contact) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ContactAvatar(
                    contact = contact,
                    decodePhoto = actions::decodePhoto,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "🎂 " + stringResource(R.string.birthday_card_title, contact.name.value),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(birthdayWhenText(birthday), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (number != null) {
                Spacer(Modifier.height(16.dp))
                val colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { placeCall(context, number) }, colors = colors) {
                        IconCallOutlined(Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(UiR.string.call))
                    }
                    Button(onClick = { ExternalIntents.sendSms(context, number) }, colors = colors) {
                        IconChatBubbleOutlined(Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_message))
                    }
                }
            }
        }
    }
}

/** "Today", "Tomorrow" or "Mar 15 · In 5 days", plus "· Turns 34" when the year is known. */
@Composable
private fun birthdayWhenText(birthday: UpcomingBirthday): String {
    val whenText = when (birthday.daysAway) {
        0 -> stringResource(R.string.birthday_today)
        1 -> stringResource(R.string.birthday_tomorrow)
        else -> "${shortDate(birthday.date)} · " +
            pluralStringResource(R.plurals.birthday_in_days, birthday.daysAway, birthday.daysAway)
    }
    val ageText = birthday.turningAge?.let { stringResource(R.string.birthday_turns_age, it) }
    return listOfNotNull(whenText, ageText).joinToString(" · ")
}

/** "Mar 15" in the user's locale, without the year. */
@OptIn(ExperimentalTime::class)
@Composable
private fun shortDate(date: LocalDate): String {
    val context = LocalContext.current
    val millis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    return DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
    )
}
