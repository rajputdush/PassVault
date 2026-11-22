package com.ritvik.passvault.crypto

/** Where the failure count and lock-out time are kept between app launches. */
interface AttemptStore {
    var failures: Int
    var lockedUntilMs: Long
}

/**
 * Slows down password guessing. The first few mistakes are free (typos happen), then the wait grows:
 *
 *   failures 1-4: none, 5: 30 s, 6: 1 min, 7: 5 min, 8: 15 min, 9 and more: 1 hour
 *
 * The count resets after a successful unlock. Argon2id already makes each guess cost about a second;
 * this limit stops a thief from trying thousands of guesses on the phone itself.
 * Limitation: it uses the wall clock, so a thief who can change the phone's date could shorten a wait.
 * The remaining wait is capped at one hour so that setting the clock far back cannot lock the owner out for years.
 */
class AttemptLimiter(
    private val store: AttemptStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Milliseconds the user must still wait, or 0 if an attempt is allowed. */
    fun remainingLockMs(): Long {
        val remaining = store.lockedUntilMs - now()
        return remaining.coerceIn(0L, MAX_DELAY_MS)
    }

    /** Records a wrong password and returns the wait (in ms) that now applies, 0 if none. */
    fun recordFailure(): Long {
        val failures = store.failures + 1
        val delay = delayAfterFailures(failures)
        store.failures = failures
        store.lockedUntilMs = if (delay > 0) now() + delay else 0L
        return delay
    }

    fun recordSuccess() {
        store.failures = 0
        store.lockedUntilMs = 0L
    }

    companion object {
        const val MAX_DELAY_MS = 60 * 60_000L

        fun delayAfterFailures(failures: Int): Long = when {
            failures < 5 -> 0L
            failures == 5 -> 30_000L
            failures == 6 -> 60_000L
            failures == 7 -> 5 * 60_000L
            failures == 8 -> 15 * 60_000L
            else -> MAX_DELAY_MS
        }

        /** Human-readable wait, rounded up: "30 seconds", "1 minute", "15 minutes". */
        fun describe(ms: Long): String {
            val seconds = (ms + 999) / 1000
            if (seconds < 60) return "$seconds second" + if (seconds == 1L) "" else "s"
            val minutes = (seconds + 59) / 60
            return "$minutes minute" + if (minutes == 1L) "" else "s"
        }
    }
}
