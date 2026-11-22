package com.ritvik.passvault.crypto

/** Very short labels for the Security check list, so a row can say why without a sentence. */
object AuditLabels {
    /**
     * One or two words for the main reason a password is weak, taken from [SitePasswordStrength]'s reasons.
     * Unknown reasons fall back to "Weak".
     */
    fun weakReason(reasons: List<String>): String {
        val first = reasons.firstOrNull()?.lowercase() ?: return "Weak"
        return when {
            "very common" in first -> "Common"
            "shorter" in first -> "Short"
            "repeated" in first -> "Repeats"
            "sequence" in first -> "Sequence"
            "only digits" in first -> "Digits only"
            "lowercase" in first -> "Lowercase only"
            "easy to guess" in first -> "Easy to guess"
            else -> "Weak"
        }
    }
}
