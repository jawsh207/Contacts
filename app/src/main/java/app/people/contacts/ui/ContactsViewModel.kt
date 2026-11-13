package app.people.contacts.ui

import android.app.Application
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.people.contacts.R
import app.people.contacts.calendar.BirthdayCalendar
import app.people.contacts.calendar.BirthdayWorker
import app.people.contacts.data.AccountKeys
import app.people.contacts.data.BIRTHDAY_WINDOW_DAYS
import app.people.contacts.data.ContactAccount
import app.people.contacts.data.ContactDetails
import app.people.contacts.data.ContactSummary
import app.people.contacts.data.ContactWriter
import app.people.contacts.data.ContactsRepository
import app.people.contacts.data.DuplicateSet
import app.people.contacts.data.Label
import app.people.contacts.data.PhotoChange
import app.people.contacts.data.renameOnDeviceAccount as renameAccountContacts
import app.people.contacts.data.LabelStore
import app.people.contacts.data.RecentEntry
import app.people.contacts.data.Settings
import app.people.contacts.data.SortOrder
import app.people.contacts.data.UpcomingBirthday
import app.people.contacts.data.VCardReader
import app.people.contacts.data.VCardWriter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A contact page's state: still loading, or loaded ([details] null if the contact is gone). */
data class DetailState(val loaded: Boolean, val details: ContactDetails?)

