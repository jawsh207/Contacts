package app.people.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** What search finds, and which detail it shows for the match. */
class SearchTest {
    private val jose = testContact(1, "José Díaz", numbers = listOf("+1 555-123-4567"), emails = listOf("jd@mail.com"), nameAlternative = "Díaz, José")
    private val kim = testContact(2, "Kim Lee", details = listOf("Acme Corp", "Plumber", "Met at the climbing gym"))
    private val all = listOf(jose, kim)

    private fun find(query: String) = searchContacts(all, query).map { it.contact.id to it.matchedDetail }

    @Test fun `names match without accents or case`() = assertEquals(listOf(1L to null), find("jose"))

    @Test fun `last name first matches too`() = assertEquals(listOf(1L to null), find("díaz, j"))

    @Test fun `numbers match ignoring formatting`() = assertEquals(listOf(1L to "+1 555-123-4567"), find("5551234"))

    @Test fun `emails match`() = assertEquals(listOf(1L to "jd@mail.com"), find("jd@"))

    @Test fun `company, job title and notes match`() {
        assertEquals(listOf(2L to "Acme Corp"), find("acme"))
        assertEquals(listOf(2L to "Plumber"), find("plumb"))
        assertEquals(listOf(2L to "Met at the climbing gym"), find("climbing"))
    }

    @Test fun `nothing typed finds nothing`() {
        assertEquals(emptyList<Pair<Long, String?>>(), find("   "))
        assertEquals(emptyList<Pair<Long, String?>>(), find("zzz"))
    }
}
