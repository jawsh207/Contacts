package app.people.contacts.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A label (a contacts group): its name, the account it belongs to, and who's in it. */
data class Label(
    val id: Long,
    val title: String,
    val accountType: String?,
    val accountName: String?,
    val memberIds: Set<Long>,
)

/** Reads and changes labels. Labels belong to one account, as Android's contact groups do. */
class LabelStore(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** Every label the user made (system groups like "My Contacts" aren't labels), by name. */
    suspend fun load(): List<Label> = withContext(Dispatchers.IO) {
        val members = HashMap<Long, MutableSet<Long>>()
        runCatching {
            resolver.query(
                Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, GroupMembership.GROUP_ROW_ID),
                "${Data.MIMETYPE} = ?", arrayOf(GroupMembership.CONTENT_ITEM_TYPE), null,
            )?.use { c -> while (c.moveToNext()) members.getOrPut(c.getLong(1)) { HashSet() }.add(c.getLong(0)) }
        }
        val labels = ArrayList<Label>()
        runCatching {
            resolver.query(
                Groups.CONTENT_URI,
                arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_TYPE, Groups.ACCOUNT_NAME),
                "${Groups.DELETED} = 0 AND ${Groups.SYSTEM_ID} IS NULL AND ${Groups.TITLE} IS NOT NULL",
                null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    labels += Label(id, c.getString(1), c.getString(2), c.getString(3), members[id].orEmpty())
                }
            }
        }
        labels.sortedBy { it.title.lowercase() }
    }

    /** Makes a label in [account]. */
    suspend fun create(title: String, account: ContactAccount): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.insert(
                Groups.CONTENT_URI,
                ContentValues().apply {
                    put(Groups.TITLE, title)
                    put(Groups.ACCOUNT_NAME, account.name)
                    put(Groups.ACCOUNT_TYPE, account.type)
                    put(Groups.GROUP_VISIBLE, 1)
                },
            ) != null
        }.getOrDefault(false)
    }

    suspend fun rename(label: Label, title: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.update(
                ContentUris.withAppendedId(Groups.CONTENT_URI, label.id),
                ContentValues().apply { put(Groups.TITLE, title) }, null, null,
            ) > 0
        }.getOrDefault(false)
    }

    /** Deletes the label. Its contacts stay; they're just no longer in it. */
    suspend fun delete(label: Label): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.delete(ContentUris.withAppendedId(Groups.CONTENT_URI, label.id), null, null) > 0 }
            .getOrDefault(false)
    }

    /**
     * Adds contacts to the label. A label can only hold contacts stored in its account, so
     * contacts with no copy there are skipped. Returns how many were added.
     */
    suspend fun addMembers(label: Label, contactIds: Collection<Long>): Int = withContext(Dispatchers.IO) {
        val ops = ArrayList<ContentProviderOperation>()
        contactIds.filter { it !in label.memberIds }.forEach { contactId ->
            val rawId = rawContactIn(contactId, label.accountType, label.accountName) ?: return@forEach
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, rawId)
                .withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                .withValue(GroupMembership.GROUP_ROW_ID, label.id)
                .build()
        }
        if (ops.isEmpty()) return@withContext 0
        runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops); ops.size }.getOrDefault(0)
    }

    suspend fun removeMember(label: Label, contactId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(
                Data.CONTENT_URI,
                "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} = ?",
                arrayOf(contactId.toString(), GroupMembership.CONTENT_ITEM_TYPE, label.id.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    /** The contact's raw contact in the given account, if it has one there. */
    private fun rawContactIn(contactId: Long, type: String?, name: String?): Long? {
        val accountMatch = if (type.isNullOrEmpty()) {
            "(${RawContacts.ACCOUNT_TYPE} IS NULL OR ${RawContacts.ACCOUNT_TYPE} = '')"
        } else {
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?"
        }
        val args = if (type.isNullOrEmpty()) arrayOf(contactId.toString()) else arrayOf(contactId.toString(), type, name.orEmpty())
        return resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0 AND $accountMatch", args, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }
}
