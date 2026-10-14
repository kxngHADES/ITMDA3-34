package com.itmda.swiftq.sync

import com.itmda.swiftq.data.local.OutboxDao
import com.itmda.swiftq.data.local.OutboxEntity
import com.itmda.swiftq.data.local.OutboxOp
import com.itmda.swiftq.data.local.OutboxState
import com.itmda.swiftq.data.repository.TicketRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sends what is waiting in the outbox. A plain suspend function called by [SyncWorker] (background), and in-process right after a join and
 * when the app comes to the foreground.
 *
 * Rules (Chapter 4.4.2):
 *  - rows go out strictly in `id` order, at most [BATCH_SIZE] at a time;
 *  - a row is marked `in_flight` before sending, and goes back to `pending` at app start if the process died;
 *  - the stored idempotency key and payload bytes are reused on every attempt;
 *  - the result decides the ticket state and the outbox state (see [apply]).
 */
class OutboxProcessor(
    private val outbox: OutboxDao,
    private val tickets: TicketRepository,
    private val transport: OutboxTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val flushLock = Mutex()

    /**
     * Call once at app start: a row left `in_flight` means the process died mid-request.
     * Under the flush lock because WorkManager can start a worker in a fresh process at the same moment, and
     * a reset must never land between another flush's `markInFlight` and its result.
     */
    suspend fun recoverAfterRestart() = flushLock.withLock {
        outbox.resetStaleInFlight(clock())
    }

    /** Sends everything that is due. Safe to call from anywhere, any number of times. */
    suspend fun flush() = flushLock.withLock {
        expireOldEntries()
        val batch = outbox.nextBatch(clock(), BATCH_SIZE)
        if (batch.isEmpty()) return@withLock
        outbox.markInFlight(batch.map { it.id }, clock())
        val outcomes = transport.send(batch)
        batch.zip(outcomes).forEach { (entry, outcome) -> apply(entry, outcome) }
    }

    /** Epoch ms of the next entry that is waiting for its retry time, or null when nothing is pending. */
    suspend fun nextDueAt(): Long? = outbox.earliestPendingAttempt()

    private suspend fun expireOldEntries() {
        val now = clock()
        outbox.allPending().filter { BackoffPolicy.isExpired(it.createdAt, now) }.forEach { expire(it) }
    }

    private suspend fun apply(entry: OutboxEntity, outcome: SendOutcome) {
        val now = clock()
        when (outcome) {
            is SendOutcome.Applied -> {
                // applied / duplicate / conflict_resolved / 200 replay / 201: the server's ticket is final.
                tickets.applyServerTicket(entry.localRef, outcome.ticket)
                outbox.update(entry.copy(state = OutboxState.DONE, attempt = entry.attempt + 1, ticketId = outcome.ticket.id,
                    lastErrorCode = null, lastErrorMessage = null, updatedAt = now))
            }
            SendOutcome.ConflictPending ->
                // The IT admin resolves it. The ticket stays pending_sync, so the patient still sees their saved place.
                outbox.update(entry.copy(state = OutboxState.CONFLICT_PENDING, attempt = entry.attempt + 1, updatedAt = now))
            is SendOutcome.Rejected -> {
                // Never retried, and never retried with a new key (that could create a second ticket).
                outbox.update(entry.copy(state = OutboxState.REJECTED, attempt = entry.attempt + 1,
                    lastErrorCode = outcome.code, lastErrorMessage = outcome.message, updatedAt = now))
                if (entry.op == OutboxOp.JOIN) tickets.markFailed(entry.localRef)
            }
            is SendOutcome.Retry -> scheduleRetry(entry, outcome, now)
            SendOutcome.NotAttempted ->
                outbox.update(entry.copy(state = OutboxState.PENDING, updatedAt = now))
        }
    }

    private suspend fun scheduleRetry(entry: OutboxEntity, retry: SendOutcome.Retry, now: Long) {
        val attempts = entry.attempt + 1
        when (val decision = BackoffPolicy.afterFailure(attempts, entry.createdAt, now, retry.retryAfterSeconds)) {
            is RetryDecision.RetryAt -> outbox.update(entry.copy(state = OutboxState.PENDING, attempt = attempts,
                nextAttemptAt = decision.epochMs, lastErrorCode = retry.code, lastErrorMessage = retry.message, updatedAt = now))
            RetryDecision.Expire -> expire(entry.copy(attempt = attempts, lastErrorCode = retry.code, lastErrorMessage = retry.message))
        }
    }

    /** Past the server's 24 h idempotency window: replaying could create a duplicate, so stop. */
    private suspend fun expire(entry: OutboxEntity) {
        outbox.update(entry.copy(state = OutboxState.EXPIRED, updatedAt = clock()))
        if (entry.op == OutboxOp.JOIN) tickets.markFailed(entry.localRef)
    }

    companion object { const val BATCH_SIZE = 50 }
}
