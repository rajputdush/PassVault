package com.ritvik.passvault.crypto

/** The vault file is unreadable, uses an unsupported version, or has unsafe parameters. */
class VaultFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The master password or recovery key did not unlock the vault. */
class WrongCredentialsException : Exception("Wrong master password or recovery key")

/**
 * Everything needed to unlock a vault, stored on disk. It contains no secrets in the clear:
 * the vault key is stored twice, once wrapped by a key derived from the master password and
 * once wrapped by a key derived from the recovery key.
 */
class VaultHeader(
    val version: Int,
    val kdf: KdfParams,
    val passwordWrappedKey: EncryptedBlob,
    val recoveryWrappedKey: EncryptedBlob,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
