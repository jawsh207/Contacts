package app.people.contacts.data

/**
 * One editable row of a repeating field (a phone number, an email…). [dataId] is the provider's
 * row id, or 0 for a row added in the editor. [key] keeps the row's identity in the UI.
 */
data class EditField(
    val key: Long,
    val dataId: Long = 0,
    val value: String = "",
    val type: Int,
    /** The custom label, used when [type] is the kind's "custom" type. */
    val label: String? = null,
)

/** What to do with the contact's photo when saving. */
sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange
    /** A new photo, already scaled and encoded as JPEG. */
    class Set(val jpeg: ByteArray) : PhotoChange
}

/** Where a new contact is saved. A null name and type is the phone itself. */
data class ContactAccount(val name: String?, val type: String?, val label: String)

/**
 * A contact as the editor works on it. Single fields keep their row id ([nameId], …) so they're
 * updated in place; 0 means there's no row yet. New rows go to [rawContactId], or to a new raw
 * contact in [account] when creating a contact.
 */
data class EditableContact(
    val contactId: Long? = null,
    val rawContactId: Long? = null,
    val account: ContactAccount? = null,
    val photoUri: String? = null,
    val nameId: Long = 0,
    val prefix: String = "",
    val first: String = "",
    val middle: String = "",
    val last: String = "",
    val suffix: String = "",
    val nicknameId: Long = 0,
    val nickname: String = "",
    val organizationId: Long = 0,
    val company: String = "",
    val title: String = "",
    val noteId: Long = 0,
    val note: String = "",
    val phones: List<EditField> = emptyList(),
    val emails: List<EditField> = emptyList(),
    val addresses: List<EditField> = emptyList(),
    val events: List<EditField> = emptyList(),
    val websites: List<EditField> = emptyList(),
) {
    /** Worth saving: has a name, company, number or email. */
    val hasContent: Boolean
        get() = listOf(prefix, first, middle, last, suffix, nickname, company).any { it.isNotBlank() } ||
            phones.any { it.value.isNotBlank() } || emails.any { it.value.isNotBlank() }
}
