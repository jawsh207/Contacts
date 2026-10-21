package com.vayunmathur.contacts.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.vayunmathur.library.log.Log

/** What a Recents entry was: a contact opened in this app, a call, or a text. */
enum class RecentKind {
    VIEWED,
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_MISSED,
    MESSAGE_RECEIVED,
    MESSAGE_SENT,
}

/** One saved contact's latest activity, as shown in Highlights' Recents. */
data class RecentEntry(
    val contact: Contact,
    val timestamp: Long,
    val kind: RecentKind,
    /** The text of a message, for a one-line preview; null for calls and views. */
    val snippet: String? = null,
)

/** A contact opened in this app, and when. */
data class RecentView(val contactId: Long, val timestamp: Long)

/** How many recently viewed contacts are remembered. */
const val MAX_RECENT_VIEWS = 30

/** How many people Recents shows. */
const val MAX_RECENT_ENTRIES = 10

/** How far back into the call log and inbox Recents reads. */
private const val PROVIDER_ROWS = 100

/** Stored as "id:millis,id:millis", newest first. */
fun decodeRecentViews(stored: String?): List<RecentView> =
    stored.orEmpty().split(',').mapNotNull { entry ->
        val id = entry.substringBefore(':').toLongOrNull()
        val time = entry.substringAfter(':', "").toLongOrNull()
        if (id != null && time != null) RecentView(id, time) else null
    }

/** [views] with [contactId] moved to the front at [timestamp], trimmed to [MAX_RECENT_VIEWS]. */
fun addRecentView(views: List<RecentView>, contactId: Long, timestamp: Long): String =
    (listOf(RecentView(contactId, timestamp)) + views.filter { it.contactId != contactId })
        .take(MAX_RECENT_VIEWS)
        .joinToString(",") { "${it.contactId}:${it.timestamp}" }

/**
 * Reads the call log and SMS inbox for Recents. Each source is skipped (not an error) when its
 * permission has not been granted, so Recents degrades to recently viewed contacts.
 */
object RecentActivitySource {

    fun hasCallAndMessageAccess(context: Context): Boolean =
        granted(context, Manifest.permission.READ_CALL_LOG) && granted(context, Manifest.permission.READ_SMS)

    /**
     * Recents, one entry per saved contact at their latest activity, newest first. With
     * [includeCallsAndMessages] it is built from the call log and SMS; otherwise from [views],
     * the contacts recently opened in this app. Numbers not in the address book are ignored.
     * Runs provider queries; call off the main thread.
     */
    fun load(
        context: Context,
        views: List<RecentView>,
        contactsById: Map<Long, Contact>,
        includeCallsAndMessages: Boolean,
    ): List<RecentEntry> {
        val events = mutableListOf<RecentEntry>()
        if (includeCallsAndMessages) {
            val lookup = NumberLookup(context, contactsById)
            if (granted(context, Manifest.permission.READ_CALL_LOG)) events += recentCalls(context, lookup)
            if (granted(context, Manifest.permission.READ_SMS)) events += recentMessages(context, lookup)
        } else {
            views.forEach { view ->
                contactsById[view.contactId]?.let { events += RecentEntry(it, view.timestamp, RecentKind.VIEWED) }
            }
        }
        return events
            .sortedByDescending { it.timestamp }
            .distinctBy { it.contact.id }
            .take(MAX_RECENT_ENTRIES)
    }

    private fun recentCalls(context: Context, lookup: NumberLookup): List<RecentEntry> {
        val out = mutableListOf<RecentEntry>()
        query(
            context,
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
            "${CallLog.Calls.DATE} DESC",
        ) { number, date, type, _ ->
            val contact = lookup.contactFor(number) ?: return@query
            val kind = when (type) {
                CallLog.Calls.OUTGOING_TYPE -> RecentKind.CALL_OUTGOING
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> RecentKind.CALL_MISSED
                else -> RecentKind.CALL_INCOMING
            }
            out += RecentEntry(contact, date, kind)
        }
        return out
    }

    private fun recentMessages(context: Context, lookup: NumberLookup): List<RecentEntry> {
        val out = mutableListOf<RecentEntry>()
        query(
            context,
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.BODY),
            "${Telephony.Sms.DATE} DESC",
        ) { address, date, type, body ->
            val kind = when (type) {
                Telephony.Sms.MESSAGE_TYPE_INBOX -> RecentKind.MESSAGE_RECEIVED
                Telephony.Sms.MESSAGE_TYPE_SENT -> RecentKind.MESSAGE_SENT
                else -> return@query
            }
            val contact = lookup.contactFor(address) ?: return@query
            // One line is all the row shows; collapse line breaks so the preview reads cleanly.
            val snippet = body?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
            out += RecentEntry(contact, date, kind, snippet)
        }
        return out
    }

    /**
     * Reads up to [PROVIDER_ROWS] (address, date, type[, text]) rows; logs and returns nothing on
     * failure. The fourth column, when projected, is passed as [onRow]'s text.
     */
    private inline fun query(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        sortOrder: String,
        onRow: (address: String, date: Long, type: Int, text: String?) -> Unit,
    ) {
        try {
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                var rows = 0
                while (rows < PROVIDER_ROWS && cursor.moveToNext()) {
                    rows++
                    val address = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val text = if (projection.size > 3) cursor.getString(3) else null
                    onRow(address, cursor.getLong(1), cursor.getInt(2), text)
                }
            }
        } catch (e: SecurityException) {
            Log.error("RecentActivity", "No access to $uri", e)
        } catch (e: IllegalArgumentException) {
            Log.error("RecentActivity", "Bad query for $uri", e)
        }
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Number → contact via the provider's own phone matching, cached per number. */
    private class NumberLookup(private val context: Context, private val contactsById: Map<Long, Contact>) {
        private val cache = HashMap<String, Contact?>()

        fun contactFor(number: String): Contact? = cache.getOrPut(number) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            try {
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                    ?.use { if (it.moveToFirst()) contactsById[it.getLong(0)] else null }
            } catch (e: IllegalArgumentException) {
                Log.error("RecentActivity", "Number lookup failed", e)
                null
            }
        }
    }
}
