package app.people.contacts.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import app.people.contacts.util.hasPermission
import app.people.contacts.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and changes contacts in Android's contacts provider. */
class ContactsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    private fun canRead() = context.hasPermission(Manifest.permission.READ_CONTACTS)

    /** Every contact with a name, with its numbers and emails for search. Unsorted. */
    suspend fun loadAll(): List<ContactSummary> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val numbers = HashMap<Long, MutableList<String>>()
        val emails = HashMap<Long, MutableList<String>>()
        runCatching {
            resolver.query(
                Data.CONTENT_URI,
                arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1),
                "${Data.MIMETYPE} IN (?, ?)",
                arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val value = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val target = if (c.getString(1) == Phone.CONTENT_ITEM_TYPE) numbers else emails
                    target.getOrPut(c.getLong(0)) { mutableListOf() }.add(value)
                }
            }
        }
        val out = ArrayList<ContactSummary>()
        runCatching {
            resolver.query(
                Contacts.CONTENT_URI,
                arrayOf(
                    Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY,
                    Contacts.DISPLAY_NAME_ALTERNATIVE, Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED,
                ),
                "${Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(2) ?: continue
                    out += ContactSummary(
                        id = id,
                        lookupKey = c.getString(1).orEmpty(),
                        name = name,
                        nameAlternative = c.getString(3) ?: name,
                        photoUri = c.getString(4),
                        starred = c.getInt(5) == 1,
                        numbers = numbers[id].orEmpty(),
                        emails = emails[id].orEmpty(),
                    )
                }
            }
        }
        out
    }

    /** One contact's full details, or null if it no longer exists. */
    suspend fun loadDetails(id: Long): ContactDetails? = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext null
        val base = resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            arrayOf(
                Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI,
                Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            ContactDetails(
                id = id,
                lookupKey = c.getString(0).orEmpty(),
                name = c.getString(1).orEmpty(),
                photoUri = c.getString(2),
                thumbnailUri = c.getString(3),
                starred = c.getInt(4) == 1,
            )
        } ?: return@withContext null
        readData(base, groupTitles())
    }

    private fun readData(base: ContactDetails, groups: Map<Long, String>): ContactDetails {
        val res = context.resources
        val phones = ArrayList<LabeledValue>()
        val emails = ArrayList<LabeledValue>()
        val addresses = ArrayList<LabeledValue>()
        val events = ArrayList<LabeledValue>()
        val websites = ArrayList<String>()
        val relations = ArrayList<LabeledValue>()
        val notes = ArrayList<String>()
        val labels = ArrayList<String>()
        var nickname: String? = null
        var company: String? = null
        var title: String? = null
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4),
            "${Data.CONTACT_ID} = ?",
            arrayOf(base.id.toString()),
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val data1 = c.getString(1)?.trim().orEmpty()
                val type = if (c.isNull(2)) 0 else c.getInt(2)
                val custom = c.getString(3)
                when (c.getString(0)) {
                    Phone.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && phones.none { numberKey(it.value) == numberKey(data1) }) {
                        phones += LabeledValue(data1, Phone.getTypeLabel(res, type, custom).toString())
                    }
                    Email.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && emails.none { it.value.equals(data1, true) }) {
                        emails += LabeledValue(data1, Email.getTypeLabel(res, type, custom).toString())
                    }
                    StructuredPostal.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty()) {
                        addresses += LabeledValue(data1, StructuredPostal.getTypeLabel(res, type, custom).toString())
                    }
                    Event.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty()) {
                        events += LabeledValue(data1, Event.getTypeLabel(res, type, custom).toString(), type)
                    }
                    Relation.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty()) {
                        relations += LabeledValue(data1, Relation.getTypeLabel(res, type, custom).toString())
                    }
                    Website.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && data1 !in websites) websites += data1
                    Note.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && data1 !in notes) notes += data1
                    Nickname.CONTENT_ITEM_TYPE -> if (nickname == null && data1.isNotEmpty()) nickname = data1
                    Organization.CONTENT_ITEM_TYPE -> {
                        if (company == null && data1.isNotEmpty()) company = data1
                        val jobTitle = c.getString(4)?.trim()
                        if (title == null && !jobTitle.isNullOrEmpty()) title = jobTitle
                    }
                    GroupMembership.CONTENT_ITEM_TYPE -> data1.toLongOrNull()?.let { groups[it] }
                        ?.let { if (it !in labels) labels += it }
                }
            }
        }
        return base.copy(
            phones = phones, emails = emails, addresses = addresses, events = events,
            websites = websites, relations = relations, nickname = nickname, company = company,
            jobTitle = title, notes = notes, labels = labels.sortedWith(String.CASE_INSENSITIVE_ORDER),
        )
    }

    /** User-made labels (groups) by row id; system groups like "My Contacts" are left out. */
    private fun groupTitles(): Map<Long, String> = buildMap {
        runCatching {
            resolver.query(
                Groups.CONTENT_URI,
                arrayOf(Groups._ID, Groups.TITLE),
                "${Groups.DELETED} = 0 AND ${Groups.SYSTEM_ID} IS NULL AND ${Groups.TITLE} IS NOT NULL",
                null, null,
            )?.use { c -> while (c.moveToNext()) put(c.getLong(0), c.getString(1)) }
        }
    }

    /** Adds the contact to, or removes it from, Favorites. */
    suspend fun setStarred(id: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.update(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
                ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) },
                null, null,
            )
        }
    }
}
