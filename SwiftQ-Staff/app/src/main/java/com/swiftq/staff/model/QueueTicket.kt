package com.swiftq.staff.model

enum class QueueStatus {
    WAITING,
    CALLED,
    SERVED,
    NO_SHOW
}

enum class QueuePriority {
    REGULAR,

    // The patient asked for priority; staff have not confirmed it yet.
    REQUESTED,

    // Staff confirmed the priority category.
    PRIORITY
}

data class QueueTicket(
    val id: String,
    val ticketNumber: String,
    val status: QueueStatus,
    val priority: QueuePriority,
    val position: Int? = null,
    val displayName: String? = null,
    val priorityCategory: String? = null,
    val lateFlag: Boolean = false,
    val joinedAt: String? = null
) {
    companion object {
        // Called tickets first, then waiting tickets in queue order.
        val QUEUE_ORDER: Comparator<QueueTicket> = compareBy<QueueTicket>(
            {
                when (it.status) {
                    QueueStatus.CALLED -> 0
                    QueueStatus.WAITING -> 1
                    QueueStatus.SERVED -> 2
                    QueueStatus.NO_SHOW -> 3
                }
            },
            { it.position ?: Int.MAX_VALUE },
            { it.ticketNumber }
        )
    }
}
