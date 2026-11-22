package com.ritvik.passvault.crypto

class InvalidRecoveryKeyException(message: String) : Exception(message)

/**
 * The recovery key is 256 random bits, shown to the user once as 13 groups of 4 characters
 * (RFC 4648 Base32), e.g. ABCD-EFGH-... It can unlock the vault if the master password is forgotten.
 */
object RecoveryKey {
    const val SIZE_BYTES = 32
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val ENCODED_LENGTH = 52
    private const val GROUP_SIZE = 4

    fun generate(): ByteArray = secureRandomBytes(SIZE_BYTES)

    fun format(bytes: ByteArray): String {
        require(bytes.size == SIZE_BYTES) { "Recovery key must be $SIZE_BYTES bytes" }
        val encoded = StringBuilder(ENCODED_LENGTH)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                encoded.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) encoded.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return encoded.chunked(GROUP_SIZE).joinToString("-")
    }

    /**
     * Accepts lowercase, spaces and missing dashes. Also fixes the two look-alike digits that
     * cannot appear in the alphabet (0 becomes O, 8 becomes B).
     */
    fun parse(text: String): ByteArray {
        val cleaned = buildString {
            for (c in text) {
                when {
                    c == '-' || c.isWhitespace() -> {}
                    c == '0' -> append('O')
                    c == '8' -> append('B')
                    else -> append(c.uppercaseChar())
                }
            }
        }
        if (cleaned.length != ENCODED_LENGTH) throw InvalidRecoveryKeyException("Recovery key has the wrong length")

        val out = ByteArray(SIZE_BYTES)
        var written = 0
        var buffer = 0
        var bits = 0
        for (c in cleaned) {
            val value = ALPHABET.indexOf(c)
            if (value < 0) throw InvalidRecoveryKeyException("Recovery key contains an invalid character")
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out[written++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        if (written != SIZE_BYTES || buffer != 0) throw InvalidRecoveryKeyException("Recovery key is not valid")
        return out
    }
}
