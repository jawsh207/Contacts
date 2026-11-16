package app.people.contacts.data

import app.people.contacts.util.foldForSearch
import app.people.contacts.util.formatNumber

/** A search hit, with the detail that matched (a number, email, company, note…) when it wasn't the name. */
data class SearchResult(val contact: ContactSummary, val matchedDetail: String?)

/**
 * The contacts matching what was typed, in list order. Names match first; a query that's mostly
 * digits searches phone numbers (ignoring spaces and dashes); anything else also searches
 * emails and then the other details (company, job title, nickname, notes, addresses). Accents
 * and case don't matter, so "jose" finds "José".
 */
fun searchContacts(contacts: List<ContactSummary>, rawQuery: String): List<SearchResult> {
    val query = foldForSearch(rawQuery.trim())
    if (query.isEmpty()) return emptyList()
    val digits = rawQuery.filter { it.isDigit() }
    // A query that's mostly digits is a phone number search.
    val numberSearch = digits.length >= 2 && digits.length * 2 >= rawQuery.count { !it.isWhitespace() }
    return contacts.mapNotNull { c ->
        when {
            foldForSearch(c.name).contains(query) || foldForSearch(c.nameAlternative).contains(query) ->
                SearchResult(c, null)
            numberSearch -> c.numbers.firstOrNull { it.filter(Char::isDigit).contains(digits) }
                ?.let { SearchResult(c, formatNumber(it)) }
            else -> (c.emails + c.details).firstOrNull { foldForSearch(it).contains(query) }
                ?.let { SearchResult(c, it.replace('\n', ' ')) }
        }
    }
}
