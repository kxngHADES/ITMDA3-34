package com.swiftq.staff.notifications

import android.content.Context
import com.swiftq.staff.model.QueuePriority
import com.swiftq.staff.model.QueueStatus
import com.swiftq.staff.model.QueueTicket

// Remembers which priority requests staff have already been alerted about.
class PriorityRequestTracker(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "swiftq_priority_alerts",
        Context.MODE_PRIVATE
    )

    // Returns requests that appeared since the previous sync of this queue.
    // The first sync of a queue only records a baseline: staff are looking
    // at those requests on screen already.
    fun newRequests(
        queueId: String,
        tickets: List<QueueTicket>
    ): List<QueueTicket> {
        val pending = tickets.filter { it.isPendingPriorityRequest() }
        val key = "$KEY_PREFIX$queueId"
        val seen = preferences.getStringSet(key, null)

        preferences.edit()
            .putStringSet(key, pending.map { it.id }.toSet())
            .apply()

        if (seen == null) return emptyList()

        return pending.filter { it.id !in seen }
    }

    fun clear() {
        preferences.edit()
            .clear()
            .apply()
    }

    companion object {
        private const val KEY_PREFIX = "pending_"
    }
}

fun QueueTicket.isPendingPriorityRequest(): Boolean {
    return priority == QueuePriority.REQUESTED &&
            (status == QueueStatus.WAITING || status == QueueStatus.CALLED)
}
