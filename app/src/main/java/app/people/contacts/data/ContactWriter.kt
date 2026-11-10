package app.people.contacts.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

private val nextKey = AtomicLong(1)

/** A fresh UI key for an editor row. */
fun newFieldKey(): Long = nextKey.getAndIncrement()

/** Loads contacts for editing, and saves and deletes them. */
class ContactWriter(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** The contact as the editor needs it, with every row's id; null if it's gone. */
    suspend fun load(contactId: Long): EditableContact? = withContext(Dispatchers.IO) {
        val (nameRawId, photoUri) = resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            arrayOf(Contacts.NAME_RAW_CONTACT_ID, Contacts.PHOTO_URI),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) to c.getString(1) else null } ?: return@withContext null

        var contact = EditableContact(contactId = contactId, rawContactId = nameRawId, photoUri = photoUri)
        val phones = ArrayList<EditField>()
        val emails = ArrayList<EditField>()
        val addresses = ArrayList<EditField>()
        val events = ArrayList<EditField>()
        val websites = ArrayList<EditField>()
        val labels = HashSet<Long>()
        resolver.query(
            dataUriFor(contactId),
            arrayOf(
                Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3,
                Data.DATA4, Data.DATA5, Data.DATA6,
            ),
            "${Data.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${Data.RAW_CONTACT_ID} = $nameRawId DESC, ${Data.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val raw = c.getLong(1)
                val data1 = c.getString(3).orEmpty()
                val type = if (c.isNull(4)) 0 else c.getInt(4)
                fun field() = EditField(newFieldKey(), id, data1, type, c.getString(5))
                when (c.getString(2)) {
                    // Single fields come from the raw contact whose name the contact shows.
                    StructuredName.CONTENT_ITEM_TYPE -> if (contact.nameId == 0L && raw == nameRawId) {
                        val given = c.getString(4).orEmpty() // GIVEN_NAME
                        val family = c.getString(5).orEmpty() // FAMILY_NAME
                        val prefix = c.getString(6).orEmpty() // PREFIX
                        val middle = c.getString(7).orEmpty() // MIDDLE_NAME
                        val suffix = c.getString(8).orEmpty() // SUFFIX
                        val partsEmpty = listOf(given, family, prefix, middle, suffix).all { it.isBlank() }
                        contact = contact.copy(
                            nameId = id,
                            // A name stored only in its display form (some imports) goes in First.
                            first = if (partsEmpty) data1 else given,
                            last = family, prefix = prefix, middle = middle, suffix = suffix,
                        )
                    }
                    Nickname.CONTENT_ITEM_TYPE -> if (contact.nicknameId == 0L) contact = contact.copy(nicknameId = id, nickname = data1)
                    Organization.CONTENT_ITEM_TYPE -> if (contact.organizationId == 0L) {
                        contact = contact.copy(organizationId = id, company = data1, title = c.getString(6).orEmpty())
                    }
                    Note.CONTENT_ITEM_TYPE -> if (contact.noteId == 0L) contact = contact.copy(noteId = id, note = data1)
                    Phone.CONTENT_ITEM_TYPE -> phones += field()
                    Email.CONTENT_ITEM_TYPE -> emails += field()
                    StructuredPostal.CONTENT_ITEM_TYPE -> addresses += field()
                    Event.CONTENT_ITEM_TYPE -> events += field()
                    Website.CONTENT_ITEM_TYPE -> websites += field()
                    GroupMembership.CONTENT_ITEM_TYPE -> data1.toLongOrNull()?.let { labels += it }
                }
            }
        }
        // The account new details go to: the one holding the contact's name.
        val account = resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, nameRawId),
            arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE), null, null, null,
        )?.use { c -> if (c.moveToFirst()) ContactAccount(c.getString(0), c.getString(1), c.getString(0).orEmpty()) else null }
        contact.copy(
            account = account, phones = phones, emails = emails, addresses = addresses, events = events,
            websites = websites, labelIds = labels,
        )
    }

    /**
     * Saves [edited]. [original] is what was loaded (null for a new contact), so only what
     * changed is written. Returns the contact's id, or null if saving failed.
     */
    suspend fun save(original: EditableContact?, edited: EditableContact, photo: PhotoChange): Long? =
        withContext(Dispatchers.IO) {
            val ops = ArrayList<ContentProviderOperation>()
            val rawId = edited.rawContactId
            // New rows: into the existing raw contact, or a new one created as the first operation.
            if (rawId == null) {
                ops += ContentProviderOperation.newInsert(
                    if (edited.isProfile) ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI else RawContacts.CONTENT_URI
                )
                    .withValue(RawContacts.ACCOUNT_NAME, edited.account?.name)
                    .withValue(RawContacts.ACCOUNT_TYPE, edited.account?.type)
                    .build()
            }
            val insertInto: (ContentProviderOperation.Builder) -> ContentProviderOperation.Builder = { builder ->
                if (rawId != null) builder.withValue(Data.RAW_CONTACT_ID, rawId)
                else builder.withValueBackReference(Data.RAW_CONTACT_ID, 0)
            }
            val rows = RowWriter(ops, insertInto)

            rows.single(
                original?.nameId ?: 0L, StructuredName.CONTENT_ITEM_TYPE,
                mapOf(
                    StructuredName.PREFIX to edited.prefix, StructuredName.GIVEN_NAME to edited.first,
                    StructuredName.MIDDLE_NAME to edited.middle, StructuredName.FAMILY_NAME to edited.last,
                    StructuredName.SUFFIX to edited.suffix,
                ),
                // The display name is rebuilt from the parts.
                extra = mapOf(StructuredName.DISPLAY_NAME to null),
                changed = original == null || listOf(original.prefix, original.first, original.middle, original.last, original.suffix) !=
                    listOf(edited.prefix, edited.first, edited.middle, edited.last, edited.suffix),
            )
            rows.single(original?.nicknameId ?: 0L, Nickname.CONTENT_ITEM_TYPE, mapOf(Nickname.NAME to edited.nickname),
                changed = original?.nickname != edited.nickname)
            rows.single(
                original?.organizationId ?: 0L, Organization.CONTENT_ITEM_TYPE,
                mapOf(Organization.COMPANY to edited.company, Organization.TITLE to edited.title),
                changed = original == null || original.company != edited.company || original.title != edited.title,
            )
            rows.single(original?.noteId ?: 0L, Note.CONTENT_ITEM_TYPE, mapOf(Note.NOTE to edited.note),
                changed = original?.note != edited.note)

            rows.repeating(Phone.CONTENT_ITEM_TYPE, original?.phones, edited.phones, Phone.NUMBER, Phone.TYPE, Phone.LABEL)
            rows.repeating(Email.CONTENT_ITEM_TYPE, original?.emails, edited.emails, Email.ADDRESS, Email.TYPE, Email.LABEL)
            rows.repeating(
                StructuredPostal.CONTENT_ITEM_TYPE, original?.addresses, edited.addresses,
                StructuredPostal.FORMATTED_ADDRESS, StructuredPostal.TYPE, StructuredPostal.LABEL,
                // A changed address is stored as one line; clear the old parts so they don't disagree.
                cleared = listOf(
                    StructuredPostal.STREET, StructuredPostal.POBOX, StructuredPostal.NEIGHBORHOOD,
                    StructuredPostal.CITY, StructuredPostal.REGION, StructuredPostal.POSTCODE, StructuredPostal.COUNTRY,
                ),
            )
            rows.repeating(Event.CONTENT_ITEM_TYPE, original?.events, edited.events, Event.START_DATE, Event.TYPE, Event.LABEL)
            rows.repeating(Website.CONTENT_ITEM_TYPE, original?.websites, edited.websites, Website.URL, Website.TYPE, Website.LABEL)
            rows.labels(edited.contactId, original?.labelIds.orEmpty(), edited.labelIds)

            when (photo) {
                PhotoChange.Keep -> Unit
                PhotoChange.Remove -> if (edited.contactId != null) {
                    ops += ContentProviderOperation.newDelete(dataUriFor(edited.contactId))
                        .withSelection("${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                            arrayOf(edited.contactId.toString(), Photo.CONTENT_ITEM_TYPE))
                        .build()
                }
                is PhotoChange.Set -> {
                    if (edited.contactId != null) {
                        ops += ContentProviderOperation.newDelete(dataUriFor(edited.contactId))
                            .withSelection("${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                                arrayOf(edited.contactId.toString(), Photo.CONTENT_ITEM_TYPE))
                            .build()
                    }
                    ops += insertInto(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                        .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        .withValue(Photo.PHOTO, photo.jpeg)
                        .withValue(Data.IS_SUPER_PRIMARY, 1)
                        .build()
                }
            }

            if (ops.isEmpty()) return@withContext edited.contactId
            val results = runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops) }.getOrNull()
                ?: return@withContext null
            if (edited.contactId != null) return@withContext edited.contactId
            // New contact: find the contact the new raw contact joined.
            val newRaw = results.firstOrNull()?.uri ?: return@withContext null
            resolver.query(newRaw, arrayOf(RawContacts.CONTACT_ID), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        }

    /**
     * Moves a contact to [account]: saves a copy there, then deletes the copies it had. Labels
     * belong to accounts, so they don't come along. Returns the moved contact's id.
     */
    suspend fun move(contactId: Long, account: ContactAccount): Long? = withContext(Dispatchers.IO) {
        val oldRaw = resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(contactId.toString()), null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        val loaded = load(contactId) ?: return@withContext null
        val photo = runCatching {
            Contacts.openContactPhotoInputStream(resolver, ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), true)
                ?.use { it.readBytes() }
        }.getOrNull()
        fun List<EditField>.fresh() = map { it.copy(dataId = 0) }
        val copy = loaded.copy(
            contactId = null, rawContactId = null, account = account, nameId = 0, nicknameId = 0,
            organizationId = 0, noteId = 0, labelIds = emptySet(),
            phones = loaded.phones.fresh(), emails = loaded.emails.fresh(), addresses = loaded.addresses.fresh(),
            events = loaded.events.fresh(), websites = loaded.websites.fresh(),
        )
        val newId = save(null, copy, if (photo != null) PhotoChange.Set(photo) else PhotoChange.Keep) ?: return@withContext null
        // The copy's own raw contacts, told apart from the old ones it may have been joined with.
        val newRaw = resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(newId.toString()), null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty().filter { it !in oldRaw }
        if (oldRaw.isNotEmpty()) {
            runCatching {
                resolver.delete(RawContacts.CONTENT_URI, "${RawContacts._ID} IN (${oldRaw.joinToString(",")})", null)
            }
        }
        // Where the copy ended up once the old ones were gone.
        newRaw.firstOrNull()?.let { raw ->
            resolver.query(ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw), arrayOf(RawContacts.CONTACT_ID), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        } ?: newId
    }

    /** Deletes the contact everywhere it's stored. */
    suspend fun delete(contactId: Long, lookupKey: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(Contacts.getLookupUri(contactId, lookupKey), null, null) > 0
        }.getOrDefault(false)
    }
}

