package app.people.contacts.util

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun formatNumber(number: String): String {
    if (number.isBlank()) return number
    return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
}

/** Last 10 digits: matches one number however it's formatted ("+1 555…" and "(555) …"). */
fun numberKey(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

fun initialOf(name: String?): String? =
    name?.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Lower case without accents, so "jose" finds "José". */
fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.getDefault())
