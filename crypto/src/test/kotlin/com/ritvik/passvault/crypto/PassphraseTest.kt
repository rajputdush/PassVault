package com.ritvik.passvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PassphraseTest {
    @Test fun wordListIsTheFullEffList() {
        val words = EffWordList.words
        assertEquals(7776, words.size)
        assertEquals(7776, words.toSet().size)
        assertEquals("abacus", words.first())
        assertEquals("zoom", words.last())
        assertTrue(words.all { w -> w.isNotEmpty() && w.all { it in 'a'..'z' || it == '-' } })
    }

    @Test fun producesRequestedShape() {
        val p = PasswordGenerator.passphrase(words = 6, separator = " ", capitalize = true)
        val parts = p.split(" ")
        assertEquals(6, parts.size)
        assertTrue(parts.all { it.first().isUpperCase() })
        assertTrue(parts.all { it.lowercase() in EffWordList.words })
    }

    @Test fun lowercaseWithNumber() {
        val p = PasswordGenerator.passphrase(words = 4, capitalize = false, addNumber = true)
        assertEquals(1, p.count { it.isDigit() })
        assertTrue(p.none { it.isUpperCase() })
    }

    @Test fun differentEachTime() {
        val set = (1..20).map { PasswordGenerator.passphrase() }.toSet()
        assertTrue(set.size > 15)
    }

    @Test fun rejectsBadWordCounts() {
        for (n in listOf(0, 2, 13)) {
            try {
                PasswordGenerator.passphrase(words = n)
                fail("expected failure for $n")
            } catch (e: IllegalArgumentException) {
            }
        }
    }

    @Test fun entropyEstimate() {
        assertEquals(64.6, PasswordGenerator.passphraseBits(5), 0.1)
    }
}
