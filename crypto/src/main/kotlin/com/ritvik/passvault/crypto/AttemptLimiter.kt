package com.ritvik.passvault.crypto

/** Where the failure count and lock-out state are kept between app launches. */
interface AttemptStore {
    var failures: Int
    /** Wall-clock end of the wait. Only used for waits recorded by older versions. */
    var lockedUntilMs: Long
    /** Length of the current wait, and where the monotonic clock stood when it started or was restarted. */
    var lockDelayMs: Long
    var lockStartElapsedMs: Long
    /** Which device start the monotonic clock values belong to (it restarts from zero at every reboot). */
    var lockBootId: Int
}

/**
 * Slows down password guessing. The first few mistakes are free (typos happen), then the wait grows:
 *
 *   failures 1-4: none, 5: 30 s, 6: 1 min, 7: 5 min, 8: 15 min, 9 and more: 1 hour
 *
 * The count resets after a successful unlock. Argon2id already makes each guess cost about a second;
 * this limit stops a thief from trying thousands of guesses on the phone itself.
 *
 * The wait is measured with the monotonic clock ([elapsed], SystemClock.elapsedRealtime on Android), which changing
 * the phone's date does not affect. That clock restarts at a reboot; when [bootId] shows the phone restarted, the
 * wait starts over in full, so rebooting never shortens it. The wait is never longer than one hour.
 */
class AttemptLimiter(
    private val store: AttemptStore,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val elapsed: () -> Long = { System.nanoTime() / 1_000_000L },
    private val bootId: () -> Int = { 0 },
) {
    /** Milliseconds the user must still wait, or 0 if an attempt is allowed. */
    fun remainingLockMs(): Long {
        val delay = store.lockDelayMs
        if (delay <= 0L) {
            // A wait recorded by an older version of the app, measured on the wall clock.
            return (store.lockedUntilMs - now()).coerceIn(0L, MAX_DELAY_MS)
        }
        val e = elapsed()
        if (store.lockBootId != bootId() || e < store.lockStartElapsedMs) {
            // The phone restarted since the wait began, so the monotonic clock cannot say how much passed.
            // Start the wait again in full rather than trusting the (changeable) wall clock.
            store.lockBootId = bootId()
            store.lockStartElapsedMs = e
        }
        return (delay - (e - store.lockStartElapsedMs)).coerceIn(0L, MAX_DELAY_MS)
    }

    /** Records a wrong password and returns the wait (in ms) that now applies, 0 if none. */
    fun recordFailure(): Long {
        val failures = store.failures + 1
        val delay = delayAfterFailures(failures)
        store.failures = failures
        store.lockedUntilMs = if (delay > 0) now() + delay else 0L
        store.lockDelayMs = delay
        store.lockStartElapsedMs = elapsed()
        store.lockBootId = bootId()
        return delay
    }

    fun recordSuccess() {
        store.failures = 0
        store.lockedUntilMs = 0L
        store.lockDelayMs = 0L
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
