package app.people.contacts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One vCard property: its name, parameters ("TYPE" → ["CELL", "PREF"]) and raw value. */
private class Property(val name: String, val params: Map<String, List<String>>, val value: String) {
    val types: Set<String> = buildSet {
        params["TYPE"]?.forEach { addAll(it.uppercase().split(',')) }
        // vCard 2.1 writes bare types: "TEL;CELL;PREF:…".
        params[""]?.forEach { add(it.uppercase()) }
    }
}

/** Reads .vcf files (vCard 2.1, 3.0 and 4.0) into contacts the editor can save. */
object VCardReader {

    /** Every contact in [text]. Unknown properties are skipped. */
    fun parse(text: String): List<Pair<EditableContact, ByteArray?>> {
        val out = ArrayList<Pair<EditableContact, ByteArray?>>()
        var card: MutableList<Property>? = null
        for (line in unfold(text)) {
            val property = parseLine(line) ?: continue
            when {
                property.name == "BEGIN" && property.value.equals("VCARD", true) -> card = ArrayList()
                property.name == "END" && property.value.equals("VCARD", true) -> {
                    card?.let { out += toContact(it) }
                    card = null
                }
                else -> card?.add(property)
            }
        }
        return out
    }

    /** Joins folded lines: a line starting with a space or tab continues the one before. */
    private fun unfold(text: String): List<String> {
        val lines = ArrayList<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            when {
                (line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty() ->
                    lines[lines.lastIndex] = lines.last() + line.substring(1)
                // vCard 2.1 quoted-printable soft line breaks end in "=".
                lines.isNotEmpty() && lines.last().endsWith("=") && lines.last().contains("QUOTED-PRINTABLE", true) ->
                    lines[lines.lastIndex] = lines.last().dropLast(1) + line
                line.isNotBlank() -> lines += line
            }
        }
        return lines
    }

    private fun parseLine(line: String): Property? {
        val colon = indexOfUnquoted(line, ':')
        if (colon < 0) return null
        val head = line.substring(0, colon).split(';')
        // Drop a group prefix ("item1.TEL").
        val name = head.first().substringAfterLast('.').uppercase()
        val params = HashMap<String, MutableList<String>>()
        head.drop(1).forEach { param ->
            val key = if ('=' in param) param.substringBefore('=').uppercase() else ""
            val value = if ('=' in param) param.substringAfter('=').trim('"') else param
            params.getOrPut(key) { ArrayList() }.add(value)
        }
        var value = line.substring(colon + 1)
        if (params["ENCODING"]?.any { it.equals("QUOTED-PRINTABLE", true) } == true) {
            val charset = params["CHARSET"]?.firstOrNull()?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
            value = decodeQuotedPrintable(value, charset)
        }
        return Property(name, params, value)
    }

    private fun indexOfUnquoted(text: String, char: Char): Int {
        var quoted = false
        text.forEachIndexed { i, c ->
            if (c == '"') quoted = !quoted
            if (c == char && !quoted) return i
        }
        return -1
    }

