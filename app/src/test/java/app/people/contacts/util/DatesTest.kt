package app.people.contacts.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reading stored contact dates in the formats apps save them in. */
class DatesTest {
    @Test fun `full date`() = assertEquals(EventDate(1990, 3, 4), parseEventDate("1990-03-04"))

    @Test fun `full date with single digits and a time after it`() =
        assertEquals(EventDate(1990, 3, 4), parseEventDate("1990-3-4T00:00:00Z"))

    @Test fun `compact date`() = assertEquals(EventDate(1990, 3, 4), parseEventDate("19900304"))

    @Test fun `no year, with and without a dash`() {
        assertEquals(EventDate(null, 3, 4), parseEventDate("--03-04"))
        assertEquals(EventDate(null, 3, 4), parseEventDate("--0304"))
    }

    @Test fun `spaces around it are ignored`() = assertEquals(EventDate(null, 12, 25), parseEventDate("  --12-25 "))

    @Test fun `February 29 is a real date`() = assertEquals(EventDate(null, 2, 29), parseEventDate("--02-29"))

    @Test fun `impossible dates are rejected`() {
        assertNull(parseEventDate("1990-02-30"))
        assertNull(parseEventDate("1990-13-01"))
        assertNull(parseEventDate("--00-10"))
    }

    @Test fun `other formats are rejected`() {
        assertNull(parseEventDate("March 4"))
        assertNull(parseEventDate("04/03/1990"))
        assertNull(parseEventDate(""))
    }
}
