package app.people.contacts.data

import android.Manifest
import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import app.people.contacts.util.hasPermission
import app.people.contacts.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a Recents entry is. */
enum class RecentKind { Viewed, CallMissed, CallIncoming, CallOutgoing, TextReceived, TextSent }

/** One Recents entry: a saved contact, when, what happened, and the message or number. */
data class RecentEntry(
    val contact: ContactSummary,
    val time: Long,
    val kind: RecentKind,
    val snippet: String? = null,
    /** The number the call or text was with, so tapping can dial it or open the conversation. */
    val number: String? = null,
)

/** How many entries Recents shows. */
const val MAX_RECENTS = 10

/** Recently viewed contacts that still exist. */
fun recentViews(views: List<Pair<Long, Long>>, contacts: List<ContactSummary>): List<RecentEntry> {
    val byId = contacts.associateBy { it.id }
    return views.mapNotNull { (id, time) -> byId[id]?.let { RecentEntry(it, time, RecentKind.Viewed) } }
        .take(MAX_RECENTS)
}

/** Whether Recents can read calls and texts. */
fun hasCallAndTextAccess(context: Context): Boolean =
    context.hasPermission(Manifest.permission.READ_CALL_LOG) && context.hasPermission(Manifest.permission.READ_SMS)

/**
 * The latest call or text with each saved contact, newest first. Numbers that aren't saved are
 * left out. Empty without call log and SMS access.
 */
suspend fun recentCallsAndTexts(context: Context, contacts: List<ContactSummary>): List<RecentEntry> =
    withContext(Dispatchers.IO) {
        if (!hasCallAndTextAccess(context)) return@withContext emptyList()
        val byNumber = HashMap<String, ContactSummary>()
        contacts.forEach { c -> c.numbers.forEach { n -> numberKey(n).takeIf { it.length >= 7 }?.let { byNumber.putIfAbsent(it, c) } } }
        fun contactFor(number: String?) = number?.let { byNumber[numberKey(it)] }

        val entries = ArrayList<RecentEntry>()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                // Newest first; only the newest SCAN_LIMIT are looked at.
                while (c.position < SCAN_LIMIT - 1 && c.moveToNext()) {
                    val number = c.getString(0)
                    val contact = contactFor(number) ?: continue
                    val kind = when (c.getInt(2)) {
                        CallLog.Calls.OUTGOING_TYPE -> RecentKind.CallOutgoing
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> RecentKind.CallMissed
                        else -> RecentKind.CallIncoming
                    }
                    entries += RecentEntry(contact, c.getLong(1), kind, number = number)
                }
            }
        }
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.BODY),
                null, null, "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                // Newest first; only the newest SCAN_LIMIT are looked at.
                while (c.position < SCAN_LIMIT - 1 && c.moveToNext()) {
                    val number = c.getString(0)
                    val contact = contactFor(number) ?: continue
                    val kind = if (c.getInt(2) == Telephony.Sms.MESSAGE_TYPE_INBOX) RecentKind.TextReceived else RecentKind.TextSent
                    entries += RecentEntry(contact, c.getLong(1), kind, c.getString(3)?.replace('\n', ' ')?.trim(), number)
                }
            }
        }
        entries.sortedByDescending { it.time }.distinctBy { it.contact.id }.take(MAX_RECENTS)
    }

/** How many of the newest calls and texts to look through. */
private const val SCAN_LIMIT = 200
