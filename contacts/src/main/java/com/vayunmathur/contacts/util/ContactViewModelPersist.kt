package com.vayunmathur.contacts.util

import android.app.Application

/**
 * The contacts-provider save path, kept out of ContactViewModel so the class stays under the
 * function cap (see TooManyFunctions). Called as viewModel.*.
 */
internal suspend fun ContactViewModel.persistProviderContact(
    contact: com.vayunmathur.contacts.data.Contact
): Boolean {
    val app = getApplication<Application>()
    val contactId = contact.id
    val details = contact.details
    // Read the old rows straight from the provider rather than trusting the cached
    // list: the cache lags the provider across account syncs and external edits, and
    // updates built against stale data-row ids match zero rows without error, so the
    // batch reports success while nothing changes.
    val oldDetails = contactId.takeIf { it != 0L }?.let {
        runCatching { com.vayunmathur.contacts.data.getDetails(app, it) }.getOrNull()
            ?: com.vayunmathur.contacts.data.ContactDetails.empty()
    } ?: contacts.value.find { it.id == contactId }?.details
        ?: allContactsState.value.find { it.id == contactId }?.details
        ?: com.vayunmathur.contacts.data.ContactDetails.empty()
    val ok = contact.save(app, details, oldDetails)
    if (ok) {
        // The provider observer re-syncs on its own debounce, but an edit must be
        // visible immediately - refresh now so the details page and list update
        // without waiting for the observer round-trip.
        syncFromSystem()
    }
    return ok
}
