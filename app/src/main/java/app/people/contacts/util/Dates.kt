package app.people.contacts.util

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A contact event's date as stored: with a year ("1990-03-04") or without ("--03-04"). */
data class EventDate(val year: Int?, val month: Int, val day: Int)

private val WITH_YEAR = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")
private val WITHOUT_YEAR = Regex("""^--(\d{1,2})-?(\d{1,2})""")
private val COMPACT = Regex("""^(\d{4})(\d{2})(\d{2})$""")

fun parseEventDate(raw: String): EventDate? {
    val text = raw.trim()
    val m = WITH_YEAR.find(text) ?: COMPACT.find(text)
    if (m != null) {
        val (y, mo, d) = m.destructured
        return EventDate(y.toInt(), mo.toInt(), d.toInt()).takeIf { it.isValid() }
    }
    WITHOUT_YEAR.find(text)?.let {
        val (mo, d) = it.destructured
        return EventDate(null, mo.toInt(), d.toInt()).takeIf { date -> date.isValid() }
    }
    return null
}

private fun EventDate.isValid(): Boolean =
    runCatching { MonthDay.of(month, day) }.isSuccess

/** "March 4, 1990", or "March 4" without a year, in the user's language. Falls back to [raw]. */
fun formatEventDate(raw: String, locale: Locale = Locale.getDefault()): String {
    val date = parseEventDate(raw) ?: return raw
    return if (date.year != null) {
        runCatching {
            LocalDate.of(date.year, date.month, date.day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
        }.getOrDefault(raw)
    } else {
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMd")
        MonthDay.of(date.month, date.day).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
