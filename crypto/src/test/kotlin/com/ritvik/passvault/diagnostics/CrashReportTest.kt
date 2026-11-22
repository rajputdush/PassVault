package com.ritvik.passvault.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    @Test fun leavesOutMessagesButKeepsLocations() {
        val error = IllegalStateException("hunter2 is my password", RuntimeException("secret@example.com"))
        val text = CrashReport.format(error, "1.0", "14", "Pixel", 1L)
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("secret@example.com"))
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("Caused by: java.lang.RuntimeException"))
        assertTrue(text.contains("CrashReportTest"))
        assertTrue(text.contains("App version: 1.0"))
    }

    @Test fun limitsLength() {
        var e: Throwable = RuntimeException()
        repeat(20) { e = RuntimeException(e) }
        val lines = CrashReport.format(e, "1", "1", "d", 0).lines().count { it.startsWith("Error") || it.startsWith("Caused") }
        assertTrue(lines <= 4)
    }
}
