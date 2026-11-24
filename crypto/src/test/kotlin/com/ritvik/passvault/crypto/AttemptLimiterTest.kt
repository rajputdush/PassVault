package com.ritvik.passvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptLimiterTest {
    private class FakeStore : AttemptStore {
        override var failures = 0
        override var lockedUntilMs = 0L
        override var lockDelayMs = 0L
        override var lockStartElapsedMs = 0L
        override var lockBootId = 0
    }

    private var clock = 1_000_000L
    private var mono = 5_000L
    private var boot = 1
    private val store = FakeStore()
    private val limiter = AttemptLimiter(store, { clock }, { mono }, { boot })

    @Test
    fun firstFourFailuresAreFree() {
        repeat(4) {
            assertEquals(0L, limiter.recordFailure())
            assertEquals(0L, limiter.remainingLockMs())
        }
    }

    @Test
    fun delayGrowsAfterTheFifthFailure() {
        repeat(4) { limiter.recordFailure() }
        assertEquals(30_000L, limiter.recordFailure())
        assertEquals(30_000L, limiter.remainingLockMs())
        assertEquals(60_000L, limiter.recordFailure())
        assertEquals(5 * 60_000L, limiter.recordFailure())
        assertEquals(15 * 60_000L, limiter.recordFailure())
        assertEquals(60 * 60_000L, limiter.recordFailure())
        assertEquals(60 * 60_000L, limiter.recordFailure()) // stays at one hour
    }

    @Test
    fun lockExpiresAsTimePasses() {
        repeat(5) { limiter.recordFailure() }
        mono += 10_000
        assertEquals(20_000L, limiter.remainingLockMs())
        mono += 20_000
        assertEquals(0L, limiter.remainingLockMs())
    }

    @Test
    fun successResetsEverything() {
        repeat(6) { limiter.recordFailure() }
        limiter.recordSuccess()
        assertEquals(0L, limiter.remainingLockMs())
        assertEquals(0, store.failures)
        repeat(4) { assertEquals(0L, limiter.recordFailure()) }
    }

    @Test
    fun stateSurvivesANewLimiterOnTheSameStore() {
        repeat(5) { limiter.recordFailure() }
        val restarted = AttemptLimiter(store, { clock }, { mono }, { boot })
        assertEquals(30_000L, restarted.remainingLockMs())
    }

    @Test
    fun clockSetFarBackCannotLockOutLongerThanOneHour() {
        repeat(9) { limiter.recordFailure() }
        clock -= 365L * 24 * 60 * 60_000 // user (or thief) sets the date back a year
        assertTrue(limiter.remainingLockMs() <= AttemptLimiter.MAX_DELAY_MS)
    }

    @Test
    fun settingTheDateForwardDoesNotShortenTheWait() {
        repeat(5) { limiter.recordFailure() }
        clock += 24L * 60 * 60_000 // thief moves the date a day ahead
        assertEquals(30_000L, limiter.remainingLockMs())
    }

    @Test
    fun rebootRestartsTheWaitInsteadOfShorteningIt() {
        repeat(6) { limiter.recordFailure() } // 1 minute wait
        mono += 40_000
        boot = 2
        mono = 1_000 // monotonic clock starts again after the reboot
        clock += 24L * 60 * 60_000 // and the date is moved forward as well
        assertEquals(60_000L, limiter.remainingLockMs())
        mono += 15_000
        assertEquals(45_000L, limiter.remainingLockMs())
    }

    @Test
    fun waitRecordedByAnOlderVersionStillWorks() {
        store.failures = 5
        store.lockedUntilMs = clock + 30_000L // no monotonic data stored
        assertEquals(30_000L, limiter.remainingLockMs())
    }

    @Test
    fun describeRoundsUp() {
        assertEquals("1 second", AttemptLimiter.describe(1))
        assertEquals("30 seconds", AttemptLimiter.describe(30_000))
        assertEquals("30 seconds", AttemptLimiter.describe(29_001))
        assertEquals("1 minute", AttemptLimiter.describe(60_000))
        assertEquals("2 minutes", AttemptLimiter.describe(61_000))
        assertEquals("15 minutes", AttemptLimiter.describe(15 * 60_000L))
        assertEquals("60 minutes", AttemptLimiter.describe(60 * 60_000L))
    }

    @Test
    fun attemptsLeftCountsDownToTheFirstWait() {
        assertEquals(5, limiter.attemptsLeft())
        limiter.recordFailure()
        assertEquals(4, limiter.attemptsLeft())
        repeat(3) { limiter.recordFailure() }
        assertEquals(1, limiter.attemptsLeft())
        limiter.recordFailure()
        assertEquals(0, limiter.attemptsLeft())
        assertEquals(60_000L, limiter.nextDelayMs())
        limiter.recordSuccess()
        assertEquals(5, limiter.attemptsLeft())
    }

    @Test
    fun clockFormatsACountdown() {
        assertEquals("0:27", AttemptLimiter.clock(26_100))
        assertEquals("0:00", AttemptLimiter.clock(0))
        assertEquals("1:00", AttemptLimiter.clock(60_000))
        assertEquals("15:00", AttemptLimiter.clock(15 * 60_000L))
        assertEquals("1:00:00", AttemptLimiter.clock(60 * 60_000L))
    }
}
