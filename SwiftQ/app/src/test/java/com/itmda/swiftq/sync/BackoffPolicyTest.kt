package com.itmda.swiftq.sync

import com.itmda.swiftq.RandomKeys
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffPolicyTest {
    @Test fun scheduleMatchesTable12() {
        assertNull(nextAttemptDelay(0))
        assertEquals(Duration.ZERO, nextAttemptDelay(1))
        assertEquals(5.seconds, nextAttemptDelay(2))
        assertEquals(15.seconds, nextAttemptDelay(3))
        assertEquals(45.seconds, nextAttemptDelay(4))
        assertEquals(135.seconds, nextAttemptDelay(5))
        assertEquals(1.hours, nextAttemptDelay(6))
        assertEquals(1.hours, nextAttemptDelay(40))
    }

    @Test fun afterFailureUsesTheNextAttemptsDelay() {
        val d = BackoffPolicy.afterFailure(attemptsMade = 1, createdAtMs = 0, nowMs = 1_000)
        assertEquals(RetryDecision.RetryAt(1_000 + 5_000), d)
    }

    @Test fun retryAfterWinsWhenLongerButNotWhenShorter() {
        assertEquals(RetryDecision.RetryAt(60_000), BackoffPolicy.afterFailure(1, 0, 0, retryAfterSeconds = 60))
        assertEquals(RetryDecision.RetryAt(5_000), BackoffPolicy.afterFailure(1, 0, 0, retryAfterSeconds = 2))
    }

    @Test fun expiresAfter24Hours() {
        val day = 24.hours.inWholeMilliseconds
        assertTrue(BackoffPolicy.afterFailure(8, createdAtMs = 0, nowMs = day) is RetryDecision.Expire)
        assertTrue(BackoffPolicy.afterFailure(8, createdAtMs = 0, nowMs = day - 2 * 3_600_000L) is RetryDecision.RetryAt)
        assertTrue(BackoffPolicy.isExpired(0, day + 1))
        assertTrue(!BackoffPolicy.isExpired(0, day))
    }

    @Test fun keysAreVersion4AndNeverCollide() {
        val factory = UuidV4KeyFactory()
        val keys = List(10_000) { factory.newKey() }
        keys.forEach { assertEquals(4, UUID.fromString(it).version()) }
        assertEquals(keys.size, keys.toSet().size)
        assertNotEquals(RandomKeys().newKey(), RandomKeys().newKey())
    }
}
