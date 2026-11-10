package app.people.contacts.data

import app.people.contacts.util.parseEventDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How far ahead Highlights looks for birthdays. */
const val BIRTHDAY_WINDOW_DAYS = 30

/** A birthday coming up: when, in how many days, and the age turned if the year is known. */
data class UpcomingBirthday(
    val contact: ContactSummary,
    val date: LocalDate,
    val daysAway: Int,
    val turningAge: Int?,
)

/** The day a birthday falls on in [year]; February 29 is kept on February 28 in common years. */
fun birthdayIn(month: Int, day: Int, year: Int): LocalDate =
    if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28)
    else LocalDate.of(year, month, day)

/**
 * Contacts whose birthday is today or within [windowDays], soonest first.
 * [birthdays] maps contact ids to their stored birthday date.
 */
fun upcomingBirthdays(
    contacts: List<ContactSummary>,
    birthdays: Map<Long, String>,
    today: LocalDate,
    windowDays: Int = BIRTHDAY_WINDOW_DAYS,
): List<UpcomingBirthday> = contacts.mapNotNull { contact ->
    val date = parseEventDate(birthdays[contact.id] ?: return@mapNotNull null) ?: return@mapNotNull null
    var next = birthdayIn(date.month, date.day, today.year)
    if (next.isBefore(today)) next = birthdayIn(date.month, date.day, today.year + 1)
    val days = ChronoUnit.DAYS.between(today, next).toInt()
    if (days > windowDays) return@mapNotNull null
    val age = date.year?.let { next.year - it }?.takeIf { it > 0 }
    UpcomingBirthday(contact, next, days, age)
}.sortedWith(compareBy({ it.daysAway }, { it.contact.name }))
