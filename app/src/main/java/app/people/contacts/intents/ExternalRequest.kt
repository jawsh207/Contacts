package app.people.contacts.intents

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Intents.Insert
import android.provider.ContactsContract.RawContacts
import androidx.core.content.IntentCompat
import app.people.contacts.data.EditField
import app.people.contacts.data.EditableContact
import app.people.contacts.data.newFieldKey

/** What kind of item a picking app wants back. */
enum class PickKind { Contact, Phone, Email, Address }

/** What another app (or a shortcut) opened this app to do. */
sealed interface ExternalRequest {
    data class View(val contactId: Long) : ExternalRequest
    data class Edit(val contactId: Long) : ExternalRequest
    /** Create a contact with these details; [returnResult] sends the new contact back. */
    data class Insert(val prefill: EditableContact, val returnResult: Boolean) : ExternalRequest
    /** Add these details to a contact the user picks, or to a new one. */
    data class InsertOrEdit(val prefill: EditableContact) : ExternalRequest
    data class Pick(val kind: PickKind) : ExternalRequest
    data class ImportFile(val uri: Uri) : ExternalRequest
    data object Settings : ExternalRequest
}

private const val SHOW_OR_CREATE = "com.android.contacts.action.SHOW_OR_CREATE_CONTACT"
private val QUICK_CONTACT = setOf("com.android.contacts.action.QUICK_CONTACT", ContactsContract.QuickContact.ACTION_QUICK_CONTACT)
private val VCARD_TYPES = setOf("text/x-vcard", "text/vcard", "application/vcard", "text/vcf", "text/directory")

/** Reads the request out of [intent]; null for a plain launch. Call off the main thread. */
fun parseRequest(context: Context, intent: Intent): ExternalRequest? {
    val action = intent.action ?: return null
    val type = intent.type ?: intent.data?.let { runCatching { context.contentResolver.getType(it) }.getOrNull() }
    return when {
        action == Intent.ACTION_APPLICATION_PREFERENCES -> ExternalRequest.Settings
        action == Intent.ACTION_PICK || action == Intent.ACTION_GET_CONTENT -> when (type) {
            Phone.CONTENT_TYPE -> ExternalRequest.Pick(PickKind.Phone)
            Email.CONTENT_TYPE -> ExternalRequest.Pick(PickKind.Email)
            StructuredPostal.CONTENT_TYPE, StructuredPostal.CONTENT_ITEM_TYPE -> ExternalRequest.Pick(PickKind.Address)
            in VCARD_TYPES -> null
            else -> ExternalRequest.Pick(PickKind.Contact)
        }
        action == Intent.ACTION_INSERT -> ExternalRequest.Insert(prefillFrom(intent), returnResult = true)
        action == Intent.ACTION_INSERT_OR_EDIT -> ExternalRequest.InsertOrEdit(prefillFrom(intent))
        action == SHOW_OR_CREATE -> showOrCreate(context, intent)
        action in QUICK_CONTACT -> intent.data?.let { contactIdFor(context, it) }?.let { ExternalRequest.View(it) }
        (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) && (type in VCARD_TYPES || type == "application/octet-stream") ->
            (intent.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))?.let { ExternalRequest.ImportFile(it) }
        action == Intent.ACTION_VIEW -> intent.data?.let { contactIdFor(context, it) }?.let { ExternalRequest.View(it) }
        action == Intent.ACTION_EDIT -> intent.data?.let { contactIdFor(context, it) }?.let { ExternalRequest.Edit(it) }
        else -> null
    }
}

