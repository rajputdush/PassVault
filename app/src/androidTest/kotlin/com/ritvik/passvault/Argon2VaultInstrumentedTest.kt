package com.ritvik.passvault

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritvik.passvault.crypto.KdfParams
import com.ritvik.passvault.crypto.Kdf
import com.ritvik.passvault.crypto.KdfSettings
import com.ritvik.passvault.crypto.VaultCrypto
import com.ritvik.passvault.crypto.WrongCredentialsException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator because Argon2Kt loads a native library. */
@RunWith(AndroidJUnit4::class)
class Argon2VaultInstrumentedTest {
    private val deriver = Argon2KeyDeriver()
    private val crypto = VaultCrypto(
        derivers = listOf(deriver),
        newVaultKdf = KdfSettings.argon2id(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1),
    )

    private fun params(salt: ByteArray) = KdfParams(Kdf.ARGON2ID, salt, 3, 64 * 1024, 1)

    @Test
    fun argon2IsDeterministicForSameInputs() {
        val salt = ByteArray(16) { it.toByte() }
        val a = deriver.derive("password-123456".toCharArray(), params(salt))
        val b = deriver.derive("password-123456".toCharArray(), params(salt))
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
    }

    @Test
    fun differentSaltOrPasswordGivesDifferentKey() {
        val salt = ByteArray(16) { it.toByte() }
        val base = deriver.derive("password-123456".toCharArray(), params(salt))
        val otherSalt = deriver.derive("password-123456".toCharArray(), params(ByteArray(16) { (it + 1).toByte() }))
        val otherPassword = deriver.derive("password-654321".toCharArray(), params(salt))
        assertFalse(base.contentEquals(otherSalt))
        assertFalse(base.contentEquals(otherPassword))
    }

    @Test
    fun fullVaultRoundTripWithArgon2() {
        val password = "a long master password!".toCharArray()
        val created = crypto.createVault(password.copyOf())
        assertArrayEquals(created.vaultKey, crypto.unlock(created.header, password.copyOf()))
        assertArrayEquals(created.vaultKey, crypto.unlockWithRecoveryKey(created.header, created.recoveryKey))
        assertThrows(WrongCredentialsException::class.java) {
            crypto.unlock(created.header, "wrong password here".toCharArray())
        }
    }

    @Test
    fun headerSurvivesJsonRoundTrip() {
        val password = "a long master password!".toCharArray()
        val created = crypto.createVault(password.copyOf())
        val restored = VaultHeaderJson.decode(VaultHeaderJson.encode(created.header))
        assertArrayEquals(created.vaultKey, crypto.unlock(restored, password.copyOf()))
    }
}
