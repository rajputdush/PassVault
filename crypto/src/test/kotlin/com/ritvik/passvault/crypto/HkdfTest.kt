package com.ritvik.passvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HkdfTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** RFC 5869, Appendix A.1 (Test Case 1, SHA-256). */
    @Test
    fun matchesRfc5869TestCase1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865",
        )
        assertArrayEquals(expected, Hkdf.derive(ikm, salt, info, 42))
    }

    /** RFC 5869, Appendix A.3 (Test Case 3): empty salt and empty info. */
    @Test
    fun matchesRfc5869TestCase3() {
        val ikm = ByteArray(22) { 0x0b }
        val expected = hex(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d" +
                "9d201395faa4b61a96c8",
        )
        assertArrayEquals(expected, Hkdf.derive(ikm, null, ByteArray(0), 42))
    }

    @Test
    fun differentInfoGivesIndependentKeys() {
        val ikm = secureRandomBytes(32)
        val a = Hkdf.derive(ikm, null, "a".toByteArray(), 32)
        val b = Hkdf.derive(ikm, null, "b".toByteArray(), 32)
        assertFalse(a.contentEquals(b))
    }
}
