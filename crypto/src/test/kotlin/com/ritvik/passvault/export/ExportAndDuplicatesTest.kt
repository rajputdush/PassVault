package com.ritvik.passvault.export

import com.ritvik.passvault.importer.ImportedField
import com.ritvik.passvault.importer.ImportedKind
import com.ritvik.passvault.importer.ImportedLogin
import com.ritvik.passvault.importer.PasswordImport
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportAndDuplicatesTest {
    private val login = ImportedLogin(
        "Amazon \"Prime\"", "https://amazon.com", "me@x.com", "pa\\ss,\nword", "line1\nline2",
        totp = "JBSWY3DPEHPK3PXP", appId = "com.amazon.mShop.android.shopping", folder = "Shopping", favorite = true,
        customFields = listOf(ImportedField("Question", "Blue", true)),
    )
    private val card = ImportedLogin(
        "Visa", "", "", "", "", kind = ImportedKind.CARD, cardholder = "R S", cardNumber = "4111111111111111",
        cardExpiry = "08/28", cardCvv = "123",
    )
    private val note = ImportedLogin("Wifi", "", "", "", "pw is abc", kind = ImportedKind.NOTE)

    @Test fun bitwardenJsonRoundTrips() {
        val json = PlainExport.toBitwardenJson(listOf(login, card, note))
        val back = PasswordImport.parse(json).logins
        assertEquals(3, back.size)
        val l = back.first { it.kind == ImportedKind.LOGIN }
        assertEquals(login.title, l.title)
        assertEquals(login.password, l.password)
        assertEquals(login.notes, l.notes)
        assertEquals("Shopping", l.folder)
        assertTrue(l.favorite)
        assertEquals(login.appId, l.appId)
        assertEquals(login.totp, l.totp)
        assertEquals("Blue", l.customFields.first().value)
        assertTrue(l.customFields.first().hidden)
        val c = back.first { it.kind == ImportedKind.CARD }
        assertEquals("4111111111111111", c.cardNumber)
        assertEquals("08/28", c.cardExpiry)
        assertEquals("123", c.cardCvv)
        assertEquals("R S", c.cardholder)
        assertEquals("pw is abc", back.first { it.kind == ImportedKind.NOTE }.notes)
    }

    @Test fun emptyVaultStillValid() {
        assertEquals(0, PasswordImport.parse(PlainExport.toBitwardenJson(emptyList())).logins.size)
    }

    @Test fun csvQuotesAndDefusesFormulas() {
        val csv = PlainExport.toCsv(listOf(login, card, ImportedLogin("=cmd()", "", "u", "p", "")))
        val rows = com.ritvik.passvault.importer.CsvParser.parse(csv)
        assertEquals(3, rows.size) // header + two logins, the card is left out
        assertEquals("pa\\ss,\nword", rows[1][3])
        assertEquals("'=cmd()", rows[2][0])
    }

    @Test fun readsOnePasswordExport() {
        val data = """{"accounts":[{"vaults":[{"attrs":{"name":"Personal"},"items":[
          {"categoryUuid":"001","favIndex":1,"overview":{"title":"GitHub","url":"https://github.com","urls":[{"url":"https://github.com"}]},
           "details":{"loginFields":[{"designation":"username","value":"octo"},{"designation":"password","value":"s3cret"}],
             "notesPlain":"hi","sections":[{"fields":[{"title":"one-time password","value":{"totp":"otpauth://totp/x?secret=JBSWY3DPEHPK3PXP"}},{"title":"Recovery","value":{"concealed":"abc"}}]}]}},
          {"categoryUuid":"002","overview":{"title":"My card"},"details":{"sections":[{"fields":[
             {"title":"cardholder","value":{"string":"R S"}},{"title":"number","value":{"creditCardNumber":"4111 1111 1111 1111"}},
             {"title":"expiry date","value":{"monthYear":202812}},{"title":"verification number","value":{"concealed":"999"}}]}]}},
          {"categoryUuid":"003","overview":{"title":"Note"},"details":{"notesPlain":"secret note"}},
          {"categoryUuid":"004","overview":{"title":"Identity"},"details":{}},
          {"categoryUuid":"001","state":"archived","overview":{"title":"Old"},"details":{}}
        ]}]}]}"""
        val bytes = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z ->
                z.putNextEntry(ZipEntry("export.attributes")); z.write("{}".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("export.data")); z.write(data.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val preview = PasswordImport.parseFile(bytes)
        assertEquals("1Password", preview.source)
        assertEquals(3, preview.logins.size)
        assertEquals(2, preview.skipped)
        val l = preview.logins.first { it.kind == ImportedKind.LOGIN }
        assertEquals("octo", l.username); assertEquals("s3cret", l.password); assertEquals("hi", l.notes)
        assertTrue(l.favorite); assertEquals("Personal", l.folder)
        assertTrue(l.totp.startsWith("otpauth://"))
        assertEquals("Recovery", l.customFields.single().label)
        val c = preview.logins.first { it.kind == ImportedKind.CARD }
        assertEquals("4111111111111111", c.cardNumber); assertEquals("12/28", c.cardExpiry); assertEquals("999", c.cardCvv)
        assertEquals("R S", c.cardholder)
    }

    @Test fun notAZipOrWrongZipFailsClearly() {
        val zip = ByteArrayOutputStream().also { b -> ZipOutputStream(b).use { it.putNextEntry(ZipEntry("x.txt")); it.write(1); it.closeEntry() } }.toByteArray()
        try { PasswordImport.parseFile(zip); assertTrue(false) } catch (e: com.ritvik.passvault.importer.ImportFormatException) { assertTrue(true) }
    }

    private fun c(id: String, url: String, user: String, pw: String, t: Long) = DuplicateCandidate(id, true, false, url, user, pw, "", t)

    @Test fun findsDuplicates() {
        val groups = DuplicateFinder.find(
            listOf(
                c("a", "https://www.amazon.com/login", "me", "p1", 1),
                c("b", "amazon.com", "ME", "p1", 5),
                c("c", "amazon.com", "other", "p1", 1),
                c("d", "github.com", "me", "x", 1),
                c("e", "github.com", "me", "y", 2),
                DuplicateCandidate("k1", false, true, "", "", "", "4111 1111 1111 1111", 1),
                DuplicateCandidate("k2", false, true, "", "", "", "4111111111111111", 9),
            ),
        )
        assertEquals(3, groups.size)
        val amazon = groups.first { "a" in it.ids }
        assertEquals("b", amazon.keepId); assertTrue(amazon.identical)
        val gh = groups.first { "d" in it.ids }
        assertFalse(gh.identical); assertEquals("e", gh.keepId)
        val cards = groups.first { it.isCard }
        assertEquals("k2", cards.keepId)
    }
}
