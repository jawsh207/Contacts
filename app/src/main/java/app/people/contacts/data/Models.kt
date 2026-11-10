package app.people.contacts.data

/** A contact as the list shows it. [id] is ContactsContract.Contacts._ID. */
data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    /** Name in "Last, First" order, for sorting by last name. */
    val nameAlternative: String,
    val photoUri: String?,
    val starred: Boolean,
    /** Phone numbers and email addresses, for search. */
    val numbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    /** The accounts it's stored in, as (type, name); both null for the phone. */
    val accounts: List<Pair<String?, String?>> = emptyList(),
)

/**
 * One labelled value: a number, an address, a date… with its type label ("Mobile", "Work") and
 * the provider's type code behind it.
 */
data class LabeledValue(val value: String, val label: String, val type: Int = 0)

/** Everything the contact page shows. */
data class ContactDetails(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val thumbnailUri: String?,
    val starred: Boolean,
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val addresses: List<LabeledValue> = emptyList(),
    /** Raw dates as stored ("1990-03-04", or "--03-04" without a year). */
    val events: List<LabeledValue> = emptyList(),
    val websites: List<String> = emptyList(),
    val relations: List<LabeledValue> = emptyList(),
    val nickname: String? = null,
    val company: String? = null,
    val jobTitle: String? = null,
    val notes: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
)

/** The name a contact is sorted and grouped by: "First Last", or "Last, First" by last name. */
fun ContactSummary.sortName(order: SortOrder): String = if (order == SortOrder.LastName) nameAlternative else name
