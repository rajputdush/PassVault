package com.ritvik.passvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmTest {
    private val key = secureRandomBytes(32)
    private val aad = "context".toByteArray()
    private val plaintext = "hunter2 - correct horse battery staple".toByteArray()

    @Test
    fun roundTrip() {
        val blob = AesGcm.encrypt(key, plaintext, aad)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, blob, aad))
    }

    @Test
    fun emptyPlaintextRoundTrips() {
        val blob = AesGcm.encrypt(key, ByteArray(0), aad)
        assertArrayEquals(ByteArray(0), AesGcm.decrypt(key, blob, aad))
    }

    @Test
    fun nonceIsDifferentEveryTime() {
        val a = AesGcm.encrypt(key, plaintext, aad)
        val b = AesGcm.encrypt(key, plaintext, aad)
        assertFalse(a.nonce.contentEquals(b.nonce))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
    }

    @Test
    fun wrongKeyFails() {
        val blob = AesGcm.encrypt(key, plaintext, aad)
        assertThrows(DecryptionException::class.java) { AesGcm.decrypt(secureRandomBytes(32), blob, aad) }
    }

    @Test
    fun wrongAssociatedDataFails() {
        val blob = AesGcm.encrypt(key, plaintext, aad)
        assertThrows(DecryptionException::class.java) { AesGcm.decrypt(key, blob, "other".toByteArray()) }
    }

    @Test
    fun tamperedCiphertextFails() {
        val blob = AesGcm.encrypt(key, plaintext, aad)
        blob.ciphertext[0] = (blob.ciphertext[0].toInt() xor 1).toByte()
        assertThrows(DecryptionException::class.java) { AesGcm.decrypt(key, blob, aad) }
    }

    @Test
    fun tamperedTagFails() {
        val blob = AesGcm.encrypt(key, plaintext, aad)
        val last = blob.ciphertext.size - 1
        blob.ciphertext[last] = (blob.ciphertext[last].toInt() xor 1).toByte()
        assertThrows(DecryptionException::class.java) { AesGcm.decrypt(key, blob, aad) }
    }

    @Test
    fun badNonceLengthFails() {
        val blob = AesGcm.encrypt(key, plaintext, aad)
        val broken = EncryptedBlob(ByteArray(8), blob.ciphertext)
        assertThrows(DecryptionException::class.java) { AesGcm.decrypt(key, broken, aad) }
    }
}
