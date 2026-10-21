@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class, kotlinx.coroutines.FlowPreview::class)

package com.vayunmathur.contacts.util
import android.app.Application
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import com.vayunmathur.library.log.Log
import androidx.collection.LruCache
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vayunmathur.contacts.R
import com.vayunmathur.contacts.data.Address
import com.vayunmathur.contacts.data.CDKEmail
import com.vayunmathur.contacts.data.CDKEvent
import com.vayunmathur.contacts.data.CDKNickname
import com.vayunmathur.contacts.data.CDKPhone
import com.vayunmathur.contacts.data.CDKStructuredPostal
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.contacts.data.ContactDetail
import com.vayunmathur.contacts.data.ContactDetails
import com.vayunmathur.contacts.data.ContactGroup
import com.vayunmathur.contacts.data.ContactPrefill
import com.vayunmathur.contacts.data.Email
import com.vayunmathur.contacts.data.Event
import com.vayunmathur.contacts.data.GroupMembership
import com.vayunmathur.contacts.data.Name
import com.vayunmathur.contacts.data.Nickname
import com.vayunmathur.contacts.data.Note
import com.vayunmathur.contacts.data.Organization
import com.vayunmathur.contacts.data.PhoneNumber
import com.vayunmathur.contacts.data.Photo
import com.vayunmathur.contacts.data.PrefillValue
import com.vayunmathur.contacts.data.LOCAL_ACCOUNT_TYPE
import com.vayunmathur.contacts.data.isDefaultLocalAccount
import com.vayunmathur.contacts.data.isLocalAccountType
import com.vayunmathur.library.util.DataStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vayunmathur.contacts.util.ContactSorting.sortedByNameLocale
import kotlinx.datetime.LocalDate
import kotlin.io.encoding.Base64

data class ContactAccount(val name: String, val type: String)

data class ContactGroupMembership(val contactId: Long, val groupId: Long)

/**
 * Implements [ContactsActions] so a binder can hand itself straight to a stateless screen;
 * the navigating members keep their no-op defaults and are overridden per screen, since the
 * ViewModel has no back stack.
 */
/** Query-token separator. Hoisted so a keystroke does not recompile the pattern. */
private val WHITESPACE = Regex("\\s+")

/** Stops the WhileSubscribed sharing timeout for contact flows. */
private const val SUBSCRIBE_TIMEOUT = 5_000L

/** Debounce for coalesced system-contact change notifications. */
private const val SYNC_DEBOUNCE_MILLIS = 300L

class ContactViewModel(application: Application) : AndroidViewModel(application), ContactsActions {

    internal val dataStore = DataStoreUtils.getInstance(application)

    // Provider-backed in-memory contact list (no local DB). Populated by
    // syncFromSystem() from the system Contacts provider and refreshed via the
    // ContentObserver registered in init.
    internal val allContactsState = MutableStateFlow<List<Contact>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // The contact list starts empty and fills in asynchronously, so screens need to tell
    // "nothing loaded yet" apart from "there really are no contacts".
    private val _hasLoadedContacts = MutableStateFlow(false)
    val hasLoadedContacts: StateFlow<Boolean> = _hasLoadedContacts.asStateFlow()

    val hiddenAccounts: StateFlow<Set<String>> = dataStore.stringSetFlow("hidden_accounts")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private fun accountKey(type: String?, name: String?): String = "${type ?: ""}|${name ?: ""}"

    private fun encodedAccountKey(type: String?, name: String?): String {
        fun enc(raw: String): String =
            raw.replace("%", "%25").replace("|", "%7C").replace(",", "%2C")
        return "${enc(type ?: "")}|${enc(name ?: "")}"
    }

    /** Unfiltered address book for export (ignores search query and hidden accounts). */
    val allContactsForExport: StateFlow<List<com.vayunmathur.contacts.data.Contact>> =
        allContactsState.asStateFlow()

