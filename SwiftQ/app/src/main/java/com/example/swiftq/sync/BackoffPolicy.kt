package com.example.swiftq.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Retry schedule from Chapter 3, Table 12. The attempt number is 1-based:
 *
 * | attempt | delay before it |
 * |---------|-----------------|
 * | 1       | immediate       |
 * | 2       | 5 s             |
 * | 3       | 15 s            |
 * | 4       | 45 s            |
 * | 5       | 135 s           |
 * | 6+      | 1 hour          |
 *
 * Returns null for an attempt number below 1, which does not exist.
 */
fun nextAttemptDelay(attempt: Int): Duration? = when {
    attempt < 1 -> null
    attempt == 1 -> Duration.ZERO
    attempt == 2 -> 5.seconds
    attempt == 3 -> 15.seconds
    attempt == 4 -> 45.seconds
    attempt == 5 -> 135.seconds
    else -> 1.hours
}

sealed interface RetryDecision {
    /** Try again at this epoch-ms time. */
    data class RetryAt(val epochMs: Long) : RetryDecision

    /** Older than the server's 24 h idempotency window: replaying could create a duplicate, so give up. */
    data object Expire : RetryDecision
}

object BackoffPolicy {
    /** How long the server remembers an idempotency key. */
    val IDEMPOTENCY_TTL: Duration = 24.hours

    /**
     * Decides what happens after a failed attempt.
     * @param attemptsMade how many attempts have now been made (the one that just failed included)
     * @param retryAfterSeconds the server's Retry-After, if it sent one; the longer of it and the schedule wins
     */
    fun afterFailure(
        attemptsMade: Int,
        createdAtMs: Long,
        nowMs: Long,
        retryAfterSeconds: Int? = null,
    ): RetryDecision {
        val scheduled = nextAttemptDelay(attemptsMade + 1) ?: Duration.ZERO
        val serverWait = (retryAfterSeconds ?: 0).seconds
        val next = nowMs + maxOf(scheduled, serverWait).inWholeMilliseconds
        return if (next > createdAtMs + IDEMPOTENCY_TTL.inWholeMilliseconds) RetryDecision.Expire else RetryDecision.RetryAt(next)
    }

    fun isExpired(createdAtMs: Long, nowMs: Long): Boolean =
        nowMs - createdAtMs > IDEMPOTENCY_TTL.inWholeMilliseconds
}
