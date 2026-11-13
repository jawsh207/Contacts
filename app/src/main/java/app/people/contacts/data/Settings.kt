package app.people.contacts.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** How the contacts list is ordered. The names match what the old app stored. */
enum class SortOrder { FirstName, LastName }

/**
 * The settings file. Its name and keys are the ones the old Contacts app used, so when this app
 * replaces it, the sort order, Recents choice, birthday calendar, hidden accounts and on-device
 * accounts carry over.
 */
private object SettingsFile {
    @Volatile private var store: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> = store ?: synchronized(this) {
        store ?: PreferenceDataStoreFactory.create {
            context.applicationContext.filesDir.resolve("datastore_default.preferences_pb")
        }.also { store = it }
    }
}

/** The app's settings and small bits of state, kept on the device. */
class Settings(context: Context) {
    private val store = SettingsFile.get(context)

    val sortOrder: Flow<SortOrder> = store.data.map { prefs ->
        SortOrder.entries.firstOrNull { it.name == prefs[SORT_MODE] } ?: SortOrder.FirstName
    }

    suspend fun setSortOrder(order: SortOrder) {
        store.edit { it[SORT_MODE] = order.name }
    }

    /** The list leaves out contacts with no phone number. */
    val hideWithoutPhone: Flow<Boolean> = store.data.map { it[HIDE_WITHOUT_PHONE] ?: false }

    suspend fun setHideWithoutPhone(on: Boolean) {
        store.edit { it[HIDE_WITHOUT_PHONE] = on }
    }

    /** Recents shows calls and texts with saved contacts instead of recently viewed contacts. */
    val recentsFromCallsAndTexts: Flow<Boolean> = store.data.map { it[RECENTS_CALLS_TEXTS] ?: false }

    suspend fun setRecentsFromCallsAndTexts(on: Boolean) {
        store.edit { it[RECENTS_CALLS_TEXTS] = on }
    }

    /** Birthdays are copied into the "Contact Birthdays" calendar. */
    val birthdayCalendar: Flow<Boolean> = store.data.map { it[CALENDAR_SYNC] ?: false }

    suspend fun birthdayCalendarNow(): Boolean = birthdayCalendar.first()

    suspend fun setBirthdayCalendar(on: Boolean) {
        store.edit { it[CALENDAR_SYNC] = on }
    }

    /** Accounts whose contacts are hidden, as stored keys (see [AccountKeys.hiddenKey]). */
    val hiddenAccounts: Flow<Set<String>> = store.data.map { it[HIDDEN_ACCOUNTS].orEmpty() }

    suspend fun editHiddenAccounts(change: (Set<String>) -> Set<String>) {
        store.edit { it[HIDDEN_ACCOUNTS] = change(it[HIDDEN_ACCOUNTS].orEmpty()) }
    }

    /** On-device accounts the user made, including ones with no contacts yet (see [AccountKeys.savedEntry]). */
    val savedAccounts: Flow<Set<String>> = store.data.map { it[EXTRA_ACCOUNTS].orEmpty() }

    suspend fun editSavedAccounts(change: (Set<String>) -> Set<String>) {
        store.edit { it[EXTRA_ACCOUNTS] = change(it[EXTRA_ACCOUNTS].orEmpty()) }
    }

    /** The account the last new contact was saved to: (name, type), either may be blank. */
    suspend fun lastAccount(): Pair<String, String>? = store.data.first().let { prefs ->
        val name = prefs[LAST_ACCOUNT_NAME] ?: return@let null
        name to (prefs[LAST_ACCOUNT_TYPE] ?: "")
    }

    suspend fun setLastAccount(name: String?, type: String?) {
        store.edit {
            it[LAST_ACCOUNT_NAME] = name.orEmpty()
            it[LAST_ACCOUNT_TYPE] = type.orEmpty()
        }
    }

    /** Duplicate suggestions the user dismissed, as sorted contact-id lists ("12-40"). */
    val dismissedDuplicates: Flow<Set<String>> = store.data.map { it[DISMISSED_DUPLICATES].orEmpty() }

    suspend fun dismissDuplicate(key: String) {
        store.edit { it[DISMISSED_DUPLICATES] = it[DISMISSED_DUPLICATES].orEmpty() + key }
    }

    /** Contacts opened in this app, newest first, as (contact id, time) pairs. */
    val recentViews: Flow<List<Pair<Long, Long>>> = store.data.map { decodeViews(it[RECENT_VIEWS]) }

    /** Moves [contactId] to the front of the recent views, viewed at [time]. */
    suspend fun recordView(contactId: Long, time: Long) {
        store.edit { prefs ->
            val views = listOf(contactId to time) + decodeViews(prefs[RECENT_VIEWS]).filter { it.first != contactId }
            prefs[RECENT_VIEWS] = views.take(MAX_RECENT_VIEWS).joinToString(",") { "${it.first}:${it.second}" }
        }
    }

    private fun decodeViews(text: String?): List<Pair<Long, Long>> =
        text.orEmpty().split(',').mapNotNull { entry ->
            val (id, time) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (id.toLongOrNull() ?: return@mapNotNull null) to (time.toLongOrNull() ?: return@mapNotNull null)
        }

    private companion object {
        // Keys shared with the old app.
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val RECENTS_CALLS_TEXTS = booleanPreferencesKey("recents_calls_messages")
        val CALENDAR_SYNC = booleanPreferencesKey("calendar_sync_enabled")
        val HIDDEN_ACCOUNTS = stringSetPreferencesKey("hidden_accounts")
        val EXTRA_ACCOUNTS = stringSetPreferencesKey("extra_accounts_set")
        val LAST_ACCOUNT_NAME = stringPreferencesKey("last_account_name")
        val LAST_ACCOUNT_TYPE = stringPreferencesKey("last_account_type")
        // This app's own: the old app's versions used different contact ids.
        val RECENT_VIEWS = stringPreferencesKey("recent_contact_views")
        val DISMISSED_DUPLICATES = stringSetPreferencesKey("dismissed_duplicate_contacts")
        val HIDE_WITHOUT_PHONE = booleanPreferencesKey("hide_contacts_without_phone")
        const val MAX_RECENT_VIEWS = 30
    }
}
