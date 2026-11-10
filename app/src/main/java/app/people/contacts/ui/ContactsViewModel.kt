package app.people.contacts.ui

import android.app.Application
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.people.contacts.data.BIRTHDAY_WINDOW_DAYS
import app.people.contacts.data.ContactDetails
import app.people.contacts.data.ContactSummary
import app.people.contacts.data.ContactWriter
import app.people.contacts.data.ContactsRepository
import app.people.contacts.data.RecentEntry
import app.people.contacts.data.Settings
import app.people.contacts.data.UpcomingBirthday
import app.people.contacts.data.hasCallAndTextAccess
import app.people.contacts.data.recentCallsAndTexts
import app.people.contacts.data.recentViews
import app.people.contacts.data.upcomingBirthdays
import app.people.contacts.util.contentChanges
import java.text.Collator
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A contact page's state: still loading, or loaded ([details] null if the contact is gone). */
data class DetailState(val loaded: Boolean, val details: ContactDetails?)

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ContactsRepository(app)
    val writer = ContactWriter(app)

    /** Bumped when contacts access is granted, so everything reloads. */
    private val accessChanges = MutableStateFlow(0)

    fun onAccessChanged() {
        accessChanges.value++
    }

    private val changes: Flow<Unit> =
        accessChanges.flatMapLatest { contentChanges(app, ContactsContract.Contacts.CONTENT_URI) }

    /** All contacts, sorted by name; null until the first load. */
    val contacts: StateFlow<List<ContactSummary>?> = changes
        .mapLatest {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            repository.loadAll().sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One contact's details, reloaded whenever contacts change. */
    fun details(id: Long): Flow<DetailState> =
        changes.mapLatest { repository.loadDetails(id) }.map { DetailState(true, it) }

    val settings = Settings(app)

    /** Birthdays today and in the next [BIRTHDAY_WINDOW_DAYS] days, soonest first. */
    val birthdays: StateFlow<List<UpcomingBirthday>> = combine(
        contacts.filterNotNull(),
        changes.mapLatest { repository.loadBirthdays() },
    ) { list, dates -> upcomingBirthdays(list, dates, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Highlights' Recents: recently viewed contacts, or, when turned on in Settings and allowed,
     * the latest call or text with each saved contact.
     */
    val recents: StateFlow<List<RecentEntry>> = settings.recentsFromCallsAndTexts
        .flatMapLatest { fromCalls ->
            if (fromCalls && hasCallAndTextAccess(app)) {
                combine(
                    contacts.filterNotNull(),
                    merge(contentChanges(app, CallLog.Calls.CONTENT_URI), contentChanges(app, Telephony.Sms.CONTENT_URI)),
                ) { list, _ -> list }.mapLatest { recentCallsAndTexts(app, it) }
            } else {
                combine(contacts.filterNotNull(), settings.recentViews) { list, views -> recentViews(views, list) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Notes that a contact's page was opened, for Recents. */
    fun recordView(id: Long) {
        viewModelScope.launch { settings.recordView(id, System.currentTimeMillis()) }
    }

    fun setStarred(id: Long, starred: Boolean) {
        viewModelScope.launch { repository.setStarred(id, starred) }
    }
}
