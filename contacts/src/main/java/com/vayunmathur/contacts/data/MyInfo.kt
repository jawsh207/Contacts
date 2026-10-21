package com.vayunmathur.contacts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.util.Base64
import com.vayunmathur.library.log.Log
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

/**
 * The user's own card ("Your info"): what the QR code and Share hand out. Kept in the app's
 * own storage; first filled from Android's "Me" profile when that has anything.
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
 * Android's "Me" profile in the contacts provider: the closest thing to the device owner's
 * card that a regular app may read (the system user photo itself is only readable by system
 * apps). Read once as the starting point for [MyInfo].
 */
object MeProfileSource {

    /** The profile's name, first number and email, company and photo; null if there is none. */
    fun read(context: Context): MyInfo? = try {
        val resolver = context.contentResolver
        var info = MyInfo()
        val dataUri = ContactsContract.Profile.CONTENT_URI.buildUpon()
            .appendEncodedPath(ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
            .build()
        resolver.query(
            dataUri,
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
