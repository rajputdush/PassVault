package com.ritvik.passvault.crypto

import java.security.MessageDigest

/**
 * Helpers for the Have I Been Pwned "Pwned Passwords" range API, which uses k-anonymity:
 * the app sends only the first 5 characters of the password's SHA-1 hash, receives every known
 * hash suffix that starts with them, and does the final comparison on the phone. The password and
 * the full hash never leave the device.
 */
object BreachCheck {
    const val PREFIX_LENGTH = 5

    /** Upper-case hex SHA-1, as the API expects. */
    fun sha1Hex(password: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }

    fun prefix(hash: String) = hash.substring(0, PREFIX_LENGTH)

    fun suffix(hash: String) = hash.substring(PREFIX_LENGTH)

    /**
     * Looks for [suffix] in the API response (lines of "SUFFIX:COUNT"). Returns how many times the password
     * appears in known breaches, or 0 if it was not found. Lines with a count of 0 are padding the
     * service adds on request to hide the real response size, so they never count as a match.
     */
    fun countInRange(responseBody: String, suffix: String): Long {
        for (line in responseBody.lineSequence()) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            if (line.substring(0, separator).trim().equals(suffix, ignoreCase = true)) {
                val count = line.substring(separator + 1).trim().toLongOrNull() ?: continue // skip malformed lines
                return count.coerceAtLeast(0L)
            }
        }
        return 0L
    }
}
