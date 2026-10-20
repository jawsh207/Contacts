package com.vayunmathur.contacts.ui

import com.vayunmathur.contacts.data.Contact
import com.vayunmathur.contacts.data.hasYear
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil

/** How far ahead the Highlights tab looks for birthdays. */
internal const val BIRTHDAY_WINDOW_DAYS = 30

/** A contact's next birthday, as shown on the Highlights tab. */
data class UpcomingBirthday(
    val contact: Contact,
    /** The date of the next occurrence, on or after today. */
    val date: LocalDate,
    /** 0 for today, 1 for tomorrow, and so on. */
    val daysAway: Int,
    /** The age they turn on [date], or null when the birthday was saved without a year. */
    val turningAge: Int?,
)

/**
 * Birthdays falling within [windowDays] of [today], soonest first.
 *
 * A Feb 29 birthday is celebrated on Feb 28 in years without a leap day, so it never drops off
 * the list for three years in four.
 */
internal fun upcomingBirthdays(
    contacts: List<Contact>,
    today: LocalDate,
    windowDays: Int = BIRTHDAY_WINDOW_DAYS,
): List<UpcomingBirthday> = contacts.mapNotNull { contact ->
    val birthday = contact.birthday?.startDate ?: return@mapNotNull null
    var next = occurrenceIn(birthday, today.year)
    if (next < today) next = occurrenceIn(birthday, today.year + 1)
    val daysAway = today.daysUntil(next)
    if (daysAway > windowDays) return@mapNotNull null
    val age = if (birthday.hasYear) next.year - birthday.year else null
    UpcomingBirthday(contact, next, daysAway, age?.takeIf { it > 0 })
}.sortedWith(compareBy({ it.daysAway }, { it.contact.name.value.lowercase() }))

private fun occurrenceIn(birthday: LocalDate, year: Int): LocalDate {
    val isLeapDay = birthday.month == Month.FEBRUARY && birthday.day == LEAP_DAY
    val day = if (isLeapDay && !isLeapYear(year)) LEAP_DAY - 1 else birthday.day
    return LocalDate(year, birthday.month, day)
}

private fun isLeapYear(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private const val LEAP_DAY = 29
