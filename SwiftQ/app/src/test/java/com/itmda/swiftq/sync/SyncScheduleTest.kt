package com.itmda.swiftq.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncScheduleTest {
    @Test fun futureDueTimeBecomesTheDelayUntilThen() {
        assertEquals(45_000L, SyncSchedule.delayMs(dueAtMs = 100_000, nowMs = 55_000))
    }

    @Test fun anOverdueRowNeverCausesATightLoop() {
        assertEquals(SyncSchedule.MIN_GAP_MS, SyncSchedule.delayMs(dueAtMs = 1_000, nowMs = 50_000))
        assertEquals(SyncSchedule.MIN_GAP_MS, SyncSchedule.delayMs(dueAtMs = 50_000, nowMs = 50_000))
    }

    @Test fun theHourlyStepOfTable12IsKept() {
        val now = 10_000L
        val decision = BackoffPolicy.afterFailure(attemptsMade = 5, createdAtMs = now, nowMs = now) as RetryDecision.RetryAt
        assertEquals(3_600_000L, SyncSchedule.delayMs(decision.epochMs, now))
    }
}
