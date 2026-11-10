package app.people.contacts.data

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.RawContacts
import app.people.contacts.util.foldForSearch
import app.people.contacts.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Contacts that look like the same person. */
data class DuplicateSet(val contacts: List<ContactSummary>) {
    /** The same for these contacts in any order; how a dismissed suggestion is remembered. */
    val key: String = contacts.map { it.id }.sorted().joinToString("-")
}

/**
 * Groups contacts that share a name, a phone number or an email address, including through
 * each other (A and B share a number, B and C a name: one set of three). Biggest sets first.
 */
fun findDuplicates(contacts: List<ContactSummary>, dismissed: Set<String>): List<DuplicateSet> {
    if (contacts.size < 2) return emptyList()
    val parent = IntArray(contacts.size) { it }
    fun root(i: Int): Int {
        var r = i
        while (parent[r] != r) r = parent[r]
        var j = i
        while (parent[j] != r) { val next = parent[j]; parent[j] = r; j = next }
        return r
    }
    val firstWith = HashMap<String, Int>()
    contacts.forEachIndexed { index, c ->
        buildList {
            foldForSearch(c.name).replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }?.let { add("n:$it") }
            c.numbers.forEach { n -> numberKey(n).takeIf { it.length >= 7 }?.let { add("p:$it") } }
            c.emails.forEach { e -> e.trim().lowercase().takeIf { it.isNotEmpty() }?.let { add("e:$it") } }
        }.forEach { key ->
            val other = firstWith.putIfAbsent(key, index)
            if (other != null) parent[root(index)] = root(other)
        }
    }
    return contacts.indices.groupBy { root(it) }.values
        .filter { it.size > 1 }
        .map { members -> DuplicateSet(members.map { contacts[it] }) }
        .filter { it.key !in dismissed }
        .sortedWith(compareByDescending<DuplicateSet> { it.contacts.size }.thenBy { it.contacts.first().name.lowercase() })
}

/**
 * Merges a set into one contact by linking it, as Google Contacts does: Android shows the
 * linked copies as a single contact with all their details, and nothing is deleted.
 */
suspend fun mergeDuplicates(context: Context, set: DuplicateSet): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val rawIds = set.contacts.flatMap { contact ->
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(contact.id.toString()), null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
    }.distinct()
    if (rawIds.size < 2) return@withContext false
    val first = rawIds.first()
    val ops = rawIds.drop(1).map { other ->
        ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
            .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
            .withValue(AggregationExceptions.RAW_CONTACT_ID1, first)
            .withValue(AggregationExceptions.RAW_CONTACT_ID2, other)
            .build()
    }
    runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops)); true }.getOrDefault(false)
}
