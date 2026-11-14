package app.people.contacts.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.DisplayNameSources
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import app.people.contacts.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * One thing to fix. [name] is what the contact shows as; for a number, [dataId] is its row,
 * [value] the number as saved and [fixed] what it becomes (with a country code).
 */
data class FixItem(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val dataId: Long = 0,
    val value: String = "",
    val fixed: String = "",
)

/** What "Fix contacts" found, by kind. */
data class Fixes(
    /** Contacts with no name, showing a number or email instead. */
    val unnamed: List<FixItem> = emptyList(),
    /** Contacts with nothing in them at all. */
    val empty: List<FixItem> = emptyList(),
    /** A number saved twice on the same contact; [FixItem.dataId] is the extra copy. */
    val repeatedNumbers: List<FixItem> = emptyList(),
    /** Numbers without a country code, which other apps and other countries may not match. */
    val countryCodes: List<FixItem> = emptyList(),
) {
    val count get() = unnamed.size + empty.size + repeatedNumbers.size + countryCodes.size
}

/** Looks for things to fix among [shown] (the contacts the list shows) and empty contacts. */
suspend fun findFixes(context: Context, shown: List<ContactSummary>): Fixes = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val byId = shown.associateBy { it.id }
    val unnamed = ArrayList<FixItem>()
    val empty = ArrayList<FixItem>()
    runCatching {
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.DISPLAY_NAME_SOURCE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2)
                val source = c.getInt(3)
                val item = FixItem(id, c.getString(1).orEmpty(), name.orEmpty())
                when {
                    name.isNullOrBlank() || source == DisplayNameSources.UNDEFINED -> empty += item
                    (source == DisplayNameSources.PHONE || source == DisplayNameSources.EMAIL) && id in byId -> unnamed += item
                }
            }
        }
    }

    val country = countryIso(context)
    val repeated = ArrayList<FixItem>()
    val countryCodes = ArrayList<FixItem>()
    val seen = HashSet<Pair<Long, String>>()
    runCatching {
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.RAW_CONTACT_ID, Data.CONTACT_ID, Phone.NUMBER),
            "${Data.MIMETYPE} = ?", arrayOf(Phone.CONTENT_ITEM_TYPE),
            // The default copy of a number is the one kept.
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID}",
        )?.use { c ->
            while (c.moveToNext()) {
                val contact = byId[c.getLong(2)] ?: continue
                val number = c.getString(3)?.trim().orEmpty()
                val key = numberKey(number)
                if (key.isEmpty()) continue
                val item = FixItem(contact.id, contact.lookupKey, contact.name, c.getLong(0), number)
                // Twice in the same saved contact: the later copy can go. (Copies in different
                // accounts of a linked contact are each account's own, so they stay.)
                if (!seen.add(c.getLong(1) to key)) {
                    repeated += item
                    continue
                }
                withCountryCode(number, country)?.let { countryCodes += item.copy(fixed = it) }
            }
        }
    }
    Fixes(unnamed, empty, repeated, countryCodes)
}

/**
 * The number with this phone's country code added, formatted, or null if it has one already,
 * is too short to be a full number (a short code), or isn't a valid number for the country.
 */
private fun withCountryCode(number: String, country: String?): String? {
    if (country == null || number.startsWith("+") || number.startsWith("00")) return null
    if (number.count { it.isDigit() } < 7 || number.any { it.isLetter() }) return null
    val e164 = PhoneNumberUtils.formatNumberToE164(number, country) ?: return null
    return PhoneNumberUtils.formatNumber(e164, country) ?: e164
}

/** The phone's country, from the SIM, then the network, then the phone's language settings. */
private fun countryIso(context: Context): String? {
    val telephony = context.getSystemService(TelephonyManager::class.java)
    return listOfNotNull(telephony?.simCountryIso, telephony?.networkCountryIso, Locale.getDefault().country)
        .firstOrNull { it.length == 2 }?.uppercase(Locale.ROOT)
}

/** Deletes extra copies of numbers. Returns how many went. */
suspend fun removeNumbers(context: Context, items: List<FixItem>): Int = withContext(Dispatchers.IO) {
    items.count { item ->
        runCatching { context.contentResolver.delete(ContentUris.withAppendedId(Data.CONTENT_URI, item.dataId), null, null) > 0 }
            .getOrDefault(false)
    }
}

/** Saves numbers with their country code. Returns how many changed. */
suspend fun addCountryCodes(context: Context, items: List<FixItem>): Int = withContext(Dispatchers.IO) {
    items.count { item ->
        runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(Data.CONTENT_URI, item.dataId),
                ContentValues().apply { put(Phone.NUMBER, item.fixed) },
                null, null,
            ) > 0
        }.getOrDefault(false)
    }
}
