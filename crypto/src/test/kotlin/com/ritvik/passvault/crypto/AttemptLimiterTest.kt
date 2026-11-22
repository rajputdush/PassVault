package com.ritvik.passvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptLimiterTest {
    private class FakeStore : AttemptStore {
        override var failures = 0
        override var lockedUntilMs = 0L
    }

    private var clock = 1_000_000L
    private val store = FakeStore()
    private val limiter = AttemptLimiter(store) { clock }

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
        clock += 10_000
        assertEquals(20_000L, limiter.remainingLockMs())
        clock += 20_000
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
        val restarted = AttemptLimiter(store) { clock }
        assertEquals(30_000L, restarted.remainingLockMs())
    }

    @Test
    fun clockSetFarBackCannotLockOutLongerThanOneHour() {
        repeat(9) { limiter.recordFailure() }
        clock -= 365L * 24 * 60 * 60_000 // user (or thief) sets the date back a year
        assertTrue(limiter.remainingLockMs() <= AttemptLimiter.MAX_DELAY_MS)
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
}
