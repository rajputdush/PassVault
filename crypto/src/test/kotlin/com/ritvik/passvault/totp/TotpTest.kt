package com.ritvik.passvault.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpTest {
    private val sha1Secret = "12345678901234567890".toByteArray()
    private val sha256Secret = "12345678901234567890123456789012".toByteArray()
    private val sha512Secret = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    private fun cfg(secret: ByteArray, alg: TotpAlgorithm) = TotpConfig(secret, alg, digits = 8)

    // Test vectors from RFC 6238, appendix B.
    @Test fun rfc6238Sha1() {
        val c = cfg(sha1Secret, TotpAlgorithm.SHA1)
        assertEquals("94287082", Totp.code(c, 59_000))
        assertEquals("07081804", Totp.code(c, 1_111_111_109_000))
        assertEquals("14050471", Totp.code(c, 1_111_111_111_000))
        assertEquals("89005924", Totp.code(c, 1_234_567_890_000))
        assertEquals("69279037", Totp.code(c, 2_000_000_000_000))
        assertEquals("65353130", Totp.code(c, 20_000_000_000_000))
    }

    @Test fun rfc6238Sha256() {
        val c = cfg(sha256Secret, TotpAlgorithm.SHA256)
        assertEquals("46119246", Totp.code(c, 59_000))
        assertEquals("68084774", Totp.code(c, 1_111_111_109_000))
    }

    @Test fun rfc6238Sha512() {
        val c = cfg(sha512Secret, TotpAlgorithm.SHA512)
        assertEquals("90693936", Totp.code(c, 59_000))
        assertEquals("25091201", Totp.code(c, 1_111_111_109_000))
    }

    @Test fun sixDigitCodeIsZeroPaddedTail() {
        val c = TotpConfig(sha1Secret)
        assertEquals("287082", Totp.code(c, 59_000))
        assertEquals("081804", Totp.code(c, 1_111_111_109_000))
    }

    @Test fun secondsRemainingCountsDown() {
        val c = TotpConfig(sha1Secret)
        assertEquals(30, Totp.secondsRemaining(c, 0))
        assertEquals(1, Totp.secondsRemaining(c, 29_999))
        assertEquals(30, Totp.secondsRemaining(c, 30_000))
        assertEquals(1, Totp.secondsRemaining(c, 59_000))
    }

    @Test fun base32RoundTrip() {
        for (size in 0..40) {
            val bytes = ByteArray(size) { (it * 37 + size).toByte() }
            if (size == 0) continue
            assertArrayEquals(bytes, Base32.decode(Base32.encode(bytes)))
        }
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("mzxw 6ytb-oi======"))
        assertNull(Base32.decode("not base32 !"))
        assertNull(Base32.decode(""))
    }

    @Test fun parsesBareSecretWithSpaces() {
        val secret = Base32.encode(sha1Secret)
        val spaced = secret.chunked(4).joinToString(" ").lowercase()
        val c = Totp.parse(spaced)
        assertNotNull(c)
        assertArrayEquals(sha1Secret, c!!.secret)
        assertEquals(6, c.digits)
        assertEquals(30, c.periodSeconds)
    }

    @Test fun parsesOtpauthLink() {
        val secret = Base32.encode(sha256Secret)
        val c = Totp.parse("otpauth://totp/GitHub:me%40mail.com?secret=$secret&issuer=GitHub&algorithm=SHA256&digits=8&period=60")
        assertNotNull(c)
        assertEquals("GitHub", c!!.issuer)
        assertEquals("me@mail.com", c.account)
        assertEquals(TotpAlgorithm.SHA256, c.algorithm)
        assertEquals(8, c.digits)
        assertEquals(60, c.periodSeconds)
        assertArrayEquals(sha256Secret, c.secret)
    }

    @Test fun issuerFallsBackToLabelAndPlusMeansSpaceInQuery() {
        val secret = Base32.encode(sha1Secret)
        val c = Totp.parse("OTPAUTH://TOTP/My%20Bank:alice?secret=$secret")
        assertEquals("My Bank", c!!.issuer)
        val d = Totp.parse("otpauth://totp/x?secret=$secret&issuer=Big+Co")
        assertEquals("Big Co", d!!.issuer)
    }

    @Test fun rejectsBadInput() {
        val secret = Base32.encode(sha1Secret)
        assertNull(Totp.parse(""))
        assertNull(Totp.parse("hello world"))
        assertNull(Totp.parse("otpauth://hotp/x?secret=$secret&counter=1"))
        assertNull(Totp.parse("otpauth://totp/x"))
        assertNull(Totp.parse("otpauth://totp/x?secret=$secret&digits=12"))
        assertNull(Totp.parse("otpauth://totp/x?secret=$secret&period=0"))
        assertNull(Totp.parse("otpauth://totp/x?secret=$secret&algorithm=MD5"))
        assertNull(Totp.parse("ABCD")) // far too short to be a real secret
        assertTrue(Totp.isValid(secret))
    }

    @Test fun formatGroupsDigits() {
        assertEquals("123 456", Totp.format("123456"))
        assertEquals("1234 5678", Totp.format("12345678"))
    }
}
