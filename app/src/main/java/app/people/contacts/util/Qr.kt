package app.people.contacts.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.people.contacts.data.ContactDetails
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR code for [text], black on white, one pixel per module (scale it up when drawing). */
fun qrCode(text: String): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()

private fun escape(value: String) =
    value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

/**
 * A small vCard of the contact for a QR code: name, work, numbers, emails and websites. Photos
 * and notes are left out so the code stays small enough to scan.
 */
fun qrVCard(contact: ContactDetails): String = buildString {
    appendLine("BEGIN:VCARD")
    appendLine("VERSION:3.0")
    appendLine("FN:${escape(contact.name)}")
    appendLine("N:;${escape(contact.name)};;;")
    contact.company?.let { appendLine("ORG:${escape(it)}") }
    contact.jobTitle?.let { appendLine("TITLE:${escape(it)}") }
    contact.phones.forEach { appendLine("TEL:${escape(it.value)}") }
    contact.emails.forEach { appendLine("EMAIL:${escape(it.value)}") }
    contact.websites.forEach { appendLine("URL:${escape(it)}") }
    append("END:VCARD")
}
