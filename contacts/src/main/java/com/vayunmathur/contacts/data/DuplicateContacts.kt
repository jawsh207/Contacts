package com.vayunmathur.contacts.data

/**
 * Contacts in one account that look like the same person: the same name, phone number or
 * email address. Found by [findDuplicateSets] and combined by [mergeDuplicateSet].
 */
data class DuplicateSet(val contacts: List<Contact>) {
    /** Stable for the same contacts in any order; how a dismissed suggestion is remembered. */
    val key: String = contacts.map { it.id }.sorted().joinToString("-")
}

/** Phone numbers match on their last digits, so "+1 555 123 4567" and "(555) 123-4567" agree. */
private const val PHONE_MATCH_DIGITS = 10

/** Shorter numbers (short codes, extensions) are too likely to collide to count as a match. */
private const val PHONE_MIN_DIGITS = 7

internal fun phoneMatchKey(number: String): String? {
    val digits = number.filter { it.isDigit() }
    if (digits.length < PHONE_MIN_DIGITS) return null
    return digits.takeLast(PHONE_MATCH_DIGITS)
}

private fun nameMatchKey(contact: Contact): String? =
    contact.name.value.trim().lowercase().replace(WHITESPACE, " ").ifEmpty { null }

private fun emailMatchKey(address: String): String? = address.trim().lowercase().ifEmpty { null }

private val WHITESPACE = Regex("\\s+")

/**
 * Groups [contacts] into sets that share a name, phone number or email, transitively (A and B
 * share a number, B and C a name: one set of three). Only contacts in the same account are
 * matched, so a merge never moves anything between accounts. Sets whose [DuplicateSet.key] is
 * in [dismissed] are left out. Largest sets first, then by name.
 */
fun findDuplicateSets(contacts: List<Contact>, dismissed: Set<String> = emptySet()): List<DuplicateSet> =
    contacts.groupBy { it.accountType.orEmpty() to it.accountName.orEmpty() }
        .values
        .flatMap { inAccount -> duplicateSetsInAccount(inAccount) }
        .filter { it.key !in dismissed }
        .sortedWith(compareByDescending<DuplicateSet> { it.contacts.size }.thenBy { it.contacts.first().name.value.lowercase() })

private fun duplicateSetsInAccount(contacts: List<Contact>): List<DuplicateSet> {
    if (contacts.size < 2) return emptyList()
    // Union-find over contact indices; every shared key joins its contacts.
    val parent = IntArray(contacts.size) { it }
    fun root(i: Int): Int {
        var r = i
        while (parent[r] != r) r = parent[r]
        var j = i
        while (parent[j] != r) { val next = parent[j]; parent[j] = r; j = next }
        return r
    }
    val firstWithKey = HashMap<String, Int>()
    contacts.forEachIndexed { index, contact ->
        val keys = buildList {
            nameMatchKey(contact)?.let { add("n:$it") }
            contact.details.phoneNumbers.forEach { p -> phoneMatchKey(p.number)?.let { add("p:$it") } }
            contact.details.emails.forEach { e -> emailMatchKey(e.address)?.let { add("e:$it") } }
        }
        keys.forEach { key ->
            val other = firstWithKey.putIfAbsent(key, index)
            if (other != null) parent[root(index)] = root(other)
        }
    }
    return contacts.indices.groupBy { root(it) }.values
        .filter { it.size > 1 }
        .map { indices -> DuplicateSet(indices.map { contacts[it] }) }
}

/** How much a contact holds, to decide which one the others are merged into. */
private fun Contact.richness(): Int {
    val d = details
    return d.phoneNumbers.count { it.number.isNotBlank() } +
        d.emails.count { it.address.isNotBlank() } +
        d.addresses.count { it.formattedAddress.isNotBlank() } +
        d.dates.size + d.groups.size +
        d.orgs.count { it.company.isNotBlank() } +
        d.notes.count { it.content.isNotBlank() } +
        d.nicknames.count { it.nickname.isNotBlank() }
}

