package com.vayunmathur.contacts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.vayunmathur.contacts.data.RecentEntry
import com.vayunmathur.contacts.data.RecentActivitySource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.vayunmathur.library.ui.LoadingState
import com.vayunmathur.library.ui.TopAppBar
import com.vayunmathur.library.ui.appBarScrollBehavior
import com.vayunmathur.library.util.NavBackStack
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Binds [ContactViewModel] to the stateless [HighlightsScreen]. */
@OptIn(ExperimentalTime::class)
@Composable
fun HighlightsPage(viewModel: ContactViewModel, backStack: NavBackStack<Route>, onSearch: () -> Unit = {}) {
    val contacts by viewModel.visibleContacts.collectAsStateWithLifecycle()
    val hasLoadedContacts by viewModel.hasLoadedContacts.collectAsStateWithLifecycle()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val birthdays = remember(contacts, today) { upcomingBirthdays(contacts, today) }
    val favorites = remember(contacts) {
        contacts.filter { it.isFavorite }.sortedBy { it.name.value.lowercase() }
    }
    val recentViews by viewModel.recentViews.collectAsStateWithLifecycle()
    val includeCallsAndMessages by viewModel.includeCallsAndMessagesInRecents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Recently viewed contacts, plus recent calls and texts when the user has opted in.
    val recents by produceState(emptyList<RecentEntry>(), contacts, recentViews, includeCallsAndMessages) {
        value = withContext(Dispatchers.IO) {
            RecentActivitySource.load(
                context = context,
                views = recentViews,
                contactsById = contacts.associateBy { it.id },
                includeCallsAndMessages = includeCallsAndMessages,
            )
        }
    }

    HighlightsScreen(
        birthdays = birthdays,
        favorites = favorites,
        recents = recents,
        isLoading = !hasLoadedContacts,
        onSearch = onSearch,
        actions = object : ContactsActions by viewModel {
            override fun openContact(contact: Contact) {
                backStack.add(Route.ContactDetail(contact.id))
            }
        },
    )
}

/**
 * Google Contacts–style Highlights: the search bar, a strip of favorites, "For you" cards for
 * upcoming birthdays, then Recents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(
    birthdays: List<UpcomingBirthday>,
    favorites: List<Contact>,
    isLoading: Boolean,
    actions: ContactsActions,
    recents: List<RecentEntry> = emptyList(),
    onSearch: () -> Unit = {},
) {
    val scrollBehavior = appBarScrollBehavior()
    LazyListScaffold(
        topBar = {
            TopAppBar(
                title = { SearchBarButton(onSearch, Modifier.padding(bottom = 8.dp)) },
                scrollBehavior = scrollBehavior,
            )
        },
        horizontalPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        scrollBehavior = scrollBehavior,
    ) {
        if (birthdays.isEmpty() && favorites.isEmpty() && recents.isEmpty()) {
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

        if (favorites.isNotEmpty()) {
            item(key = "favorites-header") { HighlightsSectionHeader(stringResource(R.string.favorites)) }
            item(key = "favorites") { FavoritesRow(favorites, actions) }
        }

        if (birthdays.isNotEmpty()) {
            item(key = "for-you-header") { HighlightsSectionHeader(stringResource(R.string.for_you)) }
            birthdays.forEach { birthday ->
                item(key = "birthday-${birthday.contact.id}") { BirthdayCard(birthday, actions) }
            }
        }

        if (recents.isNotEmpty()) {
            item(key = "recents-header") { HighlightsSectionHeader(stringResource(R.string.recents)) }
            item(key = "recents") {
                GroupedContactSection(count = recents.size) { i -> RecentRow(recents[i], actions) }
            }
        }
    }
}
