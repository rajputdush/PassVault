package com.ritvik.passvault.crypto

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

enum class Rating { VERY_WEAK, WEAK, FAIR, STRONG }

class Assessment(val rating: Rating, val reasons: List<String>, val bits: Double)

/**
 * A fast, offline strength estimate for passwords saved in the vault (not for the master password,
 * see [PasswordPolicy] for that). It estimates the bits of entropy from length and character variety,
 * discounts repeated characters and sequences, and flags very common passwords. It is a guide, not a
 * proof: a dictionary-based estimator such as zxcvbn would catch more patterns.
 */
object SitePasswordStrength {
    private val COMMON = setOf(
        "password", "123456", "12345678", "123456789", "1234567890", "12345", "1234567", "123123", "123321",
        "111111", "000000", "654321", "987654321", "qwerty", "qwerty123", "qwertyuiop", "asdfgh", "asdfghjkl",
        "qazwsx", "zaq12wsx", "1q2w3e4r", "abc123", "abcd1234", "iloveyou", "admin", "administrator", "welcome",
        "login", "letmein", "monkey", "dragon", "football", "baseball", "master", "sunshine", "princess",
        "trustno1", "starwars", "whatever", "shadow", "superman", "michael", "jordan", "hunter", "batman",
        "freedom", "ninja", "mustang", "access", "flower", "hello", "charlie", "donald", "696969", "changeme",
        "secret", "summer", "cheese", "computer", "internet", "pass", "test", "guest", "root", "default",
        "passw0rd", "password1", "welcome1", "p@ssword", "p@ssw0rd",
    )

    fun assess(password: String): Assessment {
        if (password.isEmpty()) return Assessment(Rating.VERY_WEAK, listOf("No password saved"), 0.0)

        val reasons = mutableListOf<String>()
        if (isCommon(password)) {
            return Assessment(Rating.VERY_WEAK, listOf("A very common password"), 0.0)
        }

        var charset = 0
        if (password.any { it.isLowerCase() }) charset += 26
        if (password.any { it.isUpperCase() }) charset += 26
        if (password.any { it.isDigit() }) charset += 10
        if (password.any { !it.isLetterOrDigit() }) charset += 33
        if (charset == 0) charset = 26 // letters outside the usual cases (for example non-Latin scripts)

        val distinct = password.toSet().size
        var effectiveLength = distinct + 0.25 * (password.length - distinct)

        var sequential = 0
        for (i in 1 until password.length) {
            if (abs(password[i].code - password[i - 1].code) == 1) sequential++
        }
        effectiveLength = max(1.0, effectiveLength - 0.5 * sequential)

        val bits = effectiveLength * (ln(charset.toDouble()) / ln(2.0))
        var rating = when {
            bits < 28 -> Rating.VERY_WEAK
            bits < 36 -> Rating.WEAK
            bits < 60 -> Rating.FAIR
            else -> Rating.STRONG
        }

        if (password.length < 8) {
            reasons += "Shorter than 8 characters"
            if (rating > Rating.WEAK) rating = Rating.WEAK
        }
        if (distinct * 2 < password.length) reasons += "Many repeated characters"
        if (sequential >= 3) reasons += "Contains sequences like abc or 123"
        if (password.all { it.isDigit() }) {
            reasons += "Only digits"
        } else if (password.all { it.isLowerCase() }) {
            reasons += "Only lowercase letters"
        }
        if (reasons.isEmpty() && rating <= Rating.WEAK) reasons += "Easy to guess"

        return Assessment(rating, reasons, bits)
    }

    private fun isCommon(password: String): Boolean {
        val lowered = password.lowercase()
        val unleeted = deLeet(lowered)
        val candidates = listOf(lowered, unleeted, stripTail(lowered), stripTail(unleeted))
        return candidates.any { it.isNotEmpty() && it in COMMON }
    }

    private fun deLeet(s: String) = buildString {
        for (c in s) {
            append(
                when (c) {
                    '0' -> 'o'
                    '1' -> 'i'
                    '3' -> 'e'
                    '4' -> 'a'
                    '5' -> 's'
                    '7' -> 't'
                    '@' -> 'a'
                    '$' -> 's'
                    else -> c
                },
            )
        }
    }

    /** "password123!" becomes "password": people often add digits or symbols at the end. */
    private fun stripTail(s: String) = s.trimEnd { !it.isLetter() }
}

class AuditEntry(val id: String, val title: String, val password: String, val updatedAt: Long)

class Finding(val id: String, val title: String, val reasons: List<String>)

class ReuseGroup(val ids: List<String>, val titles: List<String>)

class AuditResult(
    val weak: List<Finding>,
    val reused: List<ReuseGroup>,
    val empty: List<Finding>,
    val old: List<Finding>,
) {
    /** Items that need the user's attention (weak, reused or without a password). Old items are only informational. */
    val attentionIds: Set<String>
        get() = (weak.map { it.id } + empty.map { it.id } + reused.flatMap { it.ids }).toSet()
}

object PasswordAudit {
    private const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

    fun analyze(entries: List<AuditEntry>, nowMs: Long): AuditResult {
        val weak = mutableListOf<Finding>()
        val empty = mutableListOf<Finding>()
        val old = mutableListOf<Finding>()

        for (e in entries) {
            if (e.password.isEmpty()) {
                empty += Finding(e.id, e.title, listOf("No password saved"))
                continue
            }
            val assessment = SitePasswordStrength.assess(e.password)
            if (assessment.rating <= Rating.WEAK) weak += Finding(e.id, e.title, assessment.reasons)
            if (e.updatedAt > 0 && nowMs - e.updatedAt > ONE_YEAR_MS) {
                old += Finding(e.id, e.title, listOf("Not changed in over a year"))
            }
        }

        val reused = entries
            .filter { it.password.isNotEmpty() }
            .groupBy { it.password }
            .values
            .filter { it.size > 1 }
            .map { group -> ReuseGroup(group.map { it.id }, group.map { it.title }) }

        return AuditResult(weak, reused, empty, old)
    }
}
