package com.ritvik.passvault.share

import com.ritvik.passvault.crypto.KdfMinimums
import com.ritvik.passvault.crypto.KdfSettings
import com.ritvik.passvault.crypto.Pbkdf2KeyDeriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ShareCodecTest {
    private val deriver = Pbkdf2KeyDeriver()
    private val fast = KdfSettings.pbkdf2(iterations = 1_000)
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val passphrase = "tumble-rocket-dimple-oxygen-salad"

    private val login = SharedItem(
        type = "login",
        title = "Netflix",
        username = "ritvik@example.com",
        password = "p@ss \"word\" \\ with\nnewline\tand tab \u0001",
        url = "https://netflix.com",
        notes = "Family plan – ₹649, héllo 日本語 😀",
        totp = "otpauth://totp/Netflix:ritvik?secret=JBSWY3DPEHPK3PXP&issuer=Netflix",
        appId = "com.netflix.mediaclient",
        customFields = listOf(SharedField("Security question", "Rover", true), SharedField("Plan", "Premium", false)),
    )

    private fun seal(item: SharedItem = login, pass: String = passphrase, valid: Long = day, at: Long = now) =
        ShareCodec.seal(item, pass.toCharArray(), deriver, fast, at, valid)

    private fun open(code: String, pass: String = passphrase, at: Long = now, minimums: KdfMinimums = KdfMinimums.NONE) =
        ShareCodec.open(code, pass.toCharArray(), listOf(deriver), at, minimums)

    private fun reason(block: () -> Unit): ShareException.Reason =
        assertThrows(ShareException::class.java) { block() }.reason

    @Test
    fun roundTripKeepsEveryField() {
        assertEquals(login, open(seal()))
    }

    @Test
    fun cardAndNoteRoundTrip() {
        val card = SharedItem(
            type = "card", title = "Visa", cardholder = "R SHARMA", cardNumber = "4111111111111111",
            cardExpiry = "08/28", cardCvv = "123", cardPin = "4321",
        )
        assertEquals(card, open(seal(card)))
        val note = SharedItem(type = "note", title = "Wi-Fi", notes = "line one\nline two")
        assertEquals(note, open(seal(note)))
    }

    @Test
    fun codeIsTextThatSurvivesChatApps() {
        val code = seal()
        assertTrue(code.startsWith("PVS1."))
        assertTrue(code.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' })
    }

    @Test
    fun codeNeverContainsThePlaintext() {
        val code = seal()
        val raw = Base64.getUrlDecoder().decode(code.removePrefix("PVS1."))
        val text = String(raw, Charsets.ISO_8859_1)
        assertFalse(text.contains("Netflix"))
        assertFalse(code.contains("Netflix"))
        assertFalse(code.contains(passphrase))
    }

    @Test
    fun everyCodeIsDifferent() {
        assertNotEquals(seal(), seal())
    }

    @Test
    fun wrongPassphraseIsRejected() {
        assertEquals(ShareException.Reason.WRONG_PASSPHRASE, reason { open(seal(), pass = "tumble-rocket-dimple-oxygen-pepper") })
    }

    @Test
    fun passphraseIgnoresCaseAndOuterSpaces() {
        val code = seal()
        assertEquals(login, open(code, pass = "  Tumble-Rocket-Dimple-OXYGEN-Salad "))
    }

    @Test
    fun tamperingIsDetected() {
        val code = seal()
        val body = code.removePrefix("PVS1.").toCharArray()
        for (position in listOf(0, 3, 12, 20, body.size / 2, body.size - 2)) {
            val copy = body.copyOf()
            copy[position] = if (copy[position] == 'A') 'B' else 'A'
            val reason = reason { open("PVS1." + String(copy)) }
            assertTrue(reason == ShareException.Reason.WRONG_PASSPHRASE || reason == ShareException.Reason.NOT_A_SHARE ||
                reason == ShareException.Reason.UNSUPPORTED || reason == ShareException.Reason.WEAK_SETTINGS)
        }
    }

    @Test
    fun loweringTheKeyDerivationCostInTheHeaderIsDetected() {
        val raw = Base64.getUrlDecoder().decode(seal().removePrefix("PVS1."))
        // iterations live at bytes 6..9; make it 1 instead of 1000.
        raw[6] = 0; raw[7] = 0; raw[8] = 0; raw[9] = 1
        val forged = "PVS1." + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        assertEquals(ShareException.Reason.WRONG_PASSPHRASE, reason { open(forged) })
    }

    @Test
    fun expiredShareIsRefused() {
        val code = seal(valid = day)
        assertEquals(login, open(code, at = now + day - 1))
        assertEquals(ShareException.Reason.EXPIRED, reason { open(code, at = now + day + 1) })
    }

    @Test
    fun weakKeyDerivationIsRefusedByStrictMinimums() {
        assertEquals(ShareException.Reason.WEAK_SETTINGS, reason { open(seal(), minimums = KdfMinimums.STRICT) })
    }

    @Test
    fun strictMinimumsAcceptStrongSettings() {
        val strong = KdfSettings.pbkdf2(iterations = 210_000)
        val code = ShareCodec.seal(login, passphrase.toCharArray(), deriver, strong, now, day)
        assertEquals(login, ShareCodec.open(code, passphrase.toCharArray(), listOf(deriver), now, KdfMinimums.STRICT))
    }

    @Test
    fun hostileKeyDerivationSettingsAreRejectedBeforeDoingWork() {
        // Argon2id asking for ~2 GiB of memory must not be attempted.
        val header = java.nio.ByteBuffer.allocate(27 + 12 + 16 + 4)
            .put(1).put(1).putInt(2 * 1024 * 1024).putInt(3).put(1).put(ByteArray(16)).put(ByteArray(12 + 16 + 4)).array()
        val code = "PVS1." + Base64.getUrlEncoder().withoutPadding().encodeToString(header)
        assertEquals(ShareException.Reason.NOT_A_SHARE, reason { open(code) })
    }

    @Test
    fun garbageIsNotAShare() {
        assertEquals(ShareException.Reason.NOT_A_SHARE, reason { open("hello world") })
        assertEquals(ShareException.Reason.NOT_A_SHARE, reason { open("") })
        assertEquals(ShareException.Reason.NOT_A_SHARE, reason { open("PVS1.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA") })
    }

    @Test
    fun truncatedCodeIsRejected() {
        val code = seal()
        assertTrue(runCatching { open(code.dropLast(30)) }.exceptionOrNull() is ShareException)
        assertTrue(runCatching { open(code.take(40)) }.exceptionOrNull() is ShareException)
    }

    @Test
    fun newerVersionIsReportedClearly() {
        val raw = Base64.getUrlDecoder().decode(seal().removePrefix("PVS1."))
        raw[0] = 2
        val forged = "PVS1." + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        assertEquals(ShareException.Reason.UNSUPPORTED, reason { open(forged) })
    }

    @Test
    fun codeIsFoundInsideAMessageOrALink() {
        val code = seal()
        val link = ShareCodec.toLink(code)
        assertEquals("passvault://share/$code", link)
        assertEquals(code, ShareCodec.extractCode(link))
        assertEquals(code, ShareCodec.extractCode("Hi! Open this in PassVault:\n$link\nPassphrase coming separately."))
        assertEquals(login, open("Here you go: $link  thanks"))
        assertNull(ShareCodec.extractCode("no code here"))
        assertNull(ShareCodec.extractCode("PVS1.short"))
    }

    @Test
    fun ordinaryItemFitsInAQrCode() {
        val link = ShareCodec.toLink(seal())
        assertTrue("link was ${link.length} chars", ShareCodec.fitsInQr(link))
        val big = login.copy(notes = (1..400).joinToString(" ") { "word${it * 7919 % 1000}" })
        assertFalse(ShareCodec.fitsInQr(ShareCodec.toLink(seal(big))))
    }

    @Test
    fun hugeItemsAreRefused() {
        val noise = java.util.Random(7)
        val random = String(CharArray(400_000) { ('a'.code + noise.nextInt(26) * 1).toChar() }) +
            String(CharArray(200_000) { (33 + noise.nextInt(90)).toChar() })
        assertEquals(ShareException.Reason.TOO_BIG, reason { seal(login.copy(notes = random)) })
    }

    @Test
    fun shortPassphrasesAreNotAllowed() {
        assertThrows(IllegalArgumentException::class.java) { seal(pass = "short") }
    }

    @Test
    fun generatedPassphraseWorksEndToEnd() {
        val generated = ShareCodec.generatePassphrase()
        assertEquals(5, generated.split("-").size)
        assertEquals(generated.lowercase(), generated)
        assertTrue(generated.length >= ShareCodec.MIN_PASSPHRASE_LENGTH)
        assertEquals(login, open(seal(pass = generated), pass = generated))
    }

    @Test
    fun decompressionBombsAreStopped() {
        val bomb = ShareCodec.gzip(ByteArray(5_000_000))
        assertTrue(bomb.size < 10_000)
        assertThrows(ShareException::class.java) { ShareCodec.gunzip(bomb, 200_000) }
        assertEquals(1000, ShareCodec.gunzip(ShareCodec.gzip(ByteArray(1000)), 200_000).size)
    }

    @Test
    fun customFieldsAreCappedOnOpen() {
        val many = (1..30).map { SharedField("f$it", "v$it", false) }
        val opened = open(seal(login.copy(customFields = many)))
        assertEquals(20, opened.customFields.size)
    }

    @Test
    fun unknownTypeFallsBackToLogin() {
        val opened = open(seal(login.copy(type = "hologram")))
        assertEquals("login", opened.type)
    }

    @Test fun webLinkKeepsTheCodeAfterTheHash() {
        val code = seal()
        val link = ShareCodec.toLink(code, "https://example.com/passvault/share/")
        assertTrue(link.startsWith("https://example.com/passvault/share/#PVS1."))
        assertEquals(code, ShareCodec.extractCode(link))
        assertEquals(code, ShareCodec.extractCode("Open: $link\nthanks"))
        assertEquals(ShareCodec.LINK_PREFIX + code, ShareCodec.toLink(code, ""))
    }
}
