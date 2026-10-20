package com.vayunmathur.contacts.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.Route
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.contacts.util.ContactViewModel
import com.vayunmathur.contacts.util.ContactsActions
import com.vayunmathur.library.ui.EmptyState
import com.vayunmathur.library.ui.ExperimentalMaterial3Api
import com.vayunmathur.library.ui.IconStar
import com.vayunmathur.library.ui.LazyListScaffold
import com.vayunmathur.library.ui.ListItem
import com.vayunmathur.library.ui.ListItemDefaults
import com.vayunmathur.library.ui.LoadingState
import com.vayunmathur.library.ui.MaterialTheme
import com.vayunmathur.library.ui.Text
import com.vayunmathur.library.ui.appBarScrollBehavior
import com.vayunmathur.library.util.NavBackStack
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Binds [ContactViewModel] to the stateless [HighlightsScreen]. */
@OptIn(ExperimentalTime::class)
@Composable
fun HighlightsPage(viewModel: ContactViewModel, backStack: NavBackStack<Route>) {
    val contacts by viewModel.visibleContacts.collectAsStateWithLifecycle()
    val hasLoadedContacts by viewModel.hasLoadedContacts.collectAsStateWithLifecycle()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val birthdays = remember(contacts, today) { upcomingBirthdays(contacts, today) }
    val favorites = remember(contacts) {
        contacts.filter { it.isFavorite }.sortedBy { it.name.value.lowercase() }
    }

    HighlightsScreen(
        birthdays = birthdays,
        favorites = favorites,
        isLoading = !hasLoadedContacts,
        actions = object : ContactsActions by viewModel {
            override fun openContact(contact: Contact) {
                backStack.add(Route.ContactDetail(contact.id))
            }
        },
    )
}

/** Google Contacts–style Highlights: upcoming birthdays, then starred contacts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(
    birthdays: List<UpcomingBirthday>,
    favorites: List<Contact>,
    isLoading: Boolean,
    actions: ContactsActions,
) {
    LazyListScaffold(
        title = stringResource(R.string.highlights),
        horizontalPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        scrollBehavior = appBarScrollBehavior(),
    ) {
        if (birthdays.isEmpty() && favorites.isEmpty()) {
            item(key = "highlights-empty") {
                if (isLoading) {
                    LoadingState(modifier = Modifier.fillParentMaxSize())
                } else {
                    EmptyState(
                        title = stringResource(R.string.no_highlights_yet),
                        modifier = Modifier.fillParentMaxSize(),
                        message = stringResource(R.string.no_highlights_yet_message),
                        icon = { IconStar() },
                    )
                }
            }
            return@LazyListScaffold
        }

        if (birthdays.isNotEmpty()) {
            item(key = "birthdays-header") { HighlightsSectionHeader(stringResource(R.string.upcoming_birthdays)) }
            item(key = "birthdays") {
                GroupedContactSection(count = birthdays.size) { i ->
                    BirthdayRow(birthdays[i], actions)
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "favorites-header") { HighlightsSectionHeader(stringResource(R.string.favorites)) }
            item(key = "favorites") {
                GroupedContactSection(count = favorites.size) { i ->
                    val contact = favorites[i]
                    ContactItem(
                        contact = contact,
                        isSelected = false,
                        showAccountLabels = false,
                        decodePhoto = actions::decodePhoto,
                        embeddedInCard = true,
                        onClick = { actions.openContact(contact) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightsSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BirthdayRow(birthday: UpcomingBirthday, actions: ContactsActions) {
    val whenText = when (birthday.daysAway) {
        0 -> stringResource(R.string.birthday_today)
        1 -> stringResource(R.string.birthday_tomorrow)
        else -> "${shortDate(birthday.date)} · " +
            pluralStringResource(R.plurals.birthday_in_days, birthday.daysAway, birthday.daysAway)
    }
    val ageText = birthday.turningAge?.let { stringResource(R.string.birthday_turns_age, it) }
    ListItem(
        modifier = Modifier.clickable { actions.openContact(birthday.contact) },
        supportingContent = {
            Text(
                text = listOfNotNull(whenText, ageText).joinToString(" · "),
                color = if (birthday.daysAway == 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            ContactAvatar(
                contact = birthday.contact,
                decodePhoto = actions::decodePhoto,
                modifier = Modifier.size(40.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(birthday.contact.name.value, style = MaterialTheme.typography.bodyLarge)
    }
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
