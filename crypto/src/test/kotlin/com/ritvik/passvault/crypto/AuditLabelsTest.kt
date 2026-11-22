package com.ritvik.passvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class AuditLabelsTest {
    @Test
    fun labelsMatchTheRealReasons() {
        assertEquals("Common", AuditLabels.weakReason(SitePasswordStrength.assess("password").reasons))
        assertEquals("Short", AuditLabels.weakReason(SitePasswordStrength.assess("a1B!").reasons))
        assertEquals("Digits only", AuditLabels.weakReason(SitePasswordStrength.assess("90817263549").reasons))
        assertEquals("Lowercase only", AuditLabels.weakReason(SitePasswordStrength.assess("qjzvkwbtxmpd").reasons))
    }

    @Test
    fun unknownOrMissingFallBackToWeak() {
        assertEquals("Weak", AuditLabels.weakReason(emptyList()))
        assertEquals("Weak", AuditLabels.weakReason(listOf("Something new")))
    }
}