/** The contact a contacts URI (lookup, contact, raw contact or data row) points at. */
fun contactIdFor(context: Context, uri: Uri): Long? = runCatching {
    val resolver = context.contentResolver
    val segments = uri.pathSegments
    when {
        segments.firstOrNull() == "raw_contacts" -> resolver.query(uri, arrayOf(RawContacts.CONTACT_ID), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        segments.firstOrNull() == "data" -> resolver.query(uri, arrayOf(ContactsContract.Data.CONTACT_ID), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        else -> Contacts.lookupContact(resolver, uri)?.lastPathSegment?.toLongOrNull()
            ?: resolver.query(uri, arrayOf(Contacts._ID), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }
}.getOrNull()

/** "Show this number's contact, or offer to create one": the contact if it exists, else the editor. */
private fun showOrCreate(context: Context, intent: Intent): ExternalRequest {
    val data = intent.data
    val resolver = context.contentResolver
    val existing = runCatching {
        when (data?.scheme) {
            "tel" -> resolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(data.schemeSpecificPart)),
                arrayOf(ContactsContract.PhoneLookup._ID), null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            "mailto" -> resolver.query(
                Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(data.schemeSpecificPart)),
                arrayOf(Email.CONTACT_ID), null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            else -> data?.let { contactIdFor(context, it) }
        }
    }.getOrNull()
    if (existing != null) return ExternalRequest.View(existing)
    val prefill = prefillFrom(intent).let { base ->
        when (data?.scheme) {
            "tel" -> base.copy(phones = base.phones + EditField(newFieldKey(), value = data.schemeSpecificPart, type = Phone.TYPE_MOBILE))
            "mailto" -> base.copy(emails = base.emails + EditField(newFieldKey(), value = data.schemeSpecificPart, type = Email.TYPE_OTHER))
            else -> base
        }
    }
    return ExternalRequest.InsertOrEdit(prefill)
}

/** The details another app passed for a new contact, in the editor's form. */
fun prefillFrom(intent: Intent): EditableContact {
    val extras = intent.extras
    var contact = EditableContact()
    fun phone(value: String?, type: Any?) = value?.takeIf { it.isNotBlank() }?.let {
        EditField(newFieldKey(), value = it, type = (type as? Int) ?: Phone.TYPE_MOBILE, label = type as? String)
    }
    fun email(value: String?, type: Any?) = value?.takeIf { it.isNotBlank() }?.let {
        EditField(newFieldKey(), value = it, type = (type as? Int) ?: Email.TYPE_OTHER, label = type as? String)
    }
    if (extras != null) {
        extras.getString(Insert.NAME)?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            val space = name.lastIndexOf(' ')
            contact = if (space > 0) contact.copy(first = name.substring(0, space), last = name.substring(space + 1))
            else contact.copy(first = name)
        }
        contact = contact.copy(
            company = extras.getString(Insert.COMPANY).orEmpty(),
            title = extras.getString(Insert.JOB_TITLE).orEmpty(),
            note = extras.getString(Insert.NOTES).orEmpty(),
            phones = listOfNotNull(
                phone(extras.getString(Insert.PHONE), extras.get(Insert.PHONE_TYPE)),
                phone(extras.getString(Insert.SECONDARY_PHONE), extras.get(Insert.SECONDARY_PHONE_TYPE)),
                phone(extras.getString(Insert.TERTIARY_PHONE), extras.get(Insert.TERTIARY_PHONE_TYPE)),
            ),
            emails = listOfNotNull(
                email(extras.getString(Insert.EMAIL), extras.get(Insert.EMAIL_TYPE)),
                email(extras.getString(Insert.SECONDARY_EMAIL), extras.get(Insert.SECONDARY_EMAIL_TYPE)),
                email(extras.getString(Insert.TERTIARY_EMAIL), extras.get(Insert.TERTIARY_EMAIL_TYPE)),
            ),
            addresses = listOfNotNull(
                extras.getString(Insert.POSTAL)?.takeIf { it.isNotBlank() }?.let {
                    EditField(newFieldKey(), value = it, type = (extras.get(Insert.POSTAL_TYPE) as? Int) ?: StructuredPostal.TYPE_HOME)
                }
            ),
        )
        IntentCompat.getParcelableArrayListExtra(intent, Insert.DATA, ContentValues::class.java)?.forEach { row ->
            contact = withDataRow(contact, row)
        }
    }
    return contact
}

/** Adds one data row (as passed in [Insert.DATA]) to the prefill. */
private fun withDataRow(contact: EditableContact, row: ContentValues): EditableContact {
    val data1 = row.getAsString(ContactsContract.Data.DATA1)?.trim().orEmpty()
    val type = row.getAsInteger(ContactsContract.Data.DATA2) ?: 0
    val label = row.getAsString(ContactsContract.Data.DATA3)
    fun field() = EditField(newFieldKey(), value = data1, type = type, label = label)
    return when (row.getAsString(ContactsContract.Data.MIMETYPE)) {
        StructuredName.CONTENT_ITEM_TYPE -> contact.copy(
            first = row.getAsString(StructuredName.GIVEN_NAME) ?: contact.first.ifEmpty { data1 },
            last = row.getAsString(StructuredName.FAMILY_NAME) ?: contact.last,
            middle = row.getAsString(StructuredName.MIDDLE_NAME) ?: contact.middle,
        )
        Phone.CONTENT_ITEM_TYPE -> if (data1.isEmpty()) contact else contact.copy(phones = contact.phones + field())
        Email.CONTENT_ITEM_TYPE -> if (data1.isEmpty()) contact else contact.copy(emails = contact.emails + field())
        StructuredPostal.CONTENT_ITEM_TYPE -> if (data1.isEmpty()) contact else contact.copy(addresses = contact.addresses + field())
        Event.CONTENT_ITEM_TYPE -> if (data1.isEmpty()) contact else contact.copy(events = contact.events + field())
        Website.CONTENT_ITEM_TYPE -> if (data1.isEmpty()) contact else contact.copy(websites = contact.websites + field())
        Organization.CONTENT_ITEM_TYPE -> contact.copy(company = data1.ifEmpty { contact.company }, title = row.getAsString(Organization.TITLE) ?: contact.title)
        Nickname.CONTENT_ITEM_TYPE -> contact.copy(nickname = data1)
        Note.CONTENT_ITEM_TYPE -> contact.copy(note = listOf(contact.note, data1).filter { it.isNotBlank() }.joinToString("\n"))
        else -> contact
    }
}

/** [prefill]'s details added to an existing contact being edited: new rows, and empty single fields filled. */
fun mergePrefill(existing: EditableContact, prefill: EditableContact): EditableContact = existing.copy(
    first = existing.first.ifEmpty { prefill.first },
    last = existing.last.ifEmpty { prefill.last },
    company = existing.company.ifEmpty { prefill.company },
    title = existing.title.ifEmpty { prefill.title },
    note = listOf(existing.note, prefill.note).filter { it.isNotBlank() }.joinToString("\n"),
    phones = existing.phones + prefill.phones,
    emails = existing.emails + prefill.emails,
    addresses = existing.addresses + prefill.addresses,
    events = existing.events + prefill.events,
    websites = existing.websites + prefill.websites,
)