/** Builds the insert, update and delete operations for one save. */
private class RowWriter(
    private val ops: MutableList<ContentProviderOperation>,
    private val insertInto: (ContentProviderOperation.Builder) -> ContentProviderOperation.Builder,
) {
    private fun rowUri(id: Long) = ContentUris.withAppendedId(Data.CONTENT_URI, id)

    /** One-per-contact fields: update, insert or delete the row as its values now need. */
    fun single(
        id: Long,
        mimeType: String,
        values: Map<String, String>,
        changed: Boolean,
        extra: Map<String, String?> = emptyMap(),
    ) {
        if (!changed) return
        val empty = values.values.all { it.isBlank() }
        when {
            id != 0L && empty -> ops += ContentProviderOperation.newDelete(rowUri(id)).build()
            id != 0L -> ops += ContentProviderOperation.newUpdate(rowUri(id)).withValues(values, extra).build()
            !empty -> ops += insertInto(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, mimeType).withValues(values, extra).build()
        }
    }

    /** Label memberships: leave labels no longer chosen, join newly chosen ones. */
    fun labels(contactId: Long?, before: Set<Long>, after: Set<Long>) {
        if (contactId != null) {
            (before - after).forEach { group ->
                ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection(
                        "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} = ?",
                        arrayOf(contactId.toString(), GroupMembership.CONTENT_ITEM_TYPE, group.toString()),
                    )
                    .build()
            }
        }
        (after - before).forEach { group ->
            ops += insertInto(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                .withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                .withValue(GroupMembership.GROUP_ROW_ID, group)
                .build()
        }
    }

    /** Repeating fields: delete removed or emptied rows, update changed ones, insert new ones. */
    fun repeating(
        mimeType: String,
        original: List<EditField>?,
        edited: List<EditField>,
        valueColumn: String,
        typeColumn: String,
        labelColumn: String,
        cleared: List<String> = emptyList(),
    ) {
        val before = original.orEmpty().associateBy { it.dataId }
        val kept = edited.filter { it.value.isNotBlank() }
        val keptIds = kept.map { it.dataId }.toSet()
        before.keys.filter { it !in keptIds }.forEach { ops += ContentProviderOperation.newDelete(rowUri(it)).build() }
        kept.forEach { field ->
            val old = before[field.dataId]
            val builder = when {
                field.dataId == 0L -> insertInto(ContentProviderOperation.newInsert(Data.CONTENT_URI))
                    .withValue(Data.MIMETYPE, mimeType)
                old != null && old.copy(key = field.key) != field -> ContentProviderOperation.newUpdate(rowUri(field.dataId))
                else -> return@forEach
            }
            builder.withValue(valueColumn, field.value.trim())
                .withValue(typeColumn, field.type)
                .withValue(labelColumn, field.label?.takeIf { it.isNotBlank() })
            if (old == null || old.value != field.value) cleared.forEach { builder.withValue(it, null) }
            ops += builder.build()
        }
    }

    private fun ContentProviderOperation.Builder.withValues(
        values: Map<String, String>,
        extra: Map<String, String?>,
    ): ContentProviderOperation.Builder {
        values.forEach { (column, value) -> withValue(column, value.trim().ifEmpty { null }) }
        extra.forEach { (column, value) -> withValue(column, value) }
        return this
    }
}