    /**
     * Each contact paired with the lowercased text [filterBySearch] matches against.
     *
     * Built once per change to the address book rather than once per keystroke. The haystack
     * concatenates every name, nickname, phone number, email, note and company a contact has and
     * then lowercases the lot; doing that for the whole book between two frames of typing was the
     * expensive half of search, and none of it depends on what was typed.
     */
    private val searchIndex: StateFlow<List<Pair<com.vayunmathur.contacts.data.Contact, String>>> =
        allContactsState
            .map { all -> all.map { it to searchHaystack(it) } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT), emptyList())

    private fun isInVisibleAccount(c: com.vayunmathur.contacts.data.Contact, hidden: Set<String>): Boolean {
        val key = accountKey(c.accountType, c.accountName)
        val encodedKey = encodedAccountKey(c.accountType, c.accountName)
        // Support legacy hidden entries that stored only accountName, raw "type|name"
        // keys, and separator-safe encoded keys.
        return key !in hidden && encodedKey !in hidden && c.accountName !in hidden
    }

    /** Contacts in visible accounts, ignoring the search box — what the Highlights tab draws from. */
    val visibleContacts: StateFlow<List<com.vayunmathur.contacts.data.Contact>> = combine(
        allContactsState,
        hiddenAccounts,
    ) { all, hidden -> all.filter { isInVisibleAccount(it, hidden) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT), emptyList())

    val contacts: StateFlow<List<com.vayunmathur.contacts.data.Contact>> = combine(
        searchIndex,
        _searchQuery,
        hiddenAccounts
    ) { indexed, query, hidden ->
        val visible = indexed.filter { (c, _) -> isInVisibleAccount(c, hidden) }
        val tokens = query.trim().lowercase().split(WHITESPACE).filter { it.isNotBlank() }
        if (tokens.isEmpty()) visible.map { it.first }
        else filterByTokens(visible, tokens).map { it.first }
    }
        // viewModelScope is Main.immediate, so without this the whole address book was filtered on
        // the UI thread between one frame of typing and the next.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT), emptyList())

    private fun filterByTokens(
        visible: List<Pair<com.vayunmathur.contacts.data.Contact, String>>,
        tokens: List<String>,
    ): List<Pair<com.vayunmathur.contacts.data.Contact, String>> =
        visible.filter { (_, haystack) ->
            tokens.all { token -> haystackMatchesToken(haystack, token) }
        }

    private fun haystackMatchesToken(haystack: String, token: String): Boolean {
        if (haystack.contains(token)) return true
        val normalizedToken = normalizePhoneForCompare(token)
        return normalizedToken.isNotEmpty() &&
            normalizedToken != token &&
            haystack.contains(normalizedToken)
    }

    val groups: StateFlow<List<ContactGroup>> = callbackFlow {
        val resolver = getApplication<Application>().contentResolver
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                launch { send(fetchGroups()) }
            }
        }
        resolver.registerContentObserver(ContactsContract.Groups.CONTENT_URI, true, observer)
        send(fetchGroups())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT), emptyList())

    private fun fetchGroups(): List<ContactGroup> {
        val resolver = getApplication<Application>().contentResolver
        val uri = ContactsContract.Groups.CONTENT_URI
        val projection = arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE)
        val list = mutableListOf<ContactGroup>()
        val selection =
            "${ContactsContract.Groups.GROUP_VISIBLE} = 1 AND ${ContactsContract.Groups.DELETED} = 0"
        resolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
            val titleIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleIdx) ?: "Unnamed"
                list.add(ContactGroup(cursor.getLong(idIdx), title))
            }
        }
        return list.sortedByNameLocale { it.name }
    }

    val contactGroupMemberships: StateFlow<List<ContactGroupMembership>> = contacts.map { contactList ->
        contactList.flatMap { contact ->
            contact.details.groups.map { membership ->
                ContactGroupMembership(contactId = contact.id, groupId = membership.groupId)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT), emptyList())

    internal val accountsState = MutableStateFlow<List<ContactAccount>>(emptyList())
    val accounts: StateFlow<List<ContactAccount>> = accountsState.asStateFlow()

    internal val lastSelectedAccountState = MutableStateFlow<ContactAccount?>(null)

    // Parsed-VCF state for the import screen. null = not yet parsed (or cleared);
    // empty list = parsed and found nothing; non-empty = parsed contacts ready to import.
    internal val parsedVcfContactsState = MutableStateFlow<List<Contact>?>(null)
    val parsedVcfContacts: StateFlow<List<Contact>?> = parsedVcfContactsState.asStateFlow()

    val isCalendarSyncEnabled: StateFlow<Boolean> = dataStore.booleanFlow("calendar_sync_enabled")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Off unless the user turns it on in Settings: Google Contacts doesn't badge every row
    // with its storage account, and the label crowds the Expressive card rows.
    val showAccountLabels: StateFlow<Boolean> = dataStore.booleanFlow("show_account_labels", false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Which name field leads the list ordering. Unknown stored values (e.g. from a newer
     * version) fall back to first-name rather than crashing on `valueOf`.
     */
    val sortMode: StateFlow<ContactSorting.ContactSortMode> = dataStore.stringFlow("sort_mode")
        .map { stored ->
            runCatching { ContactSorting.ContactSortMode.valueOf(stored) }
                .getOrDefault(ContactSorting.ContactSortMode.FirstName)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactSorting.ContactSortMode.FirstName)

    fun setSortMode(mode: ContactSorting.ContactSortMode) {
        viewModelScope.launch { dataStore.setString("sort_mode", mode.name) }
    }

    // Coalesces system-contact change notifications; collected with debounce so a
    // burst of provider writes triggers a single re-sync instead of one per change.
    private val syncTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // Fires on any ContactsContract change (contact/raw/data/account/group). Kept as a
    // field so onCleared() can unregister it and avoid leaking the ContentResolver.
    private val contactsObserver = object : android.database.ContentObserver(
        android.os.Handler(android.os.Looper.getMainLooper())
    ) {
        override fun onChange(selfChange: Boolean) { syncTrigger.tryEmit(Unit) }
    }

    init {
        // notifyForDescendants=true on the top-level authority so any contact/raw/data/
        // account change fires it; debounced so a sync burst causes a single reload.
        getApplication<Application>().contentResolver
            .registerContentObserver(ContactsContract.AUTHORITY_URI, true, contactsObserver)
        viewModelScope.launch {
            // Load immediately so cold-launched screens like InsertOrEdit don't
            // show empty list while waiting for the debounce.
            syncFromSystem()
            syncTrigger.debounce(SYNC_DEBOUNCE_MILLIS).collectLatest { syncFromSystem() }
        }
        loadAccounts()
        loadLastSelectedAccount()
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(contactsObserver)
        super.onCleared()
    }

    override fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * The lowercased text a contact is matched against: names, nicknames, phone numbers, emails,
     * notes and organizations, run together.
     *
     * Built once per contact when the address book changes (see [searchIndex]), not per keystroke.
     * A query matches when every whitespace-separated token is a substring of this.
     */
    private fun searchHaystack(contact: Contact): String = buildString {
        append(contact.details.names.joinToString(" ") { it.value }); append(' ')
        append(contact.details.nicknames.joinToString(" ") { it.nickname }); append(' ')
        append(contact.details.phoneNumbers.joinToString(" ") { it.number }); append(' ')
        val normalized = contact.details.phoneNumbers.joinToString(" ") {
            normalizePhoneForCompare(it.number)
        }
        append(normalized); append(' ')
        append(contact.details.emails.joinToString(" ") { it.address }); append(' ')
        append(contact.details.notes.joinToString(" ") { it.content }); append(' ')
        append(contact.details.orgs.joinToString(" ") { it.company }); append(' ')
        append(contact.details.addresses.joinToString(" ") { it.formattedAddress })
    }.lowercase()

    fun setCalendarSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setBoolean("calendar_sync_enabled", enabled)
            if (enabled) {
                withContext(Dispatchers.IO) {
                    CalendarSyncHelper.syncAll(getApplication())
                }
                CalendarWorker.schedule(getApplication())
            } else {
                withContext(Dispatchers.IO) {
                    CalendarSyncHelper.removeCalendar(getApplication())
                }
                CalendarWorker.cancel(getApplication())
            }
        }
    }

    private val myInfoState = MutableStateFlow<com.vayunmathur.contacts.data.MyInfo?>(null)

    /**
     * "Your info": Android's Me profile in the contacts provider, the device-wide card other
     * apps can read too. Null when the profile is empty. Re-read on every contacts sync.
     */
    val myInfo: StateFlow<com.vayunmathur.contacts.data.MyInfo?> = myInfoState.asStateFlow()

    /** Writes [info] to the Me profile, then re-reads it so the UI shows what was stored. */
    fun saveMyInfo(info: com.vayunmathur.contacts.data.MyInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            com.vayunmathur.contacts.data.MeProfileSource.write(app, info)
            myInfoState.value = com.vayunmathur.contacts.data.MeProfileSource.read(app)
        }
    }

    /**
     * Earlier builds kept "Your info" in app storage. Moves such a copy into the Me profile
     * (only if the profile is still empty, so a newer profile always wins), then clears it.
     */
    private suspend fun migrateStoredMyInfo(app: Application) {
        val stored = dataStore.getStringAwait(LEGACY_MY_INFO_KEY)?.takeIf { it.isNotBlank() } ?: return
        val legacy = runCatching { Json.decodeFromString<com.vayunmathur.contacts.data.MyInfo>(stored) }.getOrNull()
        if (legacy != null && !legacy.isEmpty && com.vayunmathur.contacts.data.MeProfileSource.read(app) == null) {
            com.vayunmathur.contacts.data.MeProfileSource.write(app, legacy)
        }
        dataStore.setString(LEGACY_MY_INFO_KEY, "")
    }

    /** Contacts opened in this app, newest first, for Highlights' Recents. Stays on the device. */
    val recentViews: StateFlow<List<com.vayunmathur.contacts.data.RecentView>> =
        dataStore.stringFlow(RECENT_VIEWS_KEY)
            .map { com.vayunmathur.contacts.data.decodeRecentViews(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun recordRecentView(contactId: Long) {
        viewModelScope.launch {
            dataStore.updateString(RECENT_VIEWS_KEY) { stored ->
                com.vayunmathur.contacts.data.addRecentView(
                    com.vayunmathur.contacts.data.decodeRecentViews(stored),
                    contactId,
                    System.currentTimeMillis(),
                )
            }
        }
    }

    /** Likely duplicates among visible contacts, for Organize's Merge duplicates. */
    val duplicateSets: StateFlow<List<com.vayunmathur.contacts.data.DuplicateSet>> = combine(
        visibleContacts,
        dataStore.stringSetFlow(DISMISSED_DUPLICATES_KEY),
    ) { visible, dismissed -> com.vayunmathur.contacts.data.findDuplicateSets(visible, dismissed) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT), emptyList())

    /** Whether Recents also reads the call log and SMS. Off until the user opts in. */
    val includeCallsAndMessagesInRecents: StateFlow<Boolean> =
        dataStore.booleanFlow(RECENTS_CALLS_MESSAGES_KEY, false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setIncludeCallsAndMessagesInRecents(enabled: Boolean) {
        viewModelScope.launch { dataStore.setBoolean(RECENTS_CALLS_MESSAGES_KEY, enabled) }
    }

    fun setShowAccountLabels(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setBoolean("show_account_labels", enabled)
        }
    }

    /** Requests a debounced re-sync from the system contacts provider. */
    fun loadContacts() {
        syncTrigger.tryEmit(Unit)
    }

    internal suspend fun syncFromSystem() = withContext(Dispatchers.IO) {
        // Broad catch is deliberate: sync crosses the contacts provider and
        // DataStore, and a failed refresh must mark load complete rather
        // than crashing the collector.
        @Suppress("TooGenericExceptionCaught")
        try {
            refreshContactsFromSystem()
        } catch (e: Exception) {
            Log.error("ContactViewModel", "Error loading contacts", e)
        } finally {
            _hasLoadedContacts.value = true
        }
    }

    private suspend fun refreshContactsFromSystem() {
        val app = getApplication<Application>()
        allContactsState.value = com.vayunmathur.contacts.data.Contact.getAllContacts(app)
        migrateStoredMyInfo(app)
        myInfoState.value = com.vayunmathur.contacts.data.MeProfileSource.read(app)
        // Keep the accounts list in step with the contacts; already on IO via syncFromSystem().
        loadAccountsInternal()
    }

    fun getContact(contactId: Long): Contact? {
        return findContact(contactId)
    }

    override fun deleteContact(contact: com.vayunmathur.contacts.data.Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            // The system-contacts ContentObserver picks up the delete and re-syncs.
            deleteProviderContact(contact)
        }
    }

    internal suspend fun deleteProviderContact(contact: com.vayunmathur.contacts.data.Contact) {
        val app = getApplication<Application>()
        com.vayunmathur.contacts.data.Contact.delete(app, contact)
        if (isCalendarSyncEnabled.value) {
            val dateless = contact.copy(details = contact.details.copy(dates = emptyList()))
            CalendarSyncHelper.syncContact(app, dateless)
        }
    }

    // Groups Management
    override fun addGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Groups.TITLE, name)
                put(ContactsContract.Groups.GROUP_VISIBLE, 1)
            }
            resolver.insert(ContactsContract.Groups.CONTENT_URI, values)
        }
    }

    override fun deleteGroup(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            resolver.delete(ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, groupId), null, null)
        }
    }

    override fun saveContact(contact: com.vayunmathur.contacts.data.Contact) {
        viewModelScope.launch(Dispatchers.IO) { persistContact(contact) }
    }

    /** Writes [contact] to the contacts provider. Returns false if the write failed. */
    internal suspend fun persistContact(contact: com.vayunmathur.contacts.data.Contact): Boolean =
        persistProviderContact(contact)

    // ---------------------------------------------------------------------
    // Base64 photo decode cache.
    // ---------------------------------------------------------------------

    private val photoCache = LruCache<String, Bitmap>(32)

    /**
     * Returns the decoded [Bitmap] for the Base64-encoded contact photo, or
     * `null` if decoding fails. Decodes at most once per unique input string
     * across the entire app lifetime (subject to LRU eviction).
     */
    @Synchronized
    override fun decodePhoto(base64: String): Bitmap? {
        photoCache.get(base64)?.let { return it }
        return try {
            val bytes = Base64.decode(base64)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
                photoCache.put(base64, it)
            }
        } catch (e: IllegalArgumentException) {
            Log.error("ContactViewModel", "Error decoding contact photo", e)
            null
        }
    }

    // ---------------------------------------------------------------------
    // EditContactPage form draft state.
    // ---------------------------------------------------------------------

    data class ContactDraft(
        val namePrefix: String = "",
        val firstName: String = "",
        val middleName: String = "",
        val lastName: String = "",
        val nameSuffix: String = "",
        val company: String = "",
        val noteContent: String = "",
        val nickname: String = "",
        val photo: Photo? = null,
        val birthday: LocalDate? = null,
        val accountName: String = "",
        val accountType: String = "",
        val phoneNumbers: List<PhoneNumber> = emptyList(),
        val emails: List<Email> = emptyList(),
        val dates: List<Event> = emptyList(),
        val addresses: List<Address> = emptyList(),
        val groupMemberships: List<GroupMembership> = emptyList(),
    )

    internal val editDraftState = MutableStateFlow<ContactDraft?>(null)
    val editDraft: StateFlow<ContactDraft?> = editDraftState.asStateFlow()

    /** Original contact loaded into the current draft, if any. */
    internal var editingOriginal: Contact? = null
    /** Tracks which contactId the draft was initialized for. `null` = new contact. */
    internal var editingContactId: Long? = null
    /** True once a draft has been initialized at all (distinguishes "new contact" from "uninitialized"). */
    internal var editingInitialized: Boolean = false

    internal fun normalizePhoneForCompare(raw: String): String {
        return raw.filter { it.isDigit() || it == '+' }.trim()
    }
}

private const val RECENT_VIEWS_KEY = "recent_views"
private const val LEGACY_MY_INFO_KEY = "my_info"
internal const val DISMISSED_DUPLICATES_KEY = "dismissed_duplicates"
private const val RECENTS_CALLS_MESSAGES_KEY = "recents_calls_messages"
