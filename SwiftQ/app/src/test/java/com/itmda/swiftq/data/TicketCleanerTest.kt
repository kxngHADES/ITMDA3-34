package com.itmda.swiftq.data

import com.itmda.swiftq.FakeStore
import com.itmda.swiftq.data.local.OutboxEntity
import com.itmda.swiftq.data.local.SyncStateDao
import com.itmda.swiftq.data.local.SyncStateEntity
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.repository.TicketCleaner
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TicketCleanerTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
    private val store = FakeStore()
    private val state = HashMap<String, String?>()
    private val syncState = object : SyncStateDao {
        override suspend fun get(key: String) = state[key]
        override suspend fun put(entry: SyncStateEntity) { state[entry.key] = entry.value }
    }
    private val cleaner = TicketCleaner(store.ticketDao, store.outboxDao, syncState, store.transactor) { now }

    private fun ticket(ref: String, status: String, daysAgo: Long) = TicketEntity(
        localRef = ref, queueId = "q", servicePointId = "sp", clinicId = "c", status = status,
        notificationChannel = "push", language = "en",
        clientCreatedAt = Instant.ofEpochMilli(now - daysAgo.days.inWholeMilliseconds).toString(),
        updatedAt = Instant.ofEpochMilli(now - daysAgo.days.inWholeMilliseconds).toString(),
    )

    private fun entry(id: Long, state: String, daysAgo: Long) = OutboxEntity(
        id = id, idempotencyKey = "k$id", op = "join", localRef = "r$id", payloadEnc = "x", clientUpdatedAt = "t",
        state = state, nextAttemptAt = 0, createdAt = now, updatedAt = now - daysAgo.days.inWholeMilliseconds,
    )

    @Test fun oldFinishedTicketsGoButRecentAndActiveOnesStay() = runBlocking {
        store.tickets.value = listOf(
            ticket("old-served", "served", 31), ticket("old-failed", "failed", 45), ticket("old-cancelled", "cancelled", 40),
            ticket("recent-served", "served", 5),
            ticket("ancient-but-waiting", "waiting", 90), ticket("ancient-saved", "pending_sync", 90),
        ).associateBy { it.localRef }
        val result = cleaner.run()
        assertEquals(3, result.tickets)
        assertEquals(setOf("recent-served", "ancient-but-waiting", "ancient-saved"), store.tickets.value.keys)
    }

    @Test fun outboxKeepsEntriesThatStillHaveWorkToDo() = runBlocking {
        store.outbox.value = listOf(
            entry(1, "done", 8), entry(2, "rejected", 9), entry(3, "expired", 10),
            entry(4, "done", 1),
            entry(5, "pending", 30), entry(6, "in_flight", 30), entry(7, "conflict_pending", 30),
        )
        assertEquals(3, cleaner.run().outboxEntries)
        assertEquals(listOf(4L, 5L, 6L, 7L), store.outbox.value.map { it.id })
    }

    @Test fun runsAtMostEverySixHours() = runBlocking {
        assertNotNull(cleaner.runIfDue())
        assertNull(cleaner.runIfDue())
        val later = TicketCleaner(store.ticketDao, store.outboxDao, syncState, store.transactor) { now + 7 * 3_600_000L }
        assertNotNull(later.runIfDue())
    }

    @Test fun historyShowsOnlyFinishedVisibleTicketsNewestFirstAndCanBeHidden() = runBlocking {
        store.tickets.value = listOf(
            ticket("a", "served", 3), ticket("b", "cancelled", 1), ticket("c", "waiting", 0), ticket("d", "no_show", 2),
        ).associateBy { it.localRef }
        val dao = store.ticketDao
        suspend fun refs() = dao.observeHistory().first().map { it.localRef }
        assertEquals(listOf("b", "d", "a"), refs())
        dao.hide("d"); dao.hide("c") // an active ticket cannot be hidden
        assertEquals(listOf("b", "a"), refs())
        assertEquals(2, dao.hideAllFinished())
        assertEquals(emptyList<String>(), refs())
        assertNotNull(store.tickets.value["c"])
    }
}
