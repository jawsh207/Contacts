package app.people.contacts.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
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
        val accounts = HashMap<Long, MutableList<Pair<String?, String?>>>()
        runCatching {
            resolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.CONTACT_ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
                "${RawContacts.DELETED} = 0", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val account = c.getString(1) to c.getString(2)
                    val list = accounts.getOrPut(c.getLong(0)) { mutableListOf() }
                    if (account !in list) list += account
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
                        accounts = accounts[id].orEmpty(),
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
                Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED, Contacts.CUSTOM_RINGTONE,
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
                ringtone = c.getString(5)?.takeIf { it.isNotEmpty() },
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
        val sips = ArrayList<LabeledValue>()
        val chats = ArrayList<LabeledValue>()
        val notes = ArrayList<String>()
        val labels = ArrayList<String>()
        var nickname: String? = null
        var phonetic: String? = null
        var company: String? = null
        var title: String? = null
        resolver.query(
            dataUriFor(base.id),
            arrayOf(
                Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4,
                Data.IS_SUPER_PRIMARY, Data.DATA7, Data.DATA8, Data.DATA9,
            ),
            "${Data.CONTACT_ID} = ?",
            arrayOf(base.id.toString()),
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val rowId = c.getLong(0)
                val data1 = c.getString(2)?.trim().orEmpty()
                val type = if (c.isNull(3)) 0 else c.getInt(3)
                val custom = c.getString(4)
                val isDefault = c.getInt(6) == 1
                when (c.getString(1)) {
                    Phone.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && phones.none { numberKey(it.value) == numberKey(data1) }) {
                        phones += LabeledValue(data1, Phone.getTypeLabel(res, type, custom).toString(), type, rowId, isDefault)
                    }
                    Email.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && emails.none { it.value.equals(data1, true) }) {
                        emails += LabeledValue(data1, Email.getTypeLabel(res, type, custom).toString(), type, rowId, isDefault)
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
                    SipAddress.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && sips.none { it.value.equals(data1, true) }) {
                        sips += LabeledValue(data1, SipAddress.getTypeLabel(res, type, custom).toString())
                    }
                    Im.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && chats.none { it.value.equals(data1, true) }) {
                        chats += LabeledValue(data1, Im.getTypeLabel(res, type, custom).toString())
                    }
                    StructuredName.CONTENT_ITEM_TYPE -> if (phonetic == null) {
                        // Phonetic given, middle and family names.
                        phonetic = listOf(c.getString(7), c.getString(8), c.getString(9))
                            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                            .joinToString(" ").takeIf { it.isNotEmpty() }
                    }
                    Website.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && data1 !in websites) websites += data1
                    Note.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty() && data1 !in notes) notes += data1
                    Nickname.CONTENT_ITEM_TYPE -> if (nickname == null && data1.isNotEmpty()) nickname = data1
                    Organization.CONTENT_ITEM_TYPE -> {
                        if (company == null && data1.isNotEmpty()) company = data1
                        val jobTitle = c.getString(5)?.trim()
                        if (title == null && !jobTitle.isNullOrEmpty()) title = jobTitle
                    }
                    GroupMembership.CONTENT_ITEM_TYPE -> data1.toLongOrNull()?.let { groups[it] }
                        ?.let { if (it !in labels) labels += it }
                }
            }
        }
        return base.copy(
            phones = phones, emails = emails, addresses = addresses, events = events,
            websites = websites, relations = relations, sipAddresses = sips, chats = chats,
            phoneticName = phonetic, nickname = nickname, company = company,
            jobTitle = title, notes = notes, labels = labels.sortedWith(String.CASE_INSENSITIVE_ORDER),
            linkedCount = rawContactIds(base.id).size.coerceAtLeast(1),
        )
    }

    /** The stored (raw) contacts linked into one contact. */
    private fun rawContactIds(contactId: Long): List<Long> {
        if (ContactsContract.isProfileId(contactId)) return emptyList()
        return runCatching {
            resolver.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
                "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(contactId.toString()), null,
            )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * Makes a number or email the contact's default, or (with [on] false) leaves the contact
     * with no default of that kind.
     */
    suspend fun setDefault(contactId: Long, mimeType: String, dataId: Long, on: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            // Clear the kind's defaults first; Android keeps one per contact.
            resolver.update(
                dataUriFor(contactId),
                ContentValues().apply { put(Data.IS_SUPER_PRIMARY, 0); put(Data.IS_PRIMARY, 0) },
                "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(contactId.toString(), mimeType),
            )
            if (on) {
                resolver.update(
                    ContentUris.withAppendedId(Data.CONTENT_URI, dataId),
                    ContentValues().apply { put(Data.IS_SUPER_PRIMARY, 1); put(Data.IS_PRIMARY, 1) },
                    null, null,
                )
            }
        }
    }

    /**
     * Splits a contact back into the stored contacts it was linked from, and keeps Android from
     * linking them again. Returns the contacts they now show as, or empty if it failed.
     */
    suspend fun unlink(contactId: Long): List<Long> = withContext(Dispatchers.IO) {
        val raws = rawContactIds(contactId)
        if (raws.size < 2) return@withContext emptyList()
        val ops = ArrayList<ContentProviderOperation>()
        for (i in raws.indices) for (j in i + 1 until raws.size) {
            ops += ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_SEPARATE)
                .withValue(AggregationExceptions.RAW_CONTACT_ID1, raws[i])
                .withValue(AggregationExceptions.RAW_CONTACT_ID2, raws[j])
                .build()
        }
        runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops) }.getOrNull() ?: return@withContext emptyList()
        raws.mapNotNull { raw ->
            resolver.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw), arrayOf(RawContacts.CONTACT_ID), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        }.distinct()
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

    /** Each contact's birthday as stored ("1990-03-04" or "--03-04"), by contact id. */
    suspend fun loadBirthdays(): Map<Long, String> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyMap()
        buildMap {
            runCatching {
                resolver.query(
                    Data.CONTENT_URI,
                    arrayOf(Data.CONTACT_ID, Event.START_DATE),
                    "${Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
                    arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val date = c.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                        putIfAbsent(c.getLong(0), date)
                    }
                }
            }
        }
    }

    /** The user's own card ("Your info", Android's profile), or null if they haven't made one. */
    suspend fun loadProfile(): ContactSummary? = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext null
        runCatching {
            resolver.query(
                ContactsContract.Profile.CONTENT_URI,
                arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(2).orEmpty()
                ContactSummary(c.getLong(0), c.getString(1).orEmpty(), name, name, c.getString(3), starred = false)
            }
        }.getOrNull()
    }

    /** Sets the contact's ringtone; null goes back to the phone's default. */
    suspend fun setRingtone(id: Long, uri: String?) = withContext(Dispatchers.IO) {
        runCatching {
            resolver.update(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
                ContentValues().apply { put(Contacts.CUSTOM_RINGTONE, uri) },
                null, null,
            )
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
