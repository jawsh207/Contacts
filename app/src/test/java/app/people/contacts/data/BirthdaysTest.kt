package app.people.contacts.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which birthdays Highlights shows, when, and the age turned. */
class BirthdaysTest {
    private val alex = testContact(1, "Alex")
    private val bea = testContact(2, "Bea")
    private val cam = testContact(3, "Cam")

    @Test fun `February 29 falls on February 28 in common years`() {
        assertEquals(LocalDate.of(2027, 2, 28), birthdayIn(2, 29, 2027))
        assertEquals(LocalDate.of(2028, 2, 29), birthdayIn(2, 29, 2028))
    }

    @Test fun `soonest first, with days away and age`() {
        val today = LocalDate.of(2026, 11, 15)
        val result = upcomingBirthdays(listOf(alex, bea), mapOf(1L to "1990-11-20", 2L to "--11-15"), today)
        assertEquals(listOf(bea, alex), result.map { it.contact })
        assertEquals(0, result[0].daysAway)
        assertNull(result[0].turningAge)
        assertEquals(5, result[1].daysAway)
        assertEquals(36, result[1].turningAge)
    }

    @Test fun `birthdays already passed this year look to next year`() {
        val today = LocalDate.of(2026, 12, 20)
        val result = upcomingBirthdays(listOf(alex), mapOf(1L to "2000-01-05"), today)
        assertEquals(LocalDate.of(2027, 1, 5), result.single().date)
        assertEquals(27, result.single().turningAge)
    }

    @Test fun `outside the window, unreadable, or missing is left out`() {
        val today = LocalDate.of(2026, 11, 15)
        val result = upcomingBirthdays(
            listOf(alex, bea, cam),
            mapOf(1L to "1990-03-01", 2L to "sometime"),
            today,
        )
        assertEquals(emptyList<UpcomingBirthday>(), result)
    }
}
