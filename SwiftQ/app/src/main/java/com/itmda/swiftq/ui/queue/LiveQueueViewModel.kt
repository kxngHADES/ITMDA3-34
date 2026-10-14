package com.itmda.swiftq.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.remote.QueueDto
import com.itmda.swiftq.data.repository.ClinicRepository
import com.itmda.swiftq.data.repository.QueueFetch
import com.itmda.swiftq.data.repository.QueueRepository
import com.itmda.swiftq.data.repository.TicketRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class QueueRow(val position: Int, val number: String, val isMe: Boolean)

/** What Figma 12 shows. [updatedAtMillis] is when this phone last heard from the server. */
data class LiveQueueUi(
    val clinicName: String? = null,
    val nowServing: String? = null,
    val rows: List<QueueRow> = emptyList(),
    val moreAfter: Int = 0,
    val updatedAtMillis: Long? = null,
    /** False while the last refresh failed: the list is then the last one we saw. */
    val live: Boolean = true,
    val loaded: Boolean = false,
)

/** Waiting tickets in order, numbered 1.., cut after the patient's own row (always at least [minRows]). */
fun buildLiveQueue(queue: QueueDto, myTicketId: String?, minRows: Int = 5): Triple<String?, List<QueueRow>, Int> {
    val waiting = queue.entries.filter { it.status == "waiting" }.sortedBy { it.position }
    val mine = waiting.indexOfFirst { it.ticketId == myTicketId }
    val shown = minOf(waiting.size, maxOf(minRows, mine + 2))
    val rows = waiting.take(shown).mapIndexed { i, e -> QueueRow(i + 1, e.displayNumber, e.ticketId == myTicketId) }
    return Triple(queue.nowServing, rows, waiting.size - shown)
}

class LiveQueueViewModel(
    private val tickets: TicketRepository,
    private val clinics: ClinicRepository,
    private val queues: QueueRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val fetched = MutableStateFlow<Triple<QueueDto, Long, Boolean>?>(null)
    private var etag: String? = null

    private val clinicName = MutableStateFlow<String?>(null)

    val ui: StateFlow<LiveQueueUi> = combine(fetched, tickets.observeActive(), clinicName) { got, ticket, name ->
        if (got == null) return@combine LiveQueueUi(clinicName = name)
        val (queue, at, live) = got
        val (now, rows, more) = buildLiveQueue(queue, ticket?.serverId)
        LiveQueueUi(name, now, rows, more, at, live, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveQueueUi())

    /** While the screen is open: ask for the queue every few seconds. The ETag keeps unchanged answers tiny. */
    suspend fun poll() {
        while (true) {
            refresh()
            delay(POLL_MS)
        }
    }

    suspend fun refresh() {
        val ticket: TicketEntity = tickets.findActive() ?: return
        if (clinicName.value == null) clinicName.value = clinics.clinic(ticket.clinicId)?.name
        when (val r = queues.fetch(ticket.queueId, etag)) {
            is QueueFetch.Fresh -> { etag = r.etag; fetched.value = Triple(r.queue, clock(), true) }
            QueueFetch.NotModified -> fetched.value = fetched.value?.let { Triple(it.first, clock(), true) }
            // Keep the last list on screen, marked as not live. Never loaded: the screen offers Try again.
            QueueFetch.Unavailable -> fetched.value = fetched.value?.let { Triple(it.first, it.second, false) }
        }
        failed.value = fetched.value == null
    }

    val failed = MutableStateFlow(false)

    private companion object { const val POLL_MS = 5_000L }
}
