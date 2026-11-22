package com.ritvik.passvault.crypto

/**
 * A deliberately simple strength check for the master password. It rewards length and variety and
 * rejects repetitive passwords, but it is a rough guide, not a replacement for a dictionary-based
 * estimator such as zxcvbn.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 12

    enum class Strength { TOO_SHORT, WEAK, FAIR, STRONG }

    fun evaluate(password: CharArray): Strength {
        if (password.size < MIN_LENGTH) return Strength.TOO_SHORT
        if (password.toSet().size < 5) return Strength.WEAK

        val classes = listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() },
        ).count { it }

        return when {
            password.size >= 20 -> Strength.STRONG
            password.size >= 14 && classes >= 3 -> Strength.STRONG
            classes >= 2 -> Strength.FAIR
            else -> Strength.WEAK
        }
    }

    fun isAcceptable(password: CharArray) = password.size >= MIN_LENGTH
}
