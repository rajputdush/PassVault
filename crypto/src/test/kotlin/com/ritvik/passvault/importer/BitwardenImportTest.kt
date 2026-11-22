package com.ritvik.passvault.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BitwardenImportTest {
    private val export = """
    {
      "encrypted": false,
      "folders": [ {"id": "f1", "name": "Work"}, {"id": "f2", "name": "Bank"} ],
      "items": [
        {"id":"1","folderId":"f1","type":1,"name":"GitHub","notes":"my \"main\" account","favorite":true,
         "fields":[{"name":"Security question","value":"Rex","type":1},{"name":"Plan","value":"free","type":0},{"name":"linked","value":null,"type":3}],
         "login":{"uris":[{"match":null,"uri":"https://github.com/login"}],"username":"ritvik","password":"p@ssé","totp":"otpauth://totp/GitHub:ritvik?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"}},
        {"id":"2","folderId":null,"type":1,"name":"Netflix","favorite":false,
         "login":{"uris":[{"uri":"androidapp://com.netflix.mediaclient"}],"username":"me","password":"x","totp":"steam://ABCDEFGHIJKLMNOP"}},
        {"id":"3","folderId":"f2","type":3,"name":"HDFC","favorite":false,
         "card":{"cardholderName":"R Singh","brand":"Visa","number":"4111 1111 1111 1111","expMonth":"7","expYear":"2030","code":"123"}},
        {"id":"4","type":2,"name":"Wifi","notes":"code 1234","secureNote":{"type":0}},
        {"id":"5","type":4,"name":"Me","identity":{"firstName":"R"}},
        {"id":"6","type":1,"name":"","login":{}}
      ]
    }
    """.trimIndent()

    private fun items() = PasswordImport.parse(export)

    @Test fun detectsJsonAndCountsEverything() {
        val p = items()
        assertEquals("Bitwarden", p.source)
        assertEquals(4, p.logins.size)
        assertEquals(2, p.skipped) // the identity and the empty login
    }

    @Test fun loginKeepsFolderFavouriteTotpAndFields() {
        val gh = items().logins[0]
        assertEquals(ImportedKind.LOGIN, gh.kind)
        assertEquals("GitHub", gh.title)
        assertEquals("https://github.com/login", gh.url)
        assertEquals("ritvik", gh.username)
        assertEquals("p@ssé", gh.password)
        assertEquals("my \"main\" account", gh.notes)
        assertEquals("Work", gh.folder)
        assertTrue(gh.favorite)
        assertTrue(gh.totp.startsWith("otpauth://totp/GitHub"))
        assertEquals(2, gh.customFields.size) // the linked field is dropped
        assertEquals("Security question", gh.customFields[0].label)
        assertTrue(gh.customFields[0].hidden)
        assertFalse(gh.customFields[1].hidden)
    }

    @Test fun androidAppLoginsBecomeLinkedAndUnsupportedTotpIsKeptAsAField() {
        val nf = items().logins[1]
        assertEquals("", nf.url)
        assertEquals("com.netflix.mediaclient", nf.appId)
        assertEquals("", nf.totp)
        assertEquals("2FA key (not usable here)", nf.customFields[0].label)
        assertTrue(nf.customFields[0].hidden)
    }

    @Test fun cardAndNote() {
        val card = items().logins[2]
        assertEquals(ImportedKind.CARD, card.kind)
        assertEquals("4111111111111111", card.cardNumber)
        assertEquals("R Singh", card.cardholder)
        assertEquals("07/30", card.cardExpiry)
        assertEquals("123", card.cardCvv)
        assertEquals("Bank", card.folder)
        assertEquals("Brand", card.customFields[0].label)
        val note = items().logins[3]
        assertEquals(ImportedKind.NOTE, note.kind)
        assertEquals("Wifi", note.title)
        assertEquals("code 1234", note.notes)
    }

    @Test fun encryptedExportIsRefusedWithAHelpfulMessage() {
        try {
            PasswordImport.parse("""{"encrypted":true,"encKeyValidation_DO_NOT_EDIT":"x","data":"y"}""")
            fail("expected a refusal")
        } catch (e: ImportFormatException) {
            assertTrue(e.message!!.contains("encrypted"))
        }
    }

    @Test fun garbageIsRefused() {
        for (bad in listOf("{", "{\"a\":}", "{\"items\": 5}", "[1,2]", "{\"items\":[]} trailing")) {
            try {
                PasswordImport.parse(bad)
                fail("expected failure for $bad")
            } catch (e: ImportFormatException) {
            }
        }
    }

    @Test fun veryDeepNestingIsRefused() {
        val deep = "{\"items\":" + "[".repeat(200) + "]".repeat(200) + "}"
        try {
            PasswordImport.parse(deep)
            fail("expected failure")
        } catch (e: ImportFormatException) {
        }
    }

    @Test fun csvStillWorksAndJsonDetectionIgnoresBom() {
        val csv = "name,url,username,password\nAmazon,https://amazon.com,me,pw\n"
        assertEquals(1, PasswordImport.parse(csv).logins.size)
        assertEquals(4, PasswordImport.parse("﻿" + export).logins.size)
    }

    @Test fun duplicatesAreDroppedPerType() {
        val p = items()
        val fresh = PasswordImport.withoutDuplicates(
            p.logins,
            existing = listOf(Triple("github.com", "ritvik", "p@ssé")),
            existingCards = listOf("4111 1111 1111 1111"),
            existingNotes = listOf(PasswordImport.noteKey("Wifi", "code 1234")),
        )
        assertEquals(listOf("Netflix"), fresh.map { it.title })
        // Importing the same file twice inside one run only keeps one copy of each.
        assertEquals(4, PasswordImport.withoutDuplicates(p.logins + p.logins, emptyList()).size)
    }

    @Test fun miniJsonUnderstandsEscapes() {
        val v = MiniJson.parse("""{"s":"a\nbA\\\"","n":-1.5e2,"t":true,"z":null,"l":[1,[2]]}""") as Map<*, *>
        assertEquals("a\nbA\\\"", v["s"])
        assertEquals(-150.0, v["n"])
        assertEquals(true, v["t"])
        assertTrue(v.containsKey("z") && v["z"] == null)
        assertEquals(listOf(1.0, listOf(2.0)), v["l"])
    }
}