/** Contacts deleted a moment ago, gone from view but still undoable; [name] is set for one. */
data class PendingDeletion(val token: Long, val contacts: List<Pair<Long, String>>, val name: String?)

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

    /** Contact ids just deleted, waiting out the Undo before they're really deleted. */
    private val pendingIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Contacts in accounts that aren't hidden, in the chosen order, with names shown in that
     * order too ("Last, First" when sorted by last name); null until the first load. Includes
     * contacts without a phone number, for labels, duplicates and export.
     */
    val shownContacts: StateFlow<List<ContactSummary>?> = combine(allContacts, sortOrder, settings.hiddenAccounts, pendingIds) { all, order, hidden, pending ->
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        all?.filter { c -> c.id !in pending && (c.accounts.isEmpty() || c.accounts.any { (type, name) -> !AccountKeys.isHidden(type, name, hidden) }) }
            ?.sortedWith { a, b -> collator.compare(a.sortName(order), b.sortName(order)) }
            ?.map { if (order == SortOrder.LastName && it.nameAlternative.isNotBlank()) it.copy(name = it.nameAlternative) else it }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hideWithoutPhone: StateFlow<Boolean> = settings.hideWithoutPhone.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** What the list, search and Highlights show: [shownContacts], less those without a number if chosen. */
    val contacts: StateFlow<List<ContactSummary>?> = combine(shownContacts, hideWithoutPhone) { list, hide ->
        if (hide) list?.filter { it.numbers.isNotEmpty() } else list
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One contact's details, reloaded whenever contacts change. */
    fun details(id: Long): Flow<DetailState> =
        changes.mapLatest { repository.loadDetails(id) }.map { DetailState(true, it) }

    /** Birthdays today and in the next [BIRTHDAY_WINDOW_DAYS] days, soonest first. */
    val birthdays: StateFlow<List<UpcomingBirthday>> = combine(
        shownContacts.filterNotNull(),
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
        if (ContactsContract.isProfileId(id)) return
        viewModelScope.launch { settings.recordView(id, System.currentTimeMillis()) }
    }

    /** The user's own card ("Your info"); null until loaded or if there isn't one. */
    val profile: StateFlow<ContactSummary?> = accessChanges
        .flatMapLatest { merge(contentChanges(app, ContactsContract.Profile.CONTENT_URI), changes) }
        .mapLatest { repository.loadProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val labelStore = LabelStore(app)

    /** Labels, reloaded when contacts or groups change. */
    val labels: StateFlow<List<Label>?> = accessChanges
        .flatMapLatest { merge(contentChanges(app, ContactsContract.Groups.CONTENT_URI), changes) }
        .mapLatest { labelStore.load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sets of contacts that look like the same person, for Organize's Merge duplicates. */
    val duplicates: StateFlow<List<DuplicateSet>> = combine(shownContacts.filterNotNull(), settings.dismissedDuplicates) { list, dismissed ->
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
            if (renameAccountContacts(getApplication<Application>(), account, name)) {
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

    /** Turns the birthday calendar on (filling it now and daily) or off (removing it). */
    fun setBirthdayCalendar(on: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            settings.setBirthdayCalendar(on)
            if (on) {
                BirthdayWorker.schedule(app)
                BirthdayCalendar.sync(app)
            } else {
                BirthdayWorker.cancel(app)
                BirthdayCalendar.remove(app)
            }
        }
    }

    init {
        // While the birthday calendar is on, keep it in step with contacts as they change.
        viewModelScope.launch {
            combine(settings.birthdayCalendar, changes) { on, _ -> on }.collectLatest { on ->
                if (on) {
                    BirthdayWorker.schedule(getApplication<Application>())
                    delay(2_000)
                    BirthdayCalendar.sync(getApplication<Application>())
                }
            }
        }
    }

    /** Saves every contact shown to a .vcf file. */
    suspend fun exportTo(uri: android.net.Uri): Boolean {
        val app = getApplication<Application>()
        val keys = (shownContacts.value ?: shownContacts.filterNotNull().first()).map { it.lookupKey }.filter { it.isNotEmpty() }
        return app.contentResolver.openOutputStream(uri)?.use { VCardWriter.export(app, keys, it) } ?: false
    }

    /** Adds every contact in a .vcf file to [account]; returns how many were added. */
    suspend fun importFrom(uri: android.net.Uri, account: ContactAccount): Int {
        val app = getApplication<Application>()
        val text = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { app.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
        } ?: return 0
        return VCardReader.parse(text).count { (contact, photo) ->
            contact.hasContent && writer.save(
                null, contact.copy(account = account),
                if (photo != null) PhotoChange.Set(photo) else PhotoChange.Keep,
            ) != null
        }
    }

    fun setRingtone(id: Long, uri: String?) {
        viewModelScope.launch { repository.setRingtone(id, uri) }
    }

    fun setStarred(id: Long, starred: Boolean) {
        viewModelScope.launch { repository.setStarred(id, starred) }
    }

    fun setStarred(ids: Collection<Long>, starred: Boolean) {
        viewModelScope.launch { ids.forEach { repository.setStarred(it, starred) } }
    }

    fun setHideWithoutPhone(on: Boolean) {
        viewModelScope.launch { settings.setHideWithoutPhone(on) }
    }

    /** Makes a number or email the default, or clears the default of its kind. */
    fun setDefault(contactId: Long, mimeType: String, dataId: Long, on: Boolean) {
        viewModelScope.launch { repository.setDefault(contactId, mimeType, dataId, on) }
    }

    /**
     * Splits a linked contact apart. The pieces won't be suggested in Merge duplicates again;
     * [onDone] gets whether it worked.
     */
    fun unlink(contactId: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val pieces = repository.unlink(contactId)
            if (pieces.size > 1) settings.dismissDuplicate(pieces.sorted().joinToString("-"))
            onDone(pieces.isNotEmpty())
        }
    }

    /** Deleted contacts waiting for their Undo to run out; null when there are none. */
    val pendingDeletion = MutableStateFlow<PendingDeletion?>(null)

    /**
     * Deletes contacts (id, lookup key) after a short wait in which [undoDelete] brings them
     * back. They leave the lists straight away. A newer deletion finishes the one before.
     */
    fun delete(contacts: List<Pair<Long, String>>, name: String?) {
        if (contacts.isEmpty()) return
        pendingDeletion.value?.let { finishDelete(it) }
        val pending = PendingDeletion(System.nanoTime(), contacts, name)
        pendingIds.update { it + contacts.map { c -> c.first } }
        pendingDeletion.value = pending
        viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            if (pendingDeletion.value?.token == pending.token) finishDelete(pending)
        }
    }

    fun undoDelete() {
        val pending = pendingDeletion.value ?: return
        pendingDeletion.value = null
        pendingIds.update { it - pending.contacts.map { c -> c.first }.toSet() }
    }

    private fun finishDelete(pending: PendingDeletion) {
        if (pendingDeletion.value?.token == pending.token) pendingDeletion.value = null
        // Not tied to this screen's lifetime, so closing the app doesn't stop it halfway.
        deleteScope.launch {
            val failed = pending.contacts.filter { (id, key) -> !writer.delete(id, key) }
            // Deleted ones stay hidden (their ids aren't reused) so they can't flash back before
            // the list reloads; ones that couldn't be deleted come back.
            if (failed.isNotEmpty()) {
                pendingIds.update { it - failed.map { c -> c.first }.toSet() }
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication<Application>(), R.string.delete_failed, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCleared() {
        // Leaving the app ends the wait: what was deleted is deleted.
        pendingDeletion.value?.let { finishDelete(it) }
    }

    private companion object {
        const val UNDO_WINDOW_MS = 8_000L
        val deleteScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    }
}
