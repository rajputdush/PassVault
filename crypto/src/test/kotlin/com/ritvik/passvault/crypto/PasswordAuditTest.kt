package com.ritvik.passvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordAuditTest {
    private fun rating(p: String) = SitePasswordStrength.assess(p).rating

    @Test
    fun commonPasswordsAreVeryWeak() {
        for (p in listOf("password", "123456", "Qwerty", "P@ssw0rd", "Password123!", "iloveyou2020", "letmein!")) {
            assertEquals("expected very weak: $p", Rating.VERY_WEAK, rating(p))
        }
    }

    @Test
    fun shortPasswordsAreAtMostWeak() {
        assertTrue(rating("aB3$") <= Rating.WEAK)
        assertTrue(rating("x9!Qz") <= Rating.WEAK)
        assertTrue(SitePasswordStrength.assess("x9!Qz").reasons.contains("Shorter than 8 characters"))
    }

    @Test
    fun repeatedAndSequentialPasswordsAreWeak() {
        assertEquals(Rating.VERY_WEAK, rating("aaaaaaaaaaaa"))
        assertTrue(rating("abcdefghijkl") <= Rating.WEAK)
        assertTrue(SitePasswordStrength.assess("abcdefghijkl").reasons.contains("Contains sequences like abc or 123"))
    }

    @Test
    fun mediumPasswordIsFair() {
        assertEquals(Rating.FAIR, rating("q7Lm2xPz"))
    }

    @Test
    fun passphrasesAndGeneratedPasswordsAreStrong() {
        assertEquals(Rating.STRONG, rating("correct horse battery staple"))
        repeat(200) { assertEquals(Rating.STRONG, rating(PasswordGenerator.generate(20))) }
    }

    @Test
    fun emptyPasswordIsVeryWeak() {
        assertEquals(Rating.VERY_WEAK, rating(""))
    }

    @Test
    fun auditFindsWeakReusedEmptyAndOld() {
        val now = 10L * 365 * 24 * 60 * 60 * 1000
        val recent = now - 1_000
        val longAgo = now - 400L * 24 * 60 * 60 * 1000
        val strong = "Zr8!kQ2#vLp9@xWm"
        val entries = listOf(
            AuditEntry("1", "Gmail", strong, recent),
            AuditEntry("2", "Bank", strong, recent),
            AuditEntry("3", "Forum", "password", recent),
            AuditEntry("4", "Blank", "", recent),
            AuditEntry("5", "Old site", "Vn4%tY7&hGd2", longAgo),
            AuditEntry("6", "Unique", "Hs3^pW9*dKe5", recent),
        )
        val result = PasswordAudit.analyze(entries, now)

        assertEquals(listOf("3"), result.weak.map { it.id })
        assertEquals(1, result.reused.size)
        assertEquals(setOf("1", "2"), result.reused[0].ids.toSet())
        assertEquals(listOf("Gmail", "Bank"), result.reused[0].titles)
        assertEquals(listOf("4"), result.empty.map { it.id })
        assertEquals(listOf("5"), result.old.map { it.id })
        assertEquals(setOf("1", "2", "3", "4"), result.attentionIds)
    }

    @Test
    fun emptyPasswordsAreNotCountedAsReused() {
        val result = PasswordAudit.analyze(
            listOf(AuditEntry("1", "A", "", 0), AuditEntry("2", "B", "", 0)),
            nowMs = 1_000_000,
        )
        assertTrue(result.reused.isEmpty())
        assertEquals(2, result.empty.size)
    }

    @Test
    fun itemsWithNoTimestampAreNotCalledOld() {
        val result = PasswordAudit.analyze(listOf(AuditEntry("1", "A", "Zr8!kQ2#vLp9@xWm", 0)), nowMs = 9_999_999_999_999)
        assertFalse(result.old.isNotEmpty())
    }

    @Test
    fun cleanVaultHasNoAttentionItems() {
        val result = PasswordAudit.analyze(
            listOf(
                AuditEntry("1", "A", "Zr8!kQ2#vLp9@xWm", 1_000),
                AuditEntry("2", "B", "Hs3^pW9*dKe5Tn6&", 1_000),
            ),
            nowMs = 2_000,
        )
        assertTrue(result.attentionIds.isEmpty())
    }
}