/** The contact the rest of [set] merge into: a favorite, then one with a photo, then the fullest. */
fun primaryOf(set: DuplicateSet): Contact =
    set.contacts.sortedWith(
        compareByDescending<Contact> { it.isFavorite }
            .thenByDescending { it.photo != null }
            .thenByDescending { it.richness() }
            .thenBy { it.id }
    ).first()

/**
 * [primary] with everything the other contacts add: numbers, emails, addresses, dates, labels
 * and nicknames it doesn't already have; their company, photo and birthday if it has none;
 * their notes appended to its own. The result keeps [primary]'s id, so saving it updates that
 * contact in place (copied rows carry id 0 and are inserted). Deleting [others] completes the
 * merge.
 */
fun mergeDuplicateSet(primary: Contact, others: List<Contact>): Contact {
    val all = listOf(primary) + others
    val d = primary.details
    fun <T> union(pick: (ContactDetails) -> List<T>, key: (T) -> String?, reset: (T) -> T): List<T> {
        val seen = HashSet<String>()
        val out = ArrayList<T>()
        all.forEachIndexed { i, c ->
            pick(c.details).forEach { item ->
                val k = key(item) ?: return@forEach
                if (seen.add(k)) out += if (i == 0) item else reset(item)
            }
        }
        return out
    }
    val hasBirthday = d.dates.any { it.type == CDKEvent.TYPE_BIRTHDAY }
    val firstBirthday = all.firstNotNullOfOrNull { c -> c.details.dates.firstOrNull { it.type == CDKEvent.TYPE_BIRTHDAY } }
    val notes = all.flatMap { c -> c.details.notes.map { it.content.trim() } }.filter { it.isNotEmpty() }.distinct()
    val ownNote = d.notes.firstOrNull { it.id != 0L }
    val mergedDetails = d.copy(
        phoneNumbers = union({ it.phoneNumbers }, { p -> p.number.takeIf { it.isNotBlank() }?.let { phoneMatchKey(it) ?: it.trim() } }) { it.copy(id = 0) },
        emails = union({ it.emails }, { emailMatchKey(it.address) }) { it.copy(id = 0) },
        addresses = union({ it.addresses }, { a -> a.formattedAddress.trim().lowercase().replace(WHITESPACE, " ").ifEmpty { null } }) { it.copy(id = 0) },
        dates = union(
            { it.dates },
            // One birthday only: the primary's, or else the first one found.
            { e -> if (e.type == CDKEvent.TYPE_BIRTHDAY) "birthday".takeIf { hasBirthday || e == firstBirthday } else "${e.type}|${e.label}|${e.startDate}" },
        ) { it.copy(id = 0) },
        photos = d.photos.ifEmpty { others.firstNotNullOfOrNull { it.photo }?.let { listOf(it.copy(id = 0)) } ?: emptyList() },
        orgs = (d.orgs.firstOrNull { it.company.isNotBlank() }
            ?: others.firstNotNullOfOrNull { c -> c.details.orgs.firstOrNull { it.company.isNotBlank() } }?.copy(id = 0)
            )?.let { listOf(it) } ?: emptyList(),
        notes = if (notes.isEmpty()) emptyList() else listOf(Note(ownNote?.id ?: 0, notes.joinToString("\n\n"))),
        nicknames = union({ it.nicknames }, { n -> n.nickname.trim().lowercase().ifEmpty { null }?.let { "${n.type}|$it" } }) { it.copy(id = 0) },
        groups = union({ it.groups }, { it.groupId.toString() }) { it.copy(id = 0) },
    )
    return primary.copy(
        isFavorite = all.any { it.isFavorite },
        customRingtone = primary.customRingtone ?: others.firstNotNullOfOrNull { it.customRingtone },
        details = mergedDetails,
    )
}
