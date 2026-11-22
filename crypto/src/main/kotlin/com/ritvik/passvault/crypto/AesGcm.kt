package com.ritvik.passvault.crypto

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A nonce plus the AES-GCM output (ciphertext followed by the 16-byte authentication tag). */
class EncryptedBlob(val nonce: ByteArray, val ciphertext: ByteArray)

/** Thrown when authentication fails: wrong key, wrong associated data, or tampered data. */
class DecryptionException(cause: Throwable? = null) : Exception("Decryption failed", cause)

/**
 * AES-256-GCM with a fresh random 96-bit nonce for every encryption.
 *
 * [aad] (associated data) is authenticated but not encrypted. We use it to bind a ciphertext to
 * its purpose or its item id, so an attacker cannot swap blobs between items or contexts.
 */
object AesGcm {
    const val KEY_SIZE_BYTES = 32
    const val NONCE_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): EncryptedBlob {
        require(key.size == KEY_SIZE_BYTES) { "AES-256 key must be $KEY_SIZE_BYTES bytes" }
        val nonce = secureRandomBytes(NONCE_SIZE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        cipher.updateAAD(aad)
        return EncryptedBlob(nonce, cipher.doFinal(plaintext))
    }

    fun decrypt(key: ByteArray, blob: EncryptedBlob, aad: ByteArray): ByteArray {
        require(key.size == KEY_SIZE_BYTES) { "AES-256 key must be $KEY_SIZE_BYTES bytes" }
        if (blob.nonce.size != NONCE_SIZE_BYTES) throw DecryptionException()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, blob.nonce))
            cipher.updateAAD(aad)
            return cipher.doFinal(blob.ciphertext)
        } catch (e: GeneralSecurityException) {
            throw DecryptionException(e)
        }
    }
}
