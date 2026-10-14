package com.itmda.swiftq.data.repository

import com.itmda.swiftq.data.local.OutboxDao
import com.itmda.swiftq.data.local.SyncKeys
import com.itmda.swiftq.data.local.SyncStateDao
import com.itmda.swiftq.data.local.SyncStateEntity
import com.itmda.swiftq.data.local.TicketDao
import com.itmda.swiftq.data.local.Transactor
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

data class CleanupResult(val tickets: Int, val outboxEntries: Int)

/**
 * Expired-ticket cleanup, so the phone does not keep a patient's old tickets (and their name and phone) forever.
 *  - finished tickets (served, missed, left, or a join that failed or expired) go [TICKET_RETENTION] after they ended;
 *  - finished outbox entries (done, rejected, expired) go [OUTBOX_RETENTION] after they last changed.
 * Never touched: an active ticket, or an outbox entry that still has work to do (pending, in flight, conflict pending).
 * Runs at most every [MIN_INTERVAL], at app start and from the background sync.
 */
class TicketCleaner(
    private val tickets: TicketDao,
    private val outbox: OutboxDao,
    private val syncState: SyncStateDao,
    private val transactor: Transactor,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun runIfDue(): CleanupResult? {
        val now = clock()
        val last = syncState.get(SyncKeys.LAST_CLEANUP_AT)?.toLongOrNull()
        if (last != null && now - last < MIN_INTERVAL.inWholeMilliseconds) return null
        return run().also { syncState.put(SyncStateEntity(SyncKeys.LAST_CLEANUP_AT, now.toString())) }
    }

    suspend fun run(): CleanupResult {
        val now = clock()
        val ticketCutoff = Instant.ofEpochMilli(now - TICKET_RETENTION.inWholeMilliseconds).toString()
        val outboxCutoff = now - OUTBOX_RETENTION.inWholeMilliseconds
        return transactor.run { CleanupResult(tickets.deleteFinishedBefore(ticketCutoff), outbox.deleteFinishedBefore(outboxCutoff)) }
    }

    companion object {
        val TICKET_RETENTION = 30.days
        val OUTBOX_RETENTION = 7.days
        val MIN_INTERVAL = 6.hours
    }
}
