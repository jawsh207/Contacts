package app.people.contacts.data

/** Builds a contact for tests with only the parts a test cares about. */
fun testContact(
    id: Long,
    name: String,
    numbers: List<String> = emptyList(),
    emails: List<String> = emptyList(),
    details: List<String> = emptyList(),
    nameAlternative: String = name,
) = ContactSummary(
    id = id, lookupKey = "key$id", name = name, nameAlternative = nameAlternative, photoUri = null,
    starred = false, numbers = numbers, emails = emails, details = details,
)
