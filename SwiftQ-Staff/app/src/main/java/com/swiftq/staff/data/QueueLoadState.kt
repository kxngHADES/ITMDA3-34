package com.swiftq.staff.data

import com.swiftq.staff.model.QueueTicket

sealed class QueueLoadState {

    object Loading : QueueLoadState()

    data class Success(
        val tickets: List<QueueTicket>
    ) : QueueLoadState()

    data class Error(
        val message: String
    ) : QueueLoadState()
}