package com.vayunmathur.contacts.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds as Kinds
import android.util.Base64
import com.vayunmathur.library.log.Log
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

/**
 * The user's own card ("Your info"): what the QR code and Share hand out. Read from and written
 * to Android's "Me" profile (see [MeProfileSource]), so it is the same card other apps see.
 */
@Serializable
data class MyInfo(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val company: String = "",
    /** JPEG, Base64; small enough for the list and the page header. Never put in the QR code. */
    val photo: String? = null,
) {
    val isEmpty: Boolean get() = name.isBlank() && phone.isBlank() && email.isBlank() && company.isBlank()

    /**
     * A vCard 3.0 of the text fields, for the QR code and for sharing. The photo is left out:
     * it would make the QR code too dense to scan.
     */
    fun toVCard(): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        val trimmed = name.trim()
        val last = trimmed.substringAfterLast(' ', "")
        val first = if (last.isEmpty()) trimmed else trimmed.substringBeforeLast(' ')
        append("N:${escape(last)};${escape(first)};;;\r\n")
        append("FN:${escape(trimmed)}\r\n")
        if (phone.isNotBlank()) append("TEL;TYPE=CELL:${escape(phone.trim())}\r\n")
        if (email.isNotBlank()) append("EMAIL:${escape(email.trim())}\r\n")
        if (company.isNotBlank()) append("ORG:${escape(company.trim())}\r\n")
        append("END:VCARD\r\n")
    }

    private fun escape(value: String) =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}

/** Side of the square the "Your info" photo is stored at. */
private const val MY_PHOTO_SIZE = 512
private const val MY_PHOTO_QUALITY = 90

/** Center-crops [bitmap] to a [MY_PHOTO_SIZE] square and encodes it for [MyInfo.photo]. */
fun encodeMyPhoto(bitmap: Bitmap): String {
    val side = minOf(bitmap.width, bitmap.height)
    val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(square, MY_PHOTO_SIZE, MY_PHOTO_SIZE, true)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, MY_PHOTO_QUALITY, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/**
 * Android's "Me" profile in the contacts provider: the device owner's own card, readable and
 * writable with the contacts permissions (the system user photo itself is only readable by
 * system apps). Profile rows live in a separate database; the provider routes requests there
 * by URI or by the profile's raw contact id.
 */
object MeProfileSource {

    private val MANAGED_MIME_TYPES = listOf(
        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
    )

    /**
     * Replaces the profile's name, first number, first email, company and photo with [info],
     * creating the profile if there is none. Other profile data is left alone. False on failure.
     */
    fun write(context: Context, info: MyInfo): Boolean {
        val ops = ArrayList<ContentProviderOperation>()
        val rawId = profileRawContactId(context)
        if (rawId == null) {
            ops += ContentProviderOperation.newInsert(ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        } else {
            managedDataIds(context, rawId).forEach { id ->
                ops += ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id))
                    .build()
            }
        }
        fun insert(mimeType: String, vararg values: Pair<String, Any?>) {
            val op = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            if (rawId == null) op.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            else op.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
            op.withValue(ContactsContract.Data.MIMETYPE, mimeType)
            values.forEach { (key, value) -> op.withValue(key, value) }
            ops += op.build()
        }
        if (info.name.isNotBlank()) {
            insert(Kinds.StructuredName.CONTENT_ITEM_TYPE, Kinds.StructuredName.DISPLAY_NAME to info.name.trim())
        }
        if (info.phone.isNotBlank()) {
            insert(
                Kinds.Phone.CONTENT_ITEM_TYPE,
                Kinds.Phone.NUMBER to info.phone.trim(),
                Kinds.Phone.TYPE to Kinds.Phone.TYPE_MOBILE,
            )
        }
        if (info.email.isNotBlank()) {
            insert(
                Kinds.Email.CONTENT_ITEM_TYPE,
                Kinds.Email.ADDRESS to info.email.trim(),
                Kinds.Email.TYPE to Kinds.Email.TYPE_HOME,
            )
        }
        if (info.company.isNotBlank()) {
            insert(
                Kinds.Organization.CONTENT_ITEM_TYPE,
                Kinds.Organization.COMPANY to info.company.trim(),
                Kinds.Organization.TYPE to Kinds.Organization.TYPE_WORK,
            )
        }
        info.photo?.let { encoded ->
            insert(Kinds.Photo.CONTENT_ITEM_TYPE, Kinds.Photo.PHOTO to Base64.decode(encoded, Base64.DEFAULT))
        }
        return try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: android.content.OperationApplicationException) {
            Log.error("MyInfo", "Could not save the Me profile", e)
            false
        } catch (e: android.os.RemoteException) {
            Log.error("MyInfo", "Could not save the Me profile", e)
            false
        } catch (e: SecurityException) {
            Log.error("MyInfo", "No permission to save the Me profile", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.error("MyInfo", "Could not save the Me profile", e)
            false
        }
    }

    private fun profileRawContactId(context: Context): Long? =
        context.contentResolver.query(
            ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** Ids of the profile raw contact's rows that [write] replaces. */
    private fun managedDataIds(context: Context, rawId: Long): List<Long> {
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            profileDataUri(),
            arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.RAW_CONTACT_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getLong(2) == rawId && cursor.getString(1) in MANAGED_MIME_TYPES) ids += cursor.getLong(0)
            }
        }
        return ids
    }

    private fun profileDataUri() = ContactsContract.Profile.CONTENT_URI.buildUpon()
        .appendEncodedPath(ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
        .build()

    /** The profile's name, first number and email, company and photo; null if there is none. */
    fun read(context: Context): MyInfo? = try {
        val resolver = context.contentResolver
        var info = MyInfo()
        resolver.query(
            profileDataUri(),
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(1)?.trim().orEmpty()
                if (value.isEmpty()) continue
                info = when (cursor.getString(0)) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE ->
                        if (info.name.isEmpty()) info.copy(name = value) else info
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        if (info.phone.isEmpty()) info.copy(phone = value) else info
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                        if (info.email.isEmpty()) info.copy(email = value) else info
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE ->
                        if (info.company.isEmpty()) info.copy(company = value) else info
                    else -> info
                }
            }
        }
        val photo = readPhoto(context)
        info = info.copy(photo = photo)
        info.takeUnless { it.isEmpty && photo == null }
    } catch (e: SecurityException) {
        Log.error("MyInfo", "No access to the Me profile", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.error("MyInfo", "Could not read the Me profile", e)
        null
    }

    private fun readPhoto(context: Context): String? {
        val resolver = context.contentResolver
        val photoUri = resolver.query(
            ContactsContract.Profile.CONTENT_URI,
            arrayOf(ContactsContract.Profile.PHOTO_URI),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return null
        return try {
            resolver.openInputStream(android.net.Uri.parse(photoUri))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { encodeMyPhoto(it) }
            }
        } catch (e: java.io.IOException) {
            Log.error("MyInfo", "Could not read the Me profile photo", e)
            null
        }
    }
}
