package com.ritvik.passvault.crypto

import com.ritvik.passvault.crypto.PasswordPolicy.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    private fun s(p: String) = PasswordPolicy.evaluate(p.toCharArray())

    @Test
    fun shortPasswordsAreRejected() {
        assertEquals(Strength.TOO_SHORT, s("Ab1!"))
        assertFalse(PasswordPolicy.isAcceptable("elevenchars".toCharArray()))
        assertTrue(PasswordPolicy.isAcceptable("twelvechars!".toCharArray()))
    }

    @Test
    fun repetitiveIsWeak() {
        assertEquals(Strength.WEAK, s("aaaaaaaaaaaaaaaa"))
        assertEquals(Strength.WEAK, s("abababababab"))
    }

    @Test
    fun lowercaseOnlyOfModerateLengthIsWeak() {
        assertEquals(Strength.WEAK, s("passwordlonger"))
    }

    @Test
    fun mixedClassesAreStrongerThanLowercaseOnly() {
        assertEquals(Strength.FAIR, s("Passwordlonger"))
        assertEquals(Strength.STRONG, s("Passw0rd-longer!"))
    }

    @Test
    fun longPassphraseIsStrong() {
        assertEquals(Strength.STRONG, s("correct horse battery staple"))
    }
}