    private fun decodeQuotedPrintable(text: String, charset: Charset): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '=' && i + 2 < text.length) {
                val byte = text.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte != null) {
                    bytes.write(byte)
                    i += 3
                    continue
                }
            }
            bytes.write(c.code)
            i++
        }
        return String(bytes.toByteArray(), charset)
    }

    /** Splits a structured value on unescaped [separator] and unescapes each part. */
    private fun parts(value: String, separator: Char = ';'): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                current.append(
                    when (val next = value[i + 1]) {
                        'n', 'N' -> '\n'
                        else -> next
                    }
                )
                i += 2
                continue
            }
            if (c == separator) {
                out += current.toString(); current.clear()
            } else current.append(c)
            i++
        }
        out += current.toString()
        return out.map { it.trim() }
    }

    private fun text(value: String) = parts(value, '\u0000').first()

    private fun toContact(props: List<Property>): Pair<EditableContact, ByteArray?> {
        var contact = EditableContact()
        var photo: ByteArray? = null
        var formatted = ""
        val phones = ArrayList<EditField>()
        val emails = ArrayList<EditField>()
        val addresses = ArrayList<EditField>()
        val events = ArrayList<EditField>()
        val websites = ArrayList<EditField>()
        val notes = ArrayList<String>()
        props.forEach { p ->
            when (p.name) {
                "FN" -> formatted = text(p.value)
                "N" -> {
                    val n = parts(p.value) + List(5) { "" }
                    contact = contact.copy(last = n[0], first = n[1], middle = n[2], prefix = n[3], suffix = n[4])
                }
                "NICKNAME" -> contact = contact.copy(nickname = parts(p.value, ',').first())
                "ORG" -> contact = contact.copy(company = parts(p.value).first())
                "TITLE" -> contact = contact.copy(title = text(p.value))
                "NOTE" -> notes += text(p.value)
                "TEL" -> phones += EditField(newFieldKey(), value = text(p.value).removePrefix("tel:"), type = phoneType(p.types))
                "EMAIL" -> emails += EditField(newFieldKey(), value = text(p.value), type = emailType(p.types))
                "ADR" -> {
                    // PO box; extended; street; city; region; postcode; country.
                    val a = parts(p.value) + List(7) { "" }
                    val lines = listOf(listOf(a[0], a[1], a[2]), listOf(a[3], a[4], a[5]), listOf(a[6]))
                        .map { line -> line.filter { it.isNotBlank() }.joinToString(" ") }.filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) addresses += EditField(newFieldKey(), value = lines.joinToString("\n"), type = addressType(p.types))
                }
                "BDAY" -> dateValue(p.value)?.let { events += EditField(newFieldKey(), value = it, type = Event.TYPE_BIRTHDAY) }
                "ANNIVERSARY", "X-ANNIVERSARY" -> dateValue(p.value)?.let { events += EditField(newFieldKey(), value = it, type = Event.TYPE_ANNIVERSARY) }
                "URL" -> websites += EditField(newFieldKey(), value = text(p.value), type = Website.TYPE_OTHER)
                "PHOTO" -> photo = photo ?: photoBytes(p)
            }
        }
        if (listOf(contact.first, contact.last, contact.middle, contact.prefix, contact.suffix).all { it.isBlank() }) {
            contact = contact.copy(first = formatted)
        }
        return contact.copy(
            phones = phones, emails = emails, addresses = addresses, events = events, websites = websites,
            note = notes.joinToString("\n\n"),
        ) to photo
    }

    /** "1990-03-04", "19900304", "--0304" or "--03-04" → "1990-03-04" / "--03-04". */
    private fun dateValue(raw: String): String? {
        val v = raw.trim().substringBefore('T')
        Regex("""^(\d{4})-?(\d{2})-?(\d{2})$""").find(v)?.let { val (y, m, d) = it.destructured; return "$y-$m-$d" }
        Regex("""^--(\d{2})-?(\d{2})$""").find(v)?.let { val (m, d) = it.destructured; return "--$m-$d" }
        return null
    }

    private fun phoneType(types: Set<String>) = when {
        "CELL" in types || "MOBILE" in types -> Phone.TYPE_MOBILE
        "FAX" in types && "WORK" in types -> Phone.TYPE_FAX_WORK
        "FAX" in types -> Phone.TYPE_FAX_HOME
        "PAGER" in types -> Phone.TYPE_PAGER
        "WORK" in types -> Phone.TYPE_WORK
        "HOME" in types -> Phone.TYPE_HOME
        "MAIN" in types -> Phone.TYPE_MAIN
        else -> Phone.TYPE_OTHER
    }

    private fun emailType(types: Set<String>) = when {
        "WORK" in types -> Email.TYPE_WORK
        "HOME" in types -> Email.TYPE_HOME
        else -> Email.TYPE_OTHER
    }

    private fun addressType(types: Set<String>) = when {
        "WORK" in types -> StructuredPostal.TYPE_WORK
        "HOME" in types -> StructuredPostal.TYPE_HOME
        else -> StructuredPostal.TYPE_OTHER
    }

    /** An embedded photo, re-encoded as a JPEG no bigger than the provider keeps. */
    private fun photoBytes(p: Property): ByteArray? {
        val data = p.value.substringAfter("base64,", p.value)
        val bytes = runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > 720) Bitmap.createScaledBitmap(square, 720, 720, true) else square
        return ByteArrayOutputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() }
    }
}

/** Writes contacts out as one .vcf file, using the contacts provider's own vCard export. */
object VCardWriter {
    /** How many contacts to ask the provider for at once, keeping its URIs a sane length. */
    private const val BATCH = 50

    suspend fun export(context: Context, lookupKeys: List<String>, out: OutputStream): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            lookupKeys.chunked(BATCH).forEach { batch ->
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(batch.joinToString(":")))
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
            }
            out.flush()
            true
        }.getOrDefault(false)
    }
}
