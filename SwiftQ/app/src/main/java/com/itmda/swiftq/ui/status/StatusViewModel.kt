package com.itmda.swiftq.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda.swiftq.data.local.OutboxEntity
import com.itmda.swiftq.data.local.OutboxState
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.local.TicketState
import com.itmda.swiftq.data.repository.ClinicRepository
import com.itmda.swiftq.data.repository.TicketRepository
import com.itmda.swiftq.sync.OutboxProcessor
import com.itmda.swiftq.data.repository.LeaveResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Why a saved (not yet confirmed) ticket is still waiting, as far as the patient needs to know. */
enum class SavedReason { Offline, Paused, Unreachable }

data class StatusUi(
    val ticket: TicketEntity? = null,
    val clinicName: String? = null,
    val servicePointName: String? = null,
    val savedReason: SavedReason? = null,
    /** The server's plain-language refusal, or null to show our generic text. */
    val failureMessage: String? = null,
    val loaded: Boolean = false,
)

/** State of the Leave queue action on Home. */
data class LeaveUi(val leaving: Boolean = false, val error: Error? = null, val done: Boolean = false) {
    enum class Error { NoConnection, TryAgain }
}

/**
 * Drives both the "You're in" screen and the status screen. [localRef] is null when the screen should
 * show whichever ticket is active.
 */
class StatusViewModel(
    private val localRef: String?,
    private val tickets: TicketRepository,
    private val clinics: ClinicRepository,
    private val processor: OutboxProcessor,
) : ViewModel() {

    private val ticketFlow: Flow<TicketEntity?> =
        if (localRef == null) tickets.observeActive()
        // After a merge the row for localRef is gone; fall back to the ticket that absorbed it.
        else combine(tickets.observe(localRef), tickets.observeActive()) { own, active -> own ?: active }

    val ui: StateFlow<StatusUi> = ticketFlow.flatMapLatest { ticket ->
        if (ticket == null) flowOf(StatusUi(loaded = true)) else combine(
            tickets.observeJoinOutbox(ticket.localRef),
            names(ticket),
        ) { outbox, names -> build(ticket, outbox, names.first, names.second) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUi())

    private val _leave = MutableStateFlow(LeaveUi())
    val leave: StateFlow<LeaveUi> = _leave

    /** Leave the queue; on success the ticket stops being active and Home switches back by itself. */
    fun leaveQueue() {
        val ref = ui.value.ticket?.localRef ?: return
        if (_leave.value.leaving) return
        _leave.value = LeaveUi(leaving = true)
        viewModelScope.launch {
            _leave.value = when (tickets.leave(ref)) {
                LeaveResult.Left -> LeaveUi(done = true)
                LeaveResult.NoConnection -> LeaveUi(error = LeaveUi.Error.NoConnection)
                LeaveResult.TryAgain -> LeaveUi(error = LeaveUi.Error.TryAgain)
            }
        }
    }

    /** Called once the "You left the queue" screen has been shown. */
    fun leaveHandled() { _leave.value = LeaveUi() }

    private fun names(ticket: TicketEntity): Flow<Pair<String?, String?>> = flow {
        emit(clinics.clinic(ticket.clinicId)?.name to clinics.servicePoint(ticket.servicePointId)?.name)
    }

    private fun build(ticket: TicketEntity, outbox: OutboxEntity?, clinic: String?, service: String?): StatusUi {
        val saved = ticket.status == TicketState.PENDING_SYNC
        return StatusUi(
            ticket = ticket,
            clinicName = clinic,
            servicePointName = service,
            savedReason = if (!saved) null else when {
                outbox?.lastErrorCode == "QUEUE_PAUSED" -> SavedReason.Paused
                (outbox?.attempt ?: 0) > 0 -> SavedReason.Unreachable
                else -> SavedReason.Offline
            },
            failureMessage = if (ticket.status == TicketState.FAILED && outbox?.state == OutboxState.REJECTED) outbox.lastErrorMessage else null,
            loaded = true,
        )
    }

    /**
     * While this screen is open: push a still-saved join through the outbox when its retry time comes round,
     * and refresh a confirmed ticket now and then. P4/P5 move both into the WorkManager worker and the live updates.
     */
    suspend fun keepFresh() {
        var tick = 0
        while (true) {
            val ticket = ui.value.ticket
            when (ticket?.status) {
                TicketState.PENDING_SYNC -> processor.flush()
                TicketState.WAITING, TicketState.CALLED -> if (tick % REFRESH_EVERY == 0) tickets.refresh(ticket.localRef)
            }
            tick++
            delay(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS = 5_000L
        const val REFRESH_EVERY = 3
    }
}
