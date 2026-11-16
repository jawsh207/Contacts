package app.people.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which contacts Merge duplicates suggests as the same person. */
class DuplicatesTest {
    private fun ids(sets: List<DuplicateSet>) = sets.map { set -> set.contacts.map { it.id }.toSet() }

    @Test fun `same name ignoring case, accents and spaces`() {
        val sets = findDuplicates(listOf(testContact(1, "José  Díaz"), testContact(2, "jose diaz"), testContact(3, "Other")), emptySet())
        assertEquals(listOf(setOf(1L, 2L)), ids(sets))
    }

    @Test fun `same number written differently`() {
        val sets = findDuplicates(
            listOf(testContact(1, "Mum", numbers = listOf("+1 (555) 123-4567")), testContact(2, "Mother", numbers = listOf("555-123-4567"))),
            emptySet(),
        )
        assertEquals(listOf(setOf(1L, 2L)), ids(sets))
    }

    @Test fun `same email ignoring case`() {
        val sets = findDuplicates(
            listOf(testContact(1, "A", emails = listOf("Sam@Example.com")), testContact(2, "B", emails = listOf("sam@example.com "))),
            emptySet(),
        )
        assertEquals(listOf(setOf(1L, 2L)), ids(sets))
    }

    @Test fun `short numbers like 911 don't make duplicates`() {
        val sets = findDuplicates(listOf(testContact(1, "A", numbers = listOf("911")), testContact(2, "B", numbers = listOf("911"))), emptySet())
        assertEquals(emptyList<Set<Long>>(), ids(sets))
    }

    @Test fun `linked through each other makes one set, biggest first`() {
        val sets = findDuplicates(
            listOf(
                testContact(1, "Sam", numbers = listOf("5551234567")),
                testContact(2, "Samuel", numbers = listOf("5551234567")),
                testContact(3, "samuel"),
                testContact(4, "Pat"),
                testContact(5, "pat"),
            ),
            emptySet(),
        )
        assertEquals(listOf(setOf(1L, 2L, 3L), setOf(4L, 5L)), ids(sets))
    }

    @Test fun `dismissed sets aren't suggested again`() {
        val contacts = listOf(testContact(7, "Sam"), testContact(3, "Sam"))
        assertEquals("3-7", findDuplicates(contacts, emptySet()).single().key)
        assertEquals(emptyList<DuplicateSet>(), findDuplicates(contacts, setOf("3-7")))
    }
}
