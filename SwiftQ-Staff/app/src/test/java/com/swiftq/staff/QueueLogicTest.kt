package com.swiftq.staff

import com.swiftq.staff.data.remote.QueueApiRepository
import com.swiftq.staff.data.remote.model.QueueResponse
import com.swiftq.staff.model.QueuePriority
import com.swiftq.staff.model.QueueStatus
import com.swiftq.staff.model.QueueTicket
import com.swiftq.staff.notifications.isPendingPriorityRequest
import com.swiftq.staff.ui.WalkInSheet
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLogicTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun priority_isOnlyConfirmedOnceStaffConfirm() {
        assertEquals(
            QueuePriority.REQUESTED,
            QueueApiRepository.priorityOf("elderly", staffConfirmed = false)
        )
        assertEquals(
            QueuePriority.PRIORITY,
            QueueApiRepository.priorityOf("elderly", staffConfirmed = true)
        )
        assertEquals(
            QueuePriority.PRIORITY,
            QueueApiRepository.priorityOf(null, staffConfirmed = false, isPriority = true)
        )
        assertEquals(
            QueuePriority.REGULAR,
            QueueApiRepository.priorityOf("none", staffConfirmed = false)
        )
        assertEquals(
            QueuePriority.REGULAR,
            QueueApiRepository.priorityOf(null, staffConfirmed = false)
        )
    }

    @Test
    fun mapTickets_readsApiQueueAndDropsCancelled() {
        val queue = json.decodeFromString(
            QueueResponse.serializer(),
            """
            {
              "id": "q1", "service_point_id": "sp1", "clinic_id": "c1",
              "service_date": "2026-10-20", "display_prefix": "A",
              "status": "open", "waiting_count": 2,
              "updated_at": "2026-10-20T08:00:00Z",
              "entries": [
                {"ticket_id": "t1", "display_number": "A001", "position": 2,
                 "status": "waiting", "is_priority": false, "late_flag": false,
                 "priority_category": "pregnant", "staff_confirmed": null,
                 "display_name": "Lerato", "joined_at": "2026-10-20T07:55:00Z"},
                {"ticket_id": "t2", "display_number": "A002", "position": 1,
                 "status": "waiting", "is_priority": true, "late_flag": true,
                 "priority_category": "elderly", "staff_confirmed": true},
                {"ticket_id": "t3", "display_number": "A003", "position": 3,
                 "status": "cancelled", "is_priority": false, "late_flag": false}
              ]
            }
            """.trimIndent()
        )

        val tickets = QueueApiRepository(FakeApi.unused, FakeApi.unusedServicePoints)
            .mapTickets(queue)

        assertEquals(listOf("t1", "t2"), tickets.map { it.id })

        val requested = tickets.first { it.id == "t1" }
        assertEquals(QueuePriority.REQUESTED, requested.priority)
        assertEquals("pregnant", requested.priorityCategory)
        assertEquals("Lerato", requested.displayName)
        assertTrue(requested.isPendingPriorityRequest())

        val confirmed = tickets.first { it.id == "t2" }
        assertEquals(QueuePriority.PRIORITY, confirmed.priority)
        assertTrue(confirmed.lateFlag)
        assertFalse(confirmed.isPendingPriorityRequest())
    }

    @Test
    fun queueOrder_putsCalledFirstThenPosition() {
        val tickets = listOf(
            ticket("w2", QueueStatus.WAITING, position = 2),
            ticket("s", QueueStatus.SERVED),
            ticket("w1", QueueStatus.WAITING, position = 1),
            ticket("c", QueueStatus.CALLED)
        )

        assertEquals(
            listOf("c", "w1", "w2", "s"),
            tickets.sortedWith(QueueTicket.QUEUE_ORDER).map { it.id }
        )
    }

    @Test
    fun normalisePhone_acceptsLocalSouthAfricanFormats() {
        assertEquals("+27821234567", WalkInSheet.normalisePhone("082 123 4567"))
        assertEquals("+27821234567", WalkInSheet.normalisePhone("+27 82 123 4567"))
        assertEquals("+27821234567", WalkInSheet.normalisePhone("27821234567"))
        assertNull(WalkInSheet.normalisePhone("   "))
    }

    private fun ticket(id: String, status: QueueStatus, position: Int? = null) =
        QueueTicket(
            id = id,
            ticketNumber = id,
            status = status,
            priority = QueuePriority.REGULAR,
            position = position
        )
}
