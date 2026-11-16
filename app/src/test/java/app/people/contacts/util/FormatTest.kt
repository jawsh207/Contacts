package app.people.contacts.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Matching numbers however they're written, accent-blind search text, and initials. */
class FormatTest {
    @Test fun `numbers match however they are formatted`() {
        assertEquals(numberKey("+1 (555) 123-4567"), numberKey("555.123.4567"))
        assertEquals("5551234567", numberKey("+1 555 123 4567"))
    }

    @Test fun `short numbers keep all their digits`() = assertEquals("911", numberKey("911"))

    @Test fun `search text ignores accents and case`() {
        assertEquals("jose", foldForSearch("José"))
        assertEquals("francoise muller", foldForSearch("Françoise Müller"))
    }

    @Test fun `initial is the first letter, capitalised`() {
        assertEquals("A", initialOf("alex"))
        assertEquals("É", initialOf("élodie"))
        assertEquals("J", initialOf("  (Jo)"))
    }

    @Test fun `no letters means no initial`() {
        assertNull(initialOf("+1 555"))
        assertNull(initialOf(null))
    }
}
