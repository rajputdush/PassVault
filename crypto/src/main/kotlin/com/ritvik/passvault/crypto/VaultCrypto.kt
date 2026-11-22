package com.ritvik.passvault.crypto

import java.nio.CharBuffer
import java.text.Normalizer

/**
 * Key hierarchy:
 *
 *   master password --Argon2id(salt)--> master key --HKDF--> password-wrap key
 *   recovery key (256 random bits) ---HKDF--------------->  recovery-wrap key
 *   vault key (256 random bits)  <- stored wrapped by BOTH wrap keys (AES-256-GCM)
 *   vault key --> encrypts every vault item (AES-256-GCM, item id bound as associated data)
 *
 * Because items are encrypted with the vault key and not with a password-derived key,
 * changing the master password only re-wraps one 32-byte key.
 */
class VaultCrypto(
    derivers: List<KeyDeriver>,
    private val newVaultKdf: KdfSettings,
    private val minimums: KdfMinimums = KdfMinimums.STRICT,
) {
    private val deriversByAlgorithm: Map<String, KeyDeriver> = derivers.associateBy { it.algorithm }

    init {
        require(newVaultKdf.algorithm in deriversByAlgorithm) {
            "No KeyDeriver registered for ${newVaultKdf.algorithm}"
        }
        // Fail fast if the configured settings for new vaults are weaker than the allowed minimums.
        validate(newVaultKdf.toParams(ByteArray(SALT_SIZE_BYTES)))
    }

    class CreatedVault(
        val header: VaultHeader,
        /** The unlocked vault key. The caller owns it and must wipe it after use. */
        val vaultKey: ByteArray,
        /** Shown to the user exactly once. It is not stored anywhere. */
        val recoveryKey: String,
    )

    class UnlockedVault(val header: VaultHeader, val vaultKey: ByteArray)

    fun createVault(masterPassword: CharArray): CreatedVault {
        val vaultKey = secureRandomBytes(AesGcm.KEY_SIZE_BYTES)
        val recoveryBytes = RecoveryKey.generate()
        try {
            val kdf = newVaultKdf.toParams(secureRandomBytes(SALT_SIZE_BYTES))
            val header = VaultHeader(
                version = VaultHeader.CURRENT_VERSION,
                kdf = kdf,
                passwordWrappedKey = wrapWithPassword(kdf, masterPassword, vaultKey),
                recoveryWrappedKey = wrapWithRecovery(recoveryBytes, vaultKey),
            )
            return CreatedVault(header, vaultKey, RecoveryKey.format(recoveryBytes))
        } finally {
            recoveryBytes.wipe()
        }
    }

    /** @throws WrongCredentialsException if the password is wrong. */
    fun unlock(header: VaultHeader, masterPassword: CharArray): ByteArray {
        checkVersion(header)
        val wrapKey = passwordWrapKey(masterPassword, header.kdf)
        try {
            return AesGcm.decrypt(wrapKey, header.passwordWrappedKey, AAD_PASSWORD_WRAP)
        } catch (e: DecryptionException) {
            throw WrongCredentialsException()
        } finally {
            wrapKey.wipe()
        }
    }

    /**
     * @throws InvalidRecoveryKeyException if the text is not a well-formed recovery key.
     * @throws WrongCredentialsException if it is well-formed but does not belong to this vault.
     */
    fun unlockWithRecoveryKey(header: VaultHeader, recoveryKey: String): ByteArray {
        checkVersion(header)
        val recoveryBytes = RecoveryKey.parse(recoveryKey)
        val wrapKey = recoveryWrapKey(recoveryBytes)
        recoveryBytes.wipe()
        try {
            return AesGcm.decrypt(wrapKey, header.recoveryWrappedKey, AAD_RECOVERY_WRAP)
        } catch (e: DecryptionException) {
            throw WrongCredentialsException()
        } finally {
            wrapKey.wipe()
        }
    }

    /**
     * Sets a new master password. A fresh salt is generated and the current default KDF settings
     * are applied, which also upgrades vaults created with older parameters. The recovery key is unchanged.
     */
    fun changeMasterPassword(header: VaultHeader, vaultKey: ByteArray, newPassword: CharArray): VaultHeader {
        val kdf = newVaultKdf.toParams(secureRandomBytes(SALT_SIZE_BYTES))
        return VaultHeader(
            version = VaultHeader.CURRENT_VERSION,
            kdf = kdf,
            passwordWrappedKey = wrapWithPassword(kdf, newPassword, vaultKey),
            recoveryWrappedKey = header.recoveryWrappedKey,
        )
    }

    class NewRecovery(val header: VaultHeader, /** Shown to the user once. Not stored anywhere. */ val recoveryKey: String)

    /**
     * Replaces the recovery key: the vault key is wrapped under a brand-new recovery key and the old one stops
     * working. Everything else (password, salt, items) is unchanged, so this is safe to run at any time.
     */
    fun regenerateRecoveryKey(header: VaultHeader, vaultKey: ByteArray): NewRecovery {
        val recoveryBytes = RecoveryKey.generate()
        try {
            val newHeader = VaultHeader(
                version = header.version,
                kdf = header.kdf,
                passwordWrappedKey = header.passwordWrappedKey,
                recoveryWrappedKey = wrapWithRecovery(recoveryBytes, vaultKey),
            )
            return NewRecovery(newHeader, RecoveryKey.format(recoveryBytes))
        } finally {
            recoveryBytes.wipe()
        }
    }

    /** "Forgot password" flow: unlock with the recovery key and immediately set a new master password. */
    fun resetMasterPasswordWithRecoveryKey(
        header: VaultHeader,
        recoveryKey: String,
        newPassword: CharArray,
    ): UnlockedVault {
        val vaultKey = unlockWithRecoveryKey(header, recoveryKey)
        return UnlockedVault(changeMasterPassword(header, vaultKey, newPassword), vaultKey)
    }

    fun encryptItem(vaultKey: ByteArray, itemId: String, plaintext: ByteArray): EncryptedBlob =
        AesGcm.encrypt(vaultKey, plaintext, itemAad(itemId))

    /** @throws DecryptionException if the data was modified or belongs to a different item id. */
    fun decryptItem(vaultKey: ByteArray, itemId: String, blob: EncryptedBlob): ByteArray =
        AesGcm.decrypt(vaultKey, blob, itemAad(itemId))

    // ---- internals ----

    private fun wrapWithPassword(kdf: KdfParams, password: CharArray, vaultKey: ByteArray): EncryptedBlob {
        val wrapKey = passwordWrapKey(password, kdf)
        try {
            return AesGcm.encrypt(wrapKey, vaultKey, AAD_PASSWORD_WRAP)
        } finally {
            wrapKey.wipe()
        }
    }

    private fun wrapWithRecovery(recoveryBytes: ByteArray, vaultKey: ByteArray): EncryptedBlob {
        val wrapKey = recoveryWrapKey(recoveryBytes)
        try {
            return AesGcm.encrypt(wrapKey, vaultKey, AAD_RECOVERY_WRAP)
        } finally {
            wrapKey.wipe()
        }
    }

    private fun passwordWrapKey(password: CharArray, params: KdfParams): ByteArray =
        passwordSubKey(password, params, INFO_PASSWORD_WRAP)

    private fun passwordSubKey(password: CharArray, params: KdfParams, info: ByteArray): ByteArray {
        validate(params)
        val deriver = deriversByAlgorithm.getValue(params.algorithm)
        val normalized = normalize(password)
        val masterKey = try {
            deriver.derive(normalized, params)
        } finally {
            normalized.wipe()
        }
        try {
            return Hkdf.derive(masterKey, null, info, AesGcm.KEY_SIZE_BYTES)
        } finally {
            masterKey.wipe()
        }
    }

    private fun recoveryWrapKey(recoveryBytes: ByteArray): ByteArray =
        Hkdf.derive(recoveryBytes, null, INFO_RECOVERY_WRAP, AesGcm.KEY_SIZE_BYTES)

    /**
     * Unicode NFKC normalization so the same password typed on different keyboards or platforms
     * (for example composed vs decomposed accents) derives the same key. The intermediate String
     * cannot be wiped; this is a known limitation of the JVM.
     */
    private fun normalize(password: CharArray): CharArray =
        Normalizer.normalize(CharBuffer.wrap(password), Normalizer.Form.NFKC).toCharArray()

    private fun checkVersion(header: VaultHeader) {
        if (header.version != VaultHeader.CURRENT_VERSION) {
            throw VaultFormatException("Unsupported vault version ${header.version}")
        }
    }

    private fun validate(p: KdfParams) {
        if (p.salt.size < SALT_SIZE_BYTES) throw VaultFormatException("Salt is too short")
        if (p.parallelism !in 1..8) throw VaultFormatException("Invalid parallelism")
        when (p.algorithm) {
            Kdf.ARGON2ID -> {
                if (p.iterations !in 1..MAX_ARGON2_ITERATIONS || p.memoryKiB !in 8..MAX_ARGON2_MEMORY_KIB) {
                    throw VaultFormatException("Argon2 parameters are out of range")
                }
                if (p.iterations < minimums.argon2Iterations || p.memoryKiB < minimums.argon2MemoryKiB) {
                    throw VaultFormatException("Argon2 parameters are below the allowed minimum")
                }
            }
            Kdf.PBKDF2_SHA256 -> {
                if (p.iterations !in 1..MAX_PBKDF2_ITERATIONS) {
                    throw VaultFormatException("PBKDF2 parameters are out of range")
                }
                if (p.iterations < minimums.pbkdf2Iterations) {
                    throw VaultFormatException("PBKDF2 iterations are below the allowed minimum")
                }
            }
            else -> throw VaultFormatException("Unsupported key derivation: ${p.algorithm}")
        }
        if (p.algorithm !in deriversByAlgorithm) {
            throw VaultFormatException("No implementation available for ${p.algorithm}")
        }
    }

    private fun itemAad(itemId: String) = AAD_ITEM_PREFIX + itemId.toByteArray(Charsets.UTF_8)

    private companion object {
        const val SALT_SIZE_BYTES = 16
        const val MAX_ARGON2_ITERATIONS = 64
        const val MAX_ARGON2_MEMORY_KIB = 1024 * 1024 // 1 GiB, stops a corrupt header from exhausting memory
        const val MAX_PBKDF2_ITERATIONS = 5_000_000

        val INFO_PASSWORD_WRAP = "passvault/v1/hkdf/password-wrap".toByteArray()
        val INFO_RECOVERY_WRAP = "passvault/v1/hkdf/recovery-wrap".toByteArray()
        val AAD_PASSWORD_WRAP = "passvault/v1/aad/vault-key-by-password".toByteArray()
        val AAD_RECOVERY_WRAP = "passvault/v1/aad/vault-key-by-recovery".toByteArray()
        val AAD_ITEM_PREFIX = "passvault/v1/aad/item/".toByteArray()
    }
}
