package app.people.contacts.data

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading .vcf files written by other apps (vCard 2.1, 3.0 and 4.0). */
class VCardReaderTest {
    private fun one(text: String) = VCardReader.parse(text).single().first

    @Test fun `a vCard 3 contact`() {
        val c = one(
            """
            BEGIN:VCARD
            VERSION:3.0
            N:Díaz;José;Luis;Dr.;Jr.
            FN:Dr. José Luis Díaz Jr.
            NICKNAME:Pepe,JD
            ORG:Acme;Sales
            TITLE:Manager
            TEL;TYPE=CELL:+1 555 123 4567
            TEL;TYPE=WORK,FAX:555 000 1111
            EMAIL;TYPE=WORK:jose@acme.com
            ADR;TYPE=HOME:;;1 Main St;Springfield;IL;62701;USA
            BDAY:1990-03-04
            URL:https://example.com
            NOTE:Line one\nLine two\, with a comma
            END:VCARD
            """.trimIndent()
        )
        assertEquals(listOf("José", "Luis", "Díaz", "Dr.", "Jr."), listOf(c.first, c.middle, c.last, c.prefix, c.suffix))
        assertEquals("Pepe", c.nickname)
        assertEquals("Acme", c.company)
        assertEquals("Manager", c.title)
        assertEquals(listOf("+1 555 123 4567" to Phone.TYPE_MOBILE, "555 000 1111" to Phone.TYPE_FAX_WORK), c.phones.map { it.value to it.type })
        assertEquals(listOf("jose@acme.com" to Email.TYPE_WORK), c.emails.map { it.value to it.type })
        assertEquals(listOf("1 Main St\nSpringfield IL 62701\nUSA" to StructuredPostal.TYPE_HOME), c.addresses.map { it.value to it.type })
        assertEquals(listOf("1990-03-04" to Event.TYPE_BIRTHDAY), c.events.map { it.value to it.type })
        assertEquals(listOf("https://example.com"), c.websites.map { it.value })
        assertEquals("Line one\nLine two, with a comma", c.note)
    }

    @Test fun `vCard 2_1 with bare types and quoted-printable`() {
        val c = one(
            "BEGIN:VCARD\r\nVERSION:2.1\r\nN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:M=C3=BCller;Fran=\r\n=C3=A7oise\r\nTEL;CELL;PREF:555-1234\r\nEND:VCARD\r\n"
        )
        assertEquals("Françoise" to "Müller", c.first to c.last)
        assertEquals(listOf("555-1234" to Phone.TYPE_MOBILE), c.phones.map { it.value to it.type })
    }

    @Test fun `folded lines are joined`() {
        val c = one("BEGIN:VCARD\nFN:Alex\nNOTE:This note goes on\n  over two lines\nEND:VCARD")
        assertEquals("This note goes on over two lines", c.note)
    }

    @Test fun `only a display name goes into the first name`() = assertEquals("Alex Kim", one("BEGIN:VCARD\nFN:Alex Kim\nEND:VCARD").first)

    @Test fun `group prefixes and tel uris are understood`() {
        val c = one("BEGIN:VCARD\nFN:A\nitem1.TEL;VALUE=uri;TYPE=home:tel:+15551234567\nEND:VCARD")
        assertEquals(listOf("+15551234567" to Phone.TYPE_HOME), c.phones.map { it.value to it.type })
    }

    @Test fun `birthdays without a year and in compact form`() {
        assertEquals("--03-04", one("BEGIN:VCARD\nFN:A\nBDAY:--0304\nEND:VCARD").events.single().value)
        assertEquals("1990-03-04", one("BEGIN:VCARD\nFN:A\nBDAY:19900304\nEND:VCARD").events.single().value)
    }

    @Test fun `several contacts in one file, unknown lines skipped`() {
        val all = VCardReader.parse("BEGIN:VCARD\nFN:One\nX-CUSTOM:whatever\nEND:VCARD\nBEGIN:VCARD\nFN:Two\nEND:VCARD\nnoise")
        assertEquals(listOf("One", "Two"), all.map { it.first.first })
    }
}
