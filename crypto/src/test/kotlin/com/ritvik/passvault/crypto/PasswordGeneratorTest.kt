package com.ritvik.passvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test
    fun hasRequestedLength() {
        assertEquals(20, PasswordGenerator.generate().length)
        assertEquals(4, PasswordGenerator.generate(length = 4).length)
        assertEquals(128, PasswordGenerator.generate(length = 128).length)
    }

    @Test
    fun containsEveryEnabledClassEvenAtMinimumLength() {
        repeat(500) {
            val p = PasswordGenerator.generate(length = 4)
            assertTrue(p.any { it.isLowerCase() })
            assertTrue(p.any { it.isUpperCase() })
            assertTrue(p.any { it.isDigit() })
            assertTrue(p.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun respectsDisabledClasses() {
        repeat(200) {
            val p = PasswordGenerator.generate(length = 30, symbols = false, digits = false)
            assertTrue(p.all { it.isLetter() })
        }
        val digitsOnly = PasswordGenerator.generate(length = 16, lower = false, upper = false, symbols = false)
        assertTrue(digitsOnly.all { it.isDigit() })
    }

    @Test
    fun passwordsDiffer() {
        val set = (1..200).map { PasswordGenerator.generate() }.toSet()
        assertEquals(200, set.size)
    }

    @Test
    fun firstCharacterIsNotAlwaysTheSameClass() {
        val firstClasses = (1..400).map { PasswordGenerator.generate(length = 8)[0].isLowerCase() }.toSet()
        assertEquals(setOf(true, false), firstClasses)
    }

    @Test
    fun rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generate(lower = false, upper = false, digits = false, symbols = false)
        }
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(length = 3) }
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(length = 129) }
        assertFalse(PasswordGenerator.generate(length = 1, upper = false, digits = false, symbols = false).isEmpty())
    }
}
