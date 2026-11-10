package app.people.contacts.calendar

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import app.people.contacts.R
import app.people.contacts.data.ContactsRepository
import app.people.contacts.data.birthdayIn
import app.people.contacts.util.hasPermission
import app.people.contacts.util.parseEventDate
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "Contact Birthdays" calendar: every contact's birthday as an all-day event. With the year
 * known, each year gets its own event titled with the age turned; without it, one yearly event.
 * The account and calendar names match the old Contacts app's, so its calendar is reused.
 */
object BirthdayCalendar {
    private const val ACCOUNT_NAME = "Contacts"
    private const val CALENDAR_NAME = "Contact Birthdays"
    private const val COLOR = 0xFF4285F4.toInt()
    private const val YEARS_BEHIND = 1
    private const val YEARS_AHEAD = 5

    fun canWrite(context: Context) =
        context.hasPermission(Manifest.permission.READ_CALENDAR) && context.hasPermission(Manifest.permission.WRITE_CALENDAR)

    private fun accountType(context: Context) = context.getString(R.string.account_type)

    /** Calendar URIs written as the calendar's own sync adapter, which owns its events. */
    private fun Uri.asOwner(context: Context): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType(context))
        .build()

    private fun calendarIds(context: Context): List<Long> =
        context.contentResolver.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT_NAME, accountType(context)), null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()

    private fun calendarId(context: Context): Long? {
        val manager = AccountManager.get(context)
        val account = Account(ACCOUNT_NAME, accountType(context))
        if (manager.getAccountsByType(account.type).none { it.name == ACCOUNT_NAME }) {
            manager.addAccountExplicitly(account, null, null)
        }
        calendarIds(context).firstOrNull()?.let { id ->
            // Keep the name current (older versions used another).
            context.contentResolver.update(
                android.content.ContentUris.withAppendedId(Calendars.CONTENT_URI, id).asOwner(context),
                ContentValues().apply {
                    put(Calendars.NAME, CALENDAR_NAME)
                    put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
                },
                null, null,
            )
            return id
        }
        val uri = context.contentResolver.insert(
            Calendars.CONTENT_URI.asOwner(context),
            ContentValues().apply {
                put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                put(Calendars.ACCOUNT_TYPE, accountType(context))
                put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
                put(Calendars.NAME, CALENDAR_NAME)
                put(Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
                put(Calendars.CALENDAR_COLOR, COLOR)
                put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)
                put(Calendars.VISIBLE, 1)
                put(Calendars.SYNC_EVENTS, 1)
                put(Calendars.CALENDAR_TIME_ZONE, "UTC")
            },
        )
        return uri?.lastPathSegment?.toLongOrNull()
    }

    /** Rewrites the calendar from the contacts' birthdays as they are now. */
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext
        val calendar = runCatching { calendarId(context) }.getOrNull() ?: return@withContext
        val repository = ContactsRepository(context)
        val names = repository.loadAll().associate { it.id to it.name }
        val birthdays = repository.loadBirthdays()
        val today = LocalDate.now()
        val ops = arrayListOf(
            ContentProviderOperation.newDelete(Events.CONTENT_URI.asOwner(context))
                .withSelection("${Events.CALENDAR_ID} = ?", arrayOf(calendar.toString()))
                .build()
        )
        birthdays.forEach { (contactId, raw) ->
            val name = names[contactId] ?: return@forEach
            val date = parseEventDate(raw) ?: return@forEach
            val base = "🎂 " + context.getString(R.string.birthday_title, name)
            if (date.year == null) {
                ops += event(context, calendar, contactId, base, birthdayIn(date.month, date.day, today.year), recurring = true)
            } else {
                for (year in today.year - YEARS_BEHIND..today.year + YEARS_AHEAD) {
                    val age = year - date.year
                    if (age < 0) continue
                    val title = if (age > 0) base + " · " + context.getString(R.string.turns_age, age) else base
                    ops += event(context, calendar, contactId, title, birthdayIn(date.month, date.day, year), recurring = false)
                }
            }
        }
        runCatching { context.contentResolver.applyBatch(CalendarContract.AUTHORITY, ops) }
    }

    private fun event(context: Context, calendar: Long, contactId: Long, title: String, day: LocalDate, recurring: Boolean): ContentProviderOperation {
        // All-day events are stored at midnight UTC.
        val start = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val op = ContentProviderOperation.newInsert(Events.CONTENT_URI.asOwner(context))
            .withValue(Events.CALENDAR_ID, calendar)
            .withValue(Events.TITLE, title)
            .withValue(Events.DTSTART, start)
            .withValue(Events.ALL_DAY, 1)
            .withValue(Events.EVENT_TIMEZONE, "UTC")
            .withValue(Events.SYNC_DATA1, contactId.toString())
        return if (recurring) {
            op.withValue(Events.DURATION, "P1D").withValue(Events.RRULE, "FREQ=YEARLY").build()
        } else {
            op.withValue(Events.DTEND, start + 24 * 60 * 60 * 1000L).build()
        }
    }

    /** Removes the calendar and its events. */
    suspend fun remove(context: Context) = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext
        calendarIds(context).forEach { id ->
            runCatching {
                context.contentResolver.delete(android.content.ContentUris.withAppendedId(Calendars.CONTENT_URI, id).asOwner(context), null, null)
            }
        }
    }
}
