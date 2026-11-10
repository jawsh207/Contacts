package app.people.contacts.ui

import android.app.Application
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.people.contacts.data.AccountKeys
import app.people.contacts.data.BIRTHDAY_WINDOW_DAYS
import app.people.contacts.data.ContactAccount
import app.people.contacts.data.ContactDetails
import app.people.contacts.data.ContactSummary
import app.people.contacts.data.ContactWriter
import app.people.contacts.data.ContactsRepository
import app.people.contacts.data.DuplicateSet
import app.people.contacts.data.Label
import app.people.contacts.data.LabelStore
import app.people.contacts.data.RecentEntry
import app.people.contacts.data.Settings
import app.people.contacts.data.SortOrder
import app.people.contacts.data.UpcomingBirthday
import app.people.contacts.data.allAccounts
import app.people.contacts.data.deleteOnDeviceAccountContacts
import app.people.contacts.data.findDuplicates
import app.people.contacts.data.hasCallAndTextAccess
import app.people.contacts.data.mergeDuplicates
import app.people.contacts.data.recentCallsAndTexts
import app.people.contacts.data.recentViews
import app.people.contacts.data.sortName
import app.people.contacts.data.upcomingBirthdays
import app.people.contacts.util.contentChanges
import java.text.Collator
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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

    val settings = Settings(app)

    /** Every contact, unsorted; null until the first load. */
    private val allContacts: StateFlow<List<ContactSummary>?> = changes
        .mapLatest { repository.loadAll() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sortOrder: StateFlow<SortOrder> = settings.sortOrder.stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder.FirstName)

    /** Contacts in accounts that aren't hidden, in the chosen order; null until the first load. */
    val contacts: StateFlow<List<ContactSummary>?> = combine(allContacts, sortOrder, settings.hiddenAccounts) { all, order, hidden ->
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        all?.filter { c -> c.accounts.isEmpty() || c.accounts.any { (type, name) -> !AccountKeys.isHidden(type, name, hidden) } }
            ?.sortedWith { a, b -> collator.compare(a.sortName(order), b.sortName(order)) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One contact's details, reloaded whenever contacts change. */
    fun details(id: Long): Flow<DetailState> =
        changes.mapLatest { repository.loadDetails(id) }.map { DetailState(true, it) }

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

    val labelStore = LabelStore(app)

    /** Labels, reloaded when contacts or groups change. */
    val labels: StateFlow<List<Label>?> = accessChanges
        .flatMapLatest { merge(contentChanges(app, ContactsContract.Groups.CONTENT_URI), changes) }
        .mapLatest { labelStore.load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sets of contacts that look like the same person, for Organize's Merge duplicates. */
    val duplicates: StateFlow<List<DuplicateSet>> = combine(contacts.filterNotNull(), settings.dismissedDuplicates) { list, dismissed ->
        findDuplicates(list, dismissed)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every account contacts can be in, for Settings and new labels. */
    val accounts: StateFlow<List<ContactAccount>> = combine(changes, settings.savedAccounts) { _, saved -> saved }
        .mapLatest { allAccounts(app, AccountKeys.savedNames(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun mergeDuplicateSets(sets: List<DuplicateSet>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            onDone(sets.count { mergeDuplicates(getApplication<Application>(), it) })
        }
    }

    fun dismissDuplicate(set: DuplicateSet) {
        viewModelScope.launch { settings.dismissDuplicate(set.key) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(order) }
    }

    fun setRecentsFromCallsAndTexts(on: Boolean) {
        viewModelScope.launch { settings.setRecentsFromCallsAndTexts(on) }
    }

    fun setAccountVisible(account: ContactAccount, visible: Boolean) {
        viewModelScope.launch {
            settings.editHiddenAccounts { hidden ->
                val cleaned = hidden - AccountKeys.hiddenKey(account.type, account.name) -
                    "${account.type.orEmpty()}|${account.name.orEmpty()}" - account.name.orEmpty()
                if (visible) cleaned else cleaned + AccountKeys.hiddenKey(account.type, account.name)
            }
        }
    }

    fun createOnDeviceAccount(name: String) {
        viewModelScope.launch { settings.editSavedAccounts { it + AccountKeys.savedEntry(name.trim()) } }
    }

    fun renameOnDeviceAccount(account: ContactAccount, newName: String) {
        val name = newName.trim()
        viewModelScope.launch {
            if (app.people.contacts.data.renameOnDeviceAccount(getApplication<Application>(), account, name)) {
                settings.editSavedAccounts { it - AccountKeys.savedEntry(account.name.orEmpty()) + AccountKeys.savedEntry(name) }
                val wasHidden = AccountKeys.isHidden(account.type, account.name, settings.hiddenAccounts.first())
                if (wasHidden) {
                    setAccountVisible(account, true)
                    setAccountVisible(account.copy(name = name), false)
                }
            }
        }
    }

    /** Deletes an on-device account and every contact stored in it. */
    fun deleteOnDeviceAccount(account: ContactAccount) {
        viewModelScope.launch {
            if (deleteOnDeviceAccountContacts(getApplication<Application>(), account)) {
                settings.editSavedAccounts { it - AccountKeys.savedEntry(account.name.orEmpty()) }
                setAccountVisible(account, true)
            }
        }
    }

    fun setStarred(id: Long, starred: Boolean) {
        viewModelScope.launch { repository.setStarred(id, starred) }
    }
}
