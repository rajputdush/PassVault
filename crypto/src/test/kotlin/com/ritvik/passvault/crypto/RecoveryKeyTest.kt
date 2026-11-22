package com.ritvik.passvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryKeyTest {
    @Test
    fun formatLooksLikeThirteenGroupsOfFour() {
        val text = RecoveryKey.format(RecoveryKey.generate())
        assertEquals(13 * 4 + 12, text.length)
        assertTrue(text.matches(Regex("([A-Z2-7]{4}-){12}[A-Z2-7]{4}")))
    }

    @Test
    fun roundTripManyRandomKeys() {
        repeat(200) {
            val bytes = RecoveryKey.generate()
            assertArrayEquals(bytes, RecoveryKey.parse(RecoveryKey.format(bytes)))
        }
    }

    @Test
    fun allZeroAndAllOnesRoundTrip() {
        val zeros = ByteArray(32)
        val ones = ByteArray(32) { 0xFF.toByte() }
        assertArrayEquals(zeros, RecoveryKey.parse(RecoveryKey.format(zeros)))
        assertArrayEquals(ones, RecoveryKey.parse(RecoveryKey.format(ones)))
    }

    @Test
    fun parseIsLenientAboutCaseSpacesAndDashes() {
        val bytes = RecoveryKey.generate()
        val formatted = RecoveryKey.format(bytes)
        assertArrayEquals(bytes, RecoveryKey.parse(formatted.lowercase()))
        assertArrayEquals(bytes, RecoveryKey.parse(formatted.replace("-", "")))
        assertArrayEquals(bytes, RecoveryKey.parse(formatted.replace("-", "  ")))
    }

    @Test
    fun parseFixesLookAlikeDigits() {
        // The alphabet has no 0 or 8, so a typed 0 can only mean O and a typed 8 can only mean B.
        val bytes = ByteArray(32) { 0xFF.toByte() }
        val formatted = RecoveryKey.format(bytes)
        val withDigits = formatted.replace('O', '0').replace('B', '8')
        assertArrayEquals(bytes, RecoveryKey.parse(withDigits))
    }

    @Test
    fun rejectsWrongLength() {
        assertThrows(InvalidRecoveryKeyException::class.java) { RecoveryKey.parse("ABCD-EFGH") }
        assertThrows(InvalidRecoveryKeyException::class.java) { RecoveryKey.parse("") }
    }

    @Test
    fun rejectsInvalidCharacters() {
        val good = RecoveryKey.format(RecoveryKey.generate())
        val bad = "!" + good.substring(1)
        assertThrows(InvalidRecoveryKeyException::class.java) { RecoveryKey.parse(bad) }
    }
}
