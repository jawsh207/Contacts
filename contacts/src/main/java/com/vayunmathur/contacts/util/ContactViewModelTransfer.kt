package com.vayunmathur.contacts.util

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.contacts.data.ContactDetails
import com.vayunmathur.library.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TransferResult(val moved: Int, val failed: Int, val groupsDropped: Boolean)

/**
 * MOVEs [contactIds] to ([destName], [destType]): copies each contact into the
 * destination with fresh data-row ids, then deletes the source only when the
 * insert succeeded.
 * [onResult] is invoked on the main thread.
 */
fun ContactViewModel.transferContacts(
    contactIds: List<Long>,
    destName: String,
    destType: String,
    onResult: ((moved: Int, failed: Int, groupsDropped: Boolean) -> Unit)? = null,
) {
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { transferContactsInternal(contactIds, destName, destType) }
        loadAccounts()
        loadContacts()
        withContext(Dispatchers.Main) { onResult?.invoke(result.moved, result.failed, result.groupsDropped) }
    }
}

private suspend fun ContactViewModel.transferContactsInternal(
    contactIds: List<Long>,
    destName: String,
    destType: String,
): TransferResult {
    var moved = 0
    var failed = 0
    var groupsDropped = false
    for (id in contactIds) {
        val outcome = moveContactById(id, destName, destType)
        if (outcome == null) continue
        if (outcome.moved) {
            moved++
            if (outcome.groupsDropped) groupsDropped = true
        } else {
            failed++
        }
    }
    return TransferResult(moved, failed, groupsDropped)
}

/** Moves one contact by id. Null when the destination equals the source (skipped, not failed). */
private suspend fun ContactViewModel.moveContactById(
    id: Long,
    destName: String,
    destType: String,
): MoveOutcome? {
    val contact = findContact(id) ?: return MoveOutcome(false, false)
    if (isSameAccount(contact, destName, destType)) return null
    return moveOneContact(contact, destName, destType)
}

private fun isSameAccount(contact: Contact, destName: String, destType: String): Boolean =
    (contact.accountType.orEmpty() == destType) && (contact.accountName.orEmpty() == destName)

private data class MoveOutcome(val moved: Boolean, val groupsDropped: Boolean)

// Broad catch is deliberate: transfer crosses provider writes and
// must report failure via the outcome rather than crashing the caller.
@Suppress("TooGenericExceptionCaught")
private suspend fun ContactViewModel.moveOneContact(
    contact: Contact,
    destName: String,
    destType: String,
): MoveOutcome {
    try {
        val inserted = copyProviderToProvider(contact, destName, destType)
        if (!inserted.ok) return MoveOutcome(false, false)
        deleteTransferSource(contact)
        return MoveOutcome(true, inserted.groupsDropped)
    } catch (e: Exception) {
        Log.error("ContactViewModel", "Error transferring contact ${contact.id}", e)
        return MoveOutcome(false, false)
    }
}

private data class InsertOutcome(val ok: Boolean, val groupsDropped: Boolean)

private suspend fun ContactViewModel.copyProviderToProvider(
    contact: Contact,
    destName: String,
    destType: String,
): InsertOutcome {
    val app = getApplication<Application>()
    val withGroups = contact.zeroedCopy(destName, destType)
    if (withGroups.save(app, withGroups.details, ContactDetails.empty())) {
        return InsertOutcome(true, false)
    }
    // Fallback: group row ids may not exist for the destination; retry without them.
    if (withGroups.details.groups.isEmpty()) return InsertOutcome(false, false)
    val withoutGroups = withGroups.copy(details = withGroups.details.copy(groups = emptyList()))
    val ok = withoutGroups.save(app, withoutGroups.details, ContactDetails.empty())
    return InsertOutcome(ok, ok)
}

private suspend fun ContactViewModel.deleteTransferSource(contact: Contact) {
    val app = getApplication<Application>()
    // Fresh-read details so the delete path sees current rows.
    val fresh = runCatching { Contact.getContact(app, contact.id) }.getOrNull() ?: contact
    Contact.delete(app, fresh)
    syncFromSystem()
}

/** Copy pointed at ([destName], [destType]) with id 0 and all data-row ids zeroed for insert. */
private fun Contact.zeroedCopy(destName: String, destType: String): Contact {
    val accountType = destType.ifEmpty { null }
    val accountName = if (accountType == null) null else destName.ifEmpty { null }
    return copy(
        id = 0,
        accountType = accountType,
        accountName = accountName,
        details = details.zeroed(),
    )
}

private fun ContactDetails.zeroed(): ContactDetails = copy(
    phoneNumbers = phoneNumbers.map { it.copy(id = 0) },
    emails = emails.map { it.copy(id = 0) },
    addresses = addresses.map { it.copy(id = 0) },
    dates = dates.map { it.copy(id = 0) },
    photos = photos.map { it.copy(id = 0) },
    names = names.map { it.copy(id = 0) },
    orgs = orgs.map { it.copy(id = 0) },
    notes = notes.map { it.copy(id = 0) },
    nicknames = nicknames.map { it.copy(id = 0) },
    groups = groups.map { it.copy(id = 0) },
)
