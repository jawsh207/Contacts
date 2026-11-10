package app.people.contacts.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The app's own settings and small bits of state, kept on the device. */
class Settings(context: Context) {
    private val store = context.applicationContext.settingsStore

    /** Recents shows calls and texts with saved contacts instead of recently viewed contacts. */
    val recentsFromCallsAndTexts: Flow<Boolean> = store.data.map { it[RECENTS_CALLS_TEXTS] ?: false }

    suspend fun setRecentsFromCallsAndTexts(on: Boolean) {
        store.edit { it[RECENTS_CALLS_TEXTS] = on }
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
        val RECENTS_CALLS_TEXTS = booleanPreferencesKey("recents_calls_texts")
        val RECENT_VIEWS = stringPreferencesKey("recent_views")
        const val MAX_RECENT_VIEWS = 30
    }
}
