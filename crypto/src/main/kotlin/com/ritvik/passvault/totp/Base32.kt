package com.ritvik.passvault.totp

/** RFC 4648 Base32, the encoding authenticator secrets are shared in. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    /**
     * Decodes a secret as people paste it: lower or upper case, with spaces or dashes, with or without "=" padding.
     * Returns null if it contains anything that is not Base32, or is empty.
     */
    fun decode(input: String): ByteArray? {
        val clean = input.filter { it != ' ' && it != '-' && it != '\t' && it != '\n' && it != '\r' }
            .trimEnd('=')
            .uppercase()
        if (clean.isEmpty()) return null
        val out = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                if (index < out.size) out[index++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out
    }
}
