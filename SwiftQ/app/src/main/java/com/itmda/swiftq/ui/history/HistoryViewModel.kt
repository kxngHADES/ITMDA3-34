package com.itmda.swiftq.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.repository.ClinicRepository
import com.itmda.swiftq.data.repository.TicketRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryItem(val ticket: TicketEntity, val clinicName: String?)

data class HistoryUi(val items: List<HistoryItem> = emptyList(), val loaded: Boolean = false)

/** Finished tickets, newest first. All from SQLite, so it opens and works with no connection. */
class HistoryViewModel(
    private val tickets: TicketRepository,
    private val clinics: ClinicRepository,
) : ViewModel() {
    val ui: StateFlow<HistoryUi> = tickets.observeHistory()
        .map { list -> HistoryUi(list.map { HistoryItem(it, clinics.clinic(it.clinicId)?.name) }, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun remove(localRef: String) { viewModelScope.launch { tickets.removeFromHistory(localRef) } }
    fun clearAll() { viewModelScope.launch { tickets.clearHistory() } }
}
