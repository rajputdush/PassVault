package com.ritvik.passvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class BreachCheckTest {
    /** The example from the Have I Been Pwned documentation: SHA-1("password"). */
    @Test
    fun sha1OfPasswordMatchesKnownValue() {
        val hash = BreachCheck.sha1Hex("password")
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", hash)
        assertEquals("5BAA6", BreachCheck.prefix(hash))
        assertEquals("1E4C9B93F3F0682250B6CF8331B7EE68FD8", BreachCheck.suffix(hash))
    }

    @Test
    fun sha1HandlesUnicodeAsUtf8() {
        // SHA-1 of the UTF-8 bytes of "pässwörd" (not of any other encoding).
        val hash = BreachCheck.sha1Hex("pässwörd")
        assertEquals(40, hash.length)
        assertEquals(hash, BreachCheck.sha1Hex("pässwörd"))
    }

    private val body = """
        0018A45C4D1DEF81644B54AB7F969B88D65:21
        00D4F6E8FA6EECAD2A3AA415EEC418D38EC:2
        1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824
        1F2B668E8AABEF1C59E9EC6F82E3F3CD786:0
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun findsTheCountForAMatchingSuffix() {
        assertEquals(9_545_824L, BreachCheck.countInRange(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
        assertEquals(21L, BreachCheck.countInRange(body, "0018A45C4D1DEF81644B54AB7F969B88D65"))
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals(2L, BreachCheck.countInRange(body, "00d4f6e8fa6eecad2a3aa415eec418d38ec"))
    }

    @Test
    fun missingSuffixIsZero() {
        assertEquals(0L, BreachCheck.countInRange(body, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"))
        assertEquals(0L, BreachCheck.countInRange("", "ABC"))
    }

    @Test
    fun paddingEntriesWithZeroCountDoNotMatch() {
        assertEquals(0L, BreachCheck.countInRange(body, "1F2B668E8AABEF1C59E9EC6F82E3F3CD786"))
    }

    @Test
    fun garbageLinesAreIgnored() {
        assertEquals(5L, BreachCheck.countInRange("nonsense\n:7\nABC:x\nABC:5", "ABC"))
    }
}
