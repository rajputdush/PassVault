package com.ritvik.passvault.autofill

/**
 * A login the user asked Android to save while PassVault was locked. It waits here, in memory only, while
 * PassVault asks the user to unlock, and is dropped as soon as it is saved, the user backs out, or a few minutes pass.
 * It is never written to disk and never put in an intent.
 */
class PendingSave(val login: ParsedForm) {
    private val createdAt = System.currentTimeMillis()

    val expired: Boolean get() = System.currentTimeMillis() - createdAt > MAX_AGE_MS

    companion object {
        const val MAX_AGE_MS = 5 * 60 * 1000L
    }
}
