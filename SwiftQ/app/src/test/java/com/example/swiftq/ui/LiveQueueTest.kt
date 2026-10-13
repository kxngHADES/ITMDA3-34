package com.example.swiftq.ui

import com.example.swiftq.data.remote.QueueDto
import com.example.swiftq.data.remote.QueueEntryDto
import com.example.swiftq.ui.queue.buildLiveQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveQueueTest {
    private fun queue(waiting: Int, calledFirst: Boolean = false): QueueDto {
        val called = if (calledFirst) listOf(QueueEntryDto("c1", "A038", 1, "called")) else emptyList()
        val rows = (1..waiting).map { QueueEntryDto("t$it", "A%03d".format(38 + it), it + called.size, "waiting") }
        return QueueDto(id = "q", nowServing = "A038", entries = called + rows)
    }

    @Test fun showsFiveRowsAndHowManyAreAfterThePatient() {
        val (now, rows, more) = buildLiveQueue(queue(12), myTicketId = "t4")
        assertEquals("A038", now)
        assertEquals(5, rows.size)
        assertEquals(listOf(1, 2, 3, 4, 5), rows.map { it.position })
        assertEquals("t4", rows.single { it.isMe }.let { "t${it.position}" })
        assertEquals(7, more)
    }

    @Test fun calledTicketsAreNotInTheNextInLineList() {
        val (_, rows, _) = buildLiveQueue(queue(3, calledFirst = true), myTicketId = "t1")
        assertEquals(listOf("A039", "A040", "A041"), rows.map { it.number })
        assertTrue(rows.first().isMe)
    }

    @Test fun aPatientFarDownTheQueueIsStillShownWithOneRowAfter() {
        val (_, rows, more) = buildLiveQueue(queue(30), myTicketId = "t20")
        assertEquals(21, rows.size)
        assertTrue(rows[19].isMe)
        assertEquals(9, more)
    }

    @Test fun anUnknownTicketShowsTheFirstFiveWithNoYouRow() {
        val (_, rows, more) = buildLiveQueue(queue(8), myTicketId = null)
        assertEquals(5, rows.size)
        assertTrue(rows.none { it.isMe })
        assertEquals(3, more)
    }
}
