package app.people.contacts.data

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import app.people.contacts.R

/**
 * Accounts a new contact can be saved to: the phone itself first, then every account whose
 * contacts sync adapter accepts changes from the device.
 */
fun writableAccounts(context: Context): List<ContactAccount> {
    val phone = ContactAccount(null, null, context.getString(R.string.account_phone))
    val writableTypes = ContentResolver.getSyncAdapterTypes()
        .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
        .map { it.accountType }
        .toSet()
    val others = runCatching { AccountManager.get(context).accounts.toList() }.getOrDefault(emptyList())
        .filter { it.type in writableTypes }
        .map { ContactAccount(it.name, it.type, it.name) }
    return listOf(phone) + others
}
