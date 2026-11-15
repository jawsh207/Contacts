package app.people.contacts.util

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/** Whether the app has been given [permission] (for example, access to contacts). */
fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** A phone number spaced and grouped the way the phone's region writes them, or as saved if it can't be. */
fun formatNumber(number: String): String {
    if (number.isBlank()) return number
    return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
}

/** Last 10 digits: matches one number however it's formatted ("+1 555…" and "(555) …"). */
fun numberKey(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

/** The name's first letter, capitalised, for avatars and letter groups; null if it has no letters. */
fun initialOf(name: String?): String? =
    name?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Lower case without accents, so "jose" finds "José". */
fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.getDefault())
