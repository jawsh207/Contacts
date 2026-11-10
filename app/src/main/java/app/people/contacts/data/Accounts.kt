package app.people.contacts.data

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import app.people.contacts.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Account type of on-device accounts the user makes ("Family", "Work"…). They aren't system
 * accounts: just a name the contacts are stored under. This is the type the old Contacts app
 * used, kept so the accounts made in it carry over.
 */
const val ON_DEVICE_ACCOUNT_TYPE = "com.vayunmathur.contacts.local"

/** Where contacts are stored. A null name and type is the phone itself. */
data class ContactAccount(val name: String?, val type: String?, val label: String) {
    /** The phone itself, or an on-device account made in this app. */
    val onDevice: Boolean get() = type.isNullOrEmpty() || type == ON_DEVICE_ACCOUNT_TYPE

    /** An on-device account the user made, which they can rename or delete. */
    val userMade: Boolean get() = type == ON_DEVICE_ACCOUNT_TYPE
}

/** How accounts are written in the settings file, the same way the old app wrote them. */
object AccountKeys {
    private fun encode(raw: String) = raw.replace("%", "%25").replace("|", "%7C").replace(",", "%2C")
    private fun decode(text: String) =
        text.replace("%7C", "|", ignoreCase = true).replace("%2C", ",", ignoreCase = true).replace("%25", "%")

    /** Key of a hidden account: "type|name". */
    fun hiddenKey(type: String?, name: String?) = "${encode(type.orEmpty())}|${encode(name.orEmpty())}"

    /** Whether an account is hidden, also matching the older forms the old app wrote. */
    fun isHidden(type: String?, name: String?, hidden: Set<String>): Boolean =
        hiddenKey(type, name) in hidden || "${type.orEmpty()}|${name.orEmpty()}" in hidden ||
            (!name.isNullOrEmpty() && name in hidden)

    /** Entry for a saved on-device account: "name|type". */
    fun savedEntry(name: String) = "${encode(name)}|${encode(ON_DEVICE_ACCOUNT_TYPE)}"

    /** The on-device account names in the saved set. */
    fun savedNames(entries: Set<String>): List<String> = entries.mapNotNull { entry ->
        if (entry.count { it == '|' } != 1) return@mapNotNull null
        val name = decode(entry.substringBefore('|'))
        val type = decode(entry.substringAfter('|'))
        name.takeIf { it.isNotEmpty() && type == ON_DEVICE_ACCOUNT_TYPE }
    }
}

/**
 * Every account contacts can be in: the phone, on-device accounts (with or without contacts),
 * accounts that hold contacts now, and accounts that sync contacts. Phone first, then by name.
 */
suspend fun allAccounts(context: Context, savedOnDevice: List<String>): List<ContactAccount> = withContext(Dispatchers.IO) {
    val phone = ContactAccount(null, null, context.getString(R.string.account_phone))
    val found = LinkedHashMap<Pair<String, String>, ContactAccount>()
    fun add(name: String?, type: String?) {
        if (name.isNullOrEmpty() || type.isNullOrEmpty()) return
        found.putIfAbsent(type to name, ContactAccount(name, type, name))
    }
    savedOnDevice.forEach { add(it, ON_DEVICE_ACCOUNT_TYPE) }
    runCatching {
        context.contentResolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.DELETED} = 0", null, null,
        )?.use { c -> while (c.moveToNext()) add(c.getString(0), c.getString(1)) }
    }
    syncingAccounts(context).forEach { add(it.first, it.second) }
    listOf(phone) + found.values.sortedWith(compareBy({ !it.userMade }, { it.label.lowercase() }))
}

/** Accounts whose contacts sync with a server and accept changes from the phone, as (name, type). */
private fun syncingAccounts(context: Context): List<Pair<String, String>> {
    val types = ContentResolver.getSyncAdapterTypes()
        .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
        .map { it.accountType }.toSet()
    return runCatching { AccountManager.get(context).accounts.toList() }.getOrDefault(emptyList())
        .filter { it.type in types }.map { it.name to it.type }
}

/** Accounts a new contact can be saved to: the phone, on-device accounts and syncing accounts. */
suspend fun writableAccounts(context: Context, savedOnDevice: List<String>): List<ContactAccount> {
    val syncing = syncingAccounts(context).toSet()
    return allAccounts(context, savedOnDevice).filter { it.onDevice || (it.name to it.type) in syncing }
}

private fun accountSelection(account: ContactAccount): Pair<String, Array<String>> =
    "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?" to arrayOf(account.type.orEmpty(), account.name.orEmpty())

/** Moves every contact in an on-device account to its new name. */
suspend fun renameOnDeviceAccount(context: Context, account: ContactAccount, newName: String): Boolean = withContext(Dispatchers.IO) {
    val (selection, args) = accountSelection(account)
    runCatching {
        context.contentResolver.applyBatch(
            ContactsContract.AUTHORITY,
            arrayListOf(
                ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI)
                    .withSelection(selection, args)
                    .withValue(RawContacts.ACCOUNT_NAME, newName)
                    .build()
            ),
        )
        true
    }.getOrDefault(false)
}

/** Deletes an on-device account's contacts. */
suspend fun deleteOnDeviceAccountContacts(context: Context, account: ContactAccount): Boolean = withContext(Dispatchers.IO) {
    val (selection, args) = accountSelection(account)
    runCatching { context.contentResolver.delete(RawContacts.CONTENT_URI, selection, args); true }.getOrDefault(false)
}
