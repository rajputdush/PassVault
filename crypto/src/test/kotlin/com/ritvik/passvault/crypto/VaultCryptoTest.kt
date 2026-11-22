package com.ritvik.passvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultCryptoTest {
    // Fast, relaxed settings for tests. Production uses Argon2id with KdfMinimums.STRICT.
    private val crypto = VaultCrypto(
        derivers = listOf(Pbkdf2KeyDeriver()),
        newVaultKdf = KdfSettings.pbkdf2(iterations = 1_000),
        minimums = KdfMinimums.NONE,
    )
    private val password = "correct horse battery staple".toCharArray()

    @Test
    fun createThenUnlockWithPasswordGivesSameVaultKey() {
        val created = crypto.createVault(password.copyOf())
        val unlocked = crypto.unlock(created.header, password.copyOf())
        assertArrayEquals(created.vaultKey, unlocked)
        assertEquals(32, unlocked.size)
    }

    @Test
    fun wrongPasswordIsRejected() {
        val created = crypto.createVault(password.copyOf())
        assertThrows(WrongCredentialsException::class.java) {
            crypto.unlock(created.header, "not the password".toCharArray())
        }
    }

    @Test
    fun passwordIsNormalizedSoComposedAndDecomposedAccentsMatch() {
        val composed = "café au lait 123".toCharArray() // é as one character
        val decomposed = "café au lait 123".toCharArray() // e + combining accent
        val created = crypto.createVault(composed)
        assertArrayEquals(created.vaultKey, crypto.unlock(created.header, decomposed))
    }

    @Test
    fun recoveryKeyUnlocksTheSameVaultKey() {
        val created = crypto.createVault(password.copyOf())
        assertArrayEquals(created.vaultKey, crypto.unlockWithRecoveryKey(created.header, created.recoveryKey))
    }

    @Test
    fun recoveryKeyTypedSloppilyStillWorks() {
        val created = crypto.createVault(password.copyOf())
        val sloppy = created.recoveryKey.lowercase().replace("-", " ")
        assertArrayEquals(created.vaultKey, crypto.unlockWithRecoveryKey(created.header, sloppy))
    }

    @Test
    fun wrongButWellFormedRecoveryKeyIsRejected() {
        val created = crypto.createVault(password.copyOf())
        val other = RecoveryKey.format(RecoveryKey.generate())
        assertThrows(WrongCredentialsException::class.java) { crypto.unlockWithRecoveryKey(created.header, other) }
    }

    @Test
    fun malformedRecoveryKeyIsReportedAsInvalid() {
        val created = crypto.createVault(password.copyOf())
        assertThrows(InvalidRecoveryKeyException::class.java) { crypto.unlockWithRecoveryKey(created.header, "nope") }
    }

    @Test
    fun passwordDoesNotUnlockViaRecoveryPathAndViceVersa() {
        val created = crypto.createVault(password.copyOf())
        // The recovery key text is not accepted as a password.
        assertThrows(WrongCredentialsException::class.java) {
            crypto.unlock(created.header, created.recoveryKey.toCharArray())
        }
    }

    @Test
    fun changingPasswordKeepsVaultKeyAndOldPasswordStopsWorking() {
        val created = crypto.createVault(password.copyOf())
        val newPassword = "a brand new passphrase!".toCharArray()
        val newHeader = crypto.changeMasterPassword(created.header, created.vaultKey, newPassword.copyOf())

        assertArrayEquals(created.vaultKey, crypto.unlock(newHeader, newPassword.copyOf()))
        assertThrows(WrongCredentialsException::class.java) { crypto.unlock(newHeader, password.copyOf()) }
        // Recovery key still works after a password change.
        assertArrayEquals(created.vaultKey, crypto.unlockWithRecoveryKey(newHeader, created.recoveryKey))
    }

    @Test
    fun changingPasswordUsesAFreshSalt() {
        val created = crypto.createVault(password.copyOf())
        val newHeader = crypto.changeMasterPassword(created.header, created.vaultKey, password.copyOf())
        assertFalse(created.header.kdf.salt.contentEquals(newHeader.kdf.salt))
    }

    @Test
    fun forgotPasswordFlowWorksAndExistingItemsSurvive() {
        val created = crypto.createVault(password.copyOf())
        val blob = crypto.encryptItem(created.vaultKey, "item-1", "secret".toByteArray())

        val newPassword = "reset via recovery 99".toCharArray()
        val result = crypto.resetMasterPasswordWithRecoveryKey(created.header, created.recoveryKey, newPassword.copyOf())

        assertArrayEquals(created.vaultKey, result.vaultKey)
        assertArrayEquals(created.vaultKey, crypto.unlock(result.header, newPassword.copyOf()))
        assertArrayEquals("secret".toByteArray(), crypto.decryptItem(result.vaultKey, "item-1", blob))
    }

    @Test
    fun twoVaultsHaveDifferentKeysAndSalts() {
        val a = crypto.createVault(password.copyOf())
        val b = crypto.createVault(password.copyOf())
        assertFalse(a.vaultKey.contentEquals(b.vaultKey))
        assertFalse(a.header.kdf.salt.contentEquals(b.header.kdf.salt))
        assertFalse(a.recoveryKey == b.recoveryKey)
    }

    @Test
    fun itemsAreBoundToTheirId() {
        val created = crypto.createVault(password.copyOf())
        val blob = crypto.encryptItem(created.vaultKey, "item-1", "secret".toByteArray())
        assertArrayEquals("secret".toByteArray(), crypto.decryptItem(created.vaultKey, "item-1", blob))
        // A blob copied onto another item id must not decrypt.
        assertThrows(DecryptionException::class.java) { crypto.decryptItem(created.vaultKey, "item-2", blob) }
    }

    @Test
    fun tamperedWrappedKeyIsRejected() {
        val created = crypto.createVault(password.copyOf())
        created.header.passwordWrappedKey.ciphertext[0] =
            (created.header.passwordWrappedKey.ciphertext[0].toInt() xor 1).toByte()
        assertThrows(WrongCredentialsException::class.java) { crypto.unlock(created.header, password.copyOf()) }
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val created = crypto.createVault(password.copyOf())
        val h = created.header
        val future = VaultHeader(99, h.kdf, h.passwordWrappedKey, h.recoveryWrappedKey)
        assertThrows(VaultFormatException::class.java) { crypto.unlock(future, password.copyOf()) }
    }

    @Test
    fun unknownKdfAlgorithmIsRejected() {
        val created = crypto.createVault(password.copyOf())
        val h = created.header
        val kdf = KdfParams("md5", h.kdf.salt, 1000, 0, 1)
        val bad = VaultHeader(h.version, kdf, h.passwordWrappedKey, h.recoveryWrappedKey)
        assertThrows(VaultFormatException::class.java) { crypto.unlock(bad, password.copyOf()) }
    }

    @Test
    fun strictCryptoRefusesHeadersWithWeakParameters() {
        val created = crypto.createVault(password.copyOf()) // 1,000 PBKDF2 rounds
        val strict = VaultCrypto(
            derivers = listOf(Pbkdf2KeyDeriver()),
            newVaultKdf = KdfSettings.pbkdf2(), // 600,000
            minimums = KdfMinimums.STRICT,
        )
        assertThrows(VaultFormatException::class.java) { strict.unlock(created.header, password.copyOf()) }
    }

    @Test
    fun absurdParametersAreRejectedBeforeAnyWork() {
        val created = crypto.createVault(password.copyOf())
        val h = created.header
        val huge = VaultHeader(h.version, KdfParams(Kdf.PBKDF2_SHA256, h.kdf.salt, Int.MAX_VALUE, 0, 1), h.passwordWrappedKey, h.recoveryWrappedKey)
        assertThrows(VaultFormatException::class.java) { crypto.unlock(huge, password.copyOf()) }
        val shortSalt = VaultHeader(h.version, KdfParams(Kdf.PBKDF2_SHA256, ByteArray(4), 1000, 0, 1), h.passwordWrappedKey, h.recoveryWrappedKey)
        assertThrows(VaultFormatException::class.java) { crypto.unlock(shortSalt, password.copyOf()) }
    }

    @Test
    fun constructingWithSettingsBelowMinimumsFailsFast() {
        assertThrows(VaultFormatException::class.java) {
            VaultCrypto(listOf(Pbkdf2KeyDeriver()), KdfSettings.pbkdf2(iterations = 1_000), KdfMinimums.STRICT)
        }
    }

    @Test
    fun regeneratingRecoveryKeyReplacesTheOldOne() {
        val created = crypto.createVault(password.copyOf())
        val fresh = crypto.regenerateRecoveryKey(created.header, created.vaultKey)

        // The new key unlocks the same vault key; the old one no longer does.
        assertArrayEquals(created.vaultKey, crypto.unlockWithRecoveryKey(fresh.header, fresh.recoveryKey))
        assertThrows(WrongCredentialsException::class.java) {
            crypto.unlockWithRecoveryKey(fresh.header, created.recoveryKey)
        }
        assertFalse(fresh.recoveryKey == created.recoveryKey)
        // The password and its wrapper are untouched.
        assertArrayEquals(created.vaultKey, crypto.unlock(fresh.header, password.copyOf()))
        assertEquals(created.header.kdf, fresh.header.kdf)
    }
}
