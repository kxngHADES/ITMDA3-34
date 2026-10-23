package com.itmda.swiftq.data.repository

import com.itmda.swiftq.data.local.OutboxDao
import com.itmda.swiftq.data.local.OutboxEntity
import com.itmda.swiftq.data.local.OutboxOp
import com.itmda.swiftq.data.local.OutboxState
import com.itmda.swiftq.data.local.TicketDao
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.local.TicketState
import com.itmda.swiftq.data.local.Transactor
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.ApiResult
import com.itmda.swiftq.data.remote.SwiftQApi
import com.itmda.swiftq.data.remote.TicketDto
import com.itmda.swiftq.data.remote.safeApiCall
import kotlinx.coroutines.flow.Flow

sealed interface LeaveResult {
    data object Left : LeaveResult
    data object NoConnection : LeaveResult
    data object TryAgain : LeaveResult
}

/** The patient's own tickets. The UI reads these as Flows, so the status screen updates by itself. */
class TicketRepository(
    private val tickets: TicketDao,
    private val outbox: OutboxDao,
    private val transactor: Transactor,
    private val api: SwiftQApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(localRef: String): Flow<TicketEntity?> = tickets.observe(localRef)

    fun observeActive(): Flow<TicketEntity?> = tickets.observeActive()

    fun observeJoinOutbox(localRef: String): Flow<OutboxEntity?> = outbox.observeJoinFor(localRef)

    suspend fun findActive(): TicketEntity? = tickets.findActive()

    fun observeHistory(): Flow<List<TicketEntity>> = tickets.observeHistory()
    suspend fun removeFromHistory(localRef: String) = tickets.hide(localRef)
    suspend fun clearHistory(): Int = tickets.hideAllFinished()

    /**
     * Applies the server's ticket to the local row for [localRef].
     *
     * Duplicate handling: a 200 can mean "you already held an active ticket". If the server ticket's id already
     * belongs to a different local row, that row is the real one: this local row is merged into it (its outbox
     * entry is re-pointed so the history stays) and the duplicate row is deleted, so the patient never sees two tickets.
     */
    suspend fun applyServerTicket(localRef: String, dto: TicketDto) = transactor.run {
        val now = clock()
        val local = tickets.find(localRef) ?: return@run
        val owner = tickets.findByServerId(dto.id)
        if (owner != null && owner.localRef != localRef) {
            outbox.repoint(from = localRef, to = owner.localRef, ticketId = dto.id, now = now)
            tickets.delete(local)
            tickets.update(owner.withServerTicket(dto, now))
        } else {
            tickets.update(local.withServerTicket(dto, now))
        }
    }

    /** Marks a join the server refused (or that expired) so the status screen can say so. */
    suspend fun markFailed(localRef: String) {
        val local = tickets.find(localRef) ?: return
        tickets.update(local.copy(status = TicketState.FAILED, updatedAt = java.time.Instant.ofEpochMilli(clock()).toString()))
    }

    /**
     * Leave the queue. A ticket the server never saw is cancelled on the phone and its join is withdrawn from the
     * outbox, so it can never be sent later. A confirmed ticket needs the server's cancel (POST /v1/tickets/{id}/cancel),
     * so it asks the patient to try again when there is no connection instead of pretending it worked.
     */
    suspend fun leave(localRef: String): LeaveResult {
        val local = tickets.find(localRef) ?: return LeaveResult.Left
        val serverId = local.serverId
        if (serverId == null) {
            return transactor.run {
                val join = outbox.all().lastOrNull { it.localRef == localRef && it.op == OutboxOp.JOIN }
                if (join?.state == OutboxState.IN_FLIGHT) return@run LeaveResult.TryAgain
                val now = clock()
                if (join != null && join.state == OutboxState.PENDING) outbox.update(join.copy(state = OutboxState.EXPIRED, updatedAt = now))
                closeLocally(local)
                LeaveResult.Left
            }
        }
        val key = java.util.UUID.randomUUID().toString()
        return when (val result = safeApiCall({ ApiJson.decodeFromString(TicketDto.serializer(), it) }) { api.cancelTicket(serverId, key) }) {
            is ApiResult.Success -> { applyServerTicket(localRef, result.value); LeaveResult.Left }
            is ApiResult.NetworkError -> LeaveResult.NoConnection
            // The server has no such ticket (it was closed, or never existed for this patient): nothing to leave there.
            is ApiResult.Failure -> if (result.httpStatus == 404) { closeLocally(local); LeaveResult.Left } else LeaveResult.TryAgain
        }
    }

    private suspend fun closeLocally(local: TicketEntity) {
        val iso = java.time.Instant.ofEpochMilli(clock()).toString()
        tickets.update(local.copy(status = TicketState.CANCELLED, closedAt = iso, updatedAt = iso))
    }

    /** Refreshes a confirmed ticket from GET /v1/tickets/{id}. Returns false when the server could not be reached. */
    suspend fun refresh(localRef: String): Boolean {
        val local = tickets.find(localRef) ?: return false
        val serverId = local.serverId ?: return false
        val result = safeApiCall({ ApiJson.decodeFromString(TicketDto.serializer(), it) }) { api.getTicket(serverId) }
        return when (result) {
            is ApiResult.Success -> { applyServerTicket(localRef, result.value); true }
            else -> false
        }
    }
}
