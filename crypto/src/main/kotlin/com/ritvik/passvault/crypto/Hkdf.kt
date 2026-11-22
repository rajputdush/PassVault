package com.ritvik.passvault.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HKDF-SHA256 (RFC 5869). Used to derive independent keys from one secret, one per purpose. */
object Hkdf {
    private const val HMAC = "HmacSHA256"
    private const val HASH_LEN = 32

    fun derive(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LEN)) { "Invalid HKDF output length" }

        // Extract
        val prk = hmac(if (salt == null || salt.isEmpty()) ByteArray(HASH_LEN) else salt, ikm)

        // Expand
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var position = 0
        var counter = 1
        try {
            while (position < length) {
                val mac = Mac.getInstance(HMAC)
                mac.init(SecretKeySpec(prk, HMAC))
                mac.update(previous)
                mac.update(info)
                mac.update(counter.toByte())
                previous = mac.doFinal()
                val n = minOf(previous.size, length - position)
                previous.copyInto(out, position, 0, n)
                position += n
                counter++
            }
        } finally {
            prk.wipe()
            previous.wipe()
        }
        return out
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(key, HMAC))
        return mac.doFinal(data)
    }
}
