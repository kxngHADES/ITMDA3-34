package com.example.swiftq.sync

import com.example.swiftq.FakeStore
import com.example.swiftq.RandomKeys
import com.example.swiftq.TICKET_JSON
import com.example.swiftq.TestCipher
import com.example.swiftq.data.local.OutboxState
import com.example.swiftq.data.local.TicketState
import com.example.swiftq.data.remote.ApiFactory
import com.example.swiftq.data.remote.ApiJson
import com.example.swiftq.data.remote.ErrorBody
import com.example.swiftq.data.remote.SyncItemResult
import com.example.swiftq.data.remote.SyncPushResponse
import com.example.swiftq.data.remote.TicketDto
import com.example.swiftq.data.repository.JoinInput
import com.example.swiftq.data.repository.JoinRepository
import com.example.swiftq.data.repository.JoinResult
import com.example.swiftq.data.repository.TicketRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class JoinFlowTest {
    private lateinit var server: MockWebServer
    private val store = FakeStore()
    private val cipher = TestCipher()
    private var now = 1_000_000L
    private lateinit var joins: JoinRepository
    private lateinit var tickets: TicketRepository
    private lateinit var processor: OutboxProcessor

    private val input = JoinInput("c1", "sp1", "q1", "Thabo", "082 123 4567", "sms", "none", "en")

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = ApiFactory.create(server.url("/").toString(), ApiFactory.httpClient(null))
        joins = JoinRepository(store.ticketDao, store.outboxDao, store.transactor, cipher, RandomKeys()) { now }
        tickets = TicketRepository(store.ticketDao, store.outboxDao, store.transactor, api) { now }
        processor = OutboxProcessor(store.outboxDao, tickets, RestJoinTransport(api, cipher)) { now }
    }

    @After fun tearDown() { server.shutdown() }

    private fun ok(code: Int = 201) = MockResponse().setResponseCode(code).setBody(TICKET_JSON)
    private fun error(code: Int, body: String, retryAfter: Int? = null) =
        MockResponse().setResponseCode(code).setBody(body).apply { retryAfter?.let { setHeader("Retry-After", it) } }
    private fun envelope(code: String, retryable: Boolean, msg: String = "Plain words.") =
        """{"error":{"code":"$code","message":"$msg","retryable":$retryable,"request_id":"req_1"}}"""

    @Test fun joinWritesOneTicketAndOneOutboxRowWithAnEncryptedPayload() = runBlocking {
        val r = joins.join(input) as JoinResult.Created
        assertEquals(1, store.ticketDao.count()); assertEquals(1, store.outboxDao.count())
        val row = store.outboxDao.all().single()
        assertEquals(TicketState.PENDING_SYNC, store.ticketDao.find(r.localRef)?.status)
        assertEquals(OutboxState.PENDING, row.state)
        assertFalse("payload must not be plain text", row.payloadEnc.contains("+27"))
        assertFalse("phone must not be plain text", store.ticketDao.find(r.localRef)?.phoneEnc.orEmpty().contains("+27"))
        assertEquals("4567", store.ticketDao.find(r.localRef)?.phoneLast4)
        assertTrue(cipher.decrypt(row.payloadEnc).decodeToString().contains("+27821234567"))
    }

    @Test fun emailChoiceIsKeptEncryptedOnThePhoneAndSentAsNone() = runBlocking {
        val r = joins.join(input.copy(phone = "", notificationChannel = "email", email = " Thabo@Example.com ")) as JoinResult.Created
        val ticket = store.ticketDao.find(r.localRef)!!
        assertEquals("thabo@example.com", cipher.decrypt(ticket.emailEnc!!).decodeToString())
        assertEquals("none", ticket.notificationChannel)
        val body = cipher.decrypt(store.outboxDao.all().single().payloadEnc).decodeToString()
        assertTrue(body.contains("\"notification_channel\":\"none\""))
        assertFalse("the API has no email field", body.contains("example.com"))
    }

    @Test fun emailChoiceNeedsAValidAddress() = runBlocking {
        val missing = joins.join(input.copy(phone = "", notificationChannel = "email", email = ""))
        assertTrue(missing is JoinResult.Invalid && missing.emailRequired)
        val bad = joins.join(input.copy(phone = "", notificationChannel = "email", email = "not-an-email"))
        assertTrue(bad is JoinResult.Invalid && bad.emailInvalid)
        assertEquals(0, store.outboxDao.count())
    }

    @Test fun leavingBeforeTheServerSawTheJoinWithdrawsItFromTheOutbox() = runBlocking {
        val r = joins.join(input) as JoinResult.Created
        assertEquals(com.example.swiftq.data.repository.LeaveResult.Left, tickets.leave(r.localRef))
        assertEquals(TicketState.CANCELLED, store.ticketDao.find(r.localRef)?.status)
        assertEquals(OutboxState.EXPIRED, store.outboxDao.all().single().state)
        processor.flush()
        assertEquals("nothing may be sent for a ticket the patient left", 0, server.requestCount)
        assertEquals(null, store.ticketDao.findActive())
    }

    @Test fun leavingAConfirmedTicketCallsCancelAndOffersRetryWhenOffline() = runBlocking {
        joins.join(input); server.enqueue(ok()); processor.flush()
        val ref = store.ticketDao.findActive()!!.localRef
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(com.example.swiftq.data.repository.LeaveResult.NoConnection, tickets.leave(ref))
        assertNotNull(store.ticketDao.findActive())
        server.enqueue(MockResponse().setResponseCode(200).setBody(TICKET_JSON.replace("\"status\":\"waiting\"", "\"status\":\"cancelled\"")))
        assertEquals(com.example.swiftq.data.repository.LeaveResult.Left, tickets.leave(ref))
        server.takeRequest(); server.takeRequest()
        val cancel = server.takeRequest()
        assertEquals("/v1/tickets/${store.ticketDao.find(ref)?.serverId}/cancel", cancel.path)
        assertNotNull(cancel.getHeader("Idempotency-Key"))
        assertEquals(TicketState.CANCELLED, store.ticketDao.find(ref)?.status)
    }

    @Test fun leavingATicketTheServerDoesNotKnowClosesItOnThePhone() = runBlocking {
        joins.join(input); server.enqueue(ok()); processor.flush()
        val ref = store.ticketDao.findActive()!!.localRef
        server.enqueue(error(404, envelope("NOT_FOUND", false)))
        assertEquals(com.example.swiftq.data.repository.LeaveResult.Left, tickets.leave(ref))
        assertEquals(TicketState.CANCELLED, store.ticketDao.find(ref)?.status)
    }

    @Test fun failureAfterFirstInsertLeavesNeitherRow() = runBlocking {
        store.failOutboxInsert = true
        try { joins.join(input); fail("expected the simulated failure") } catch (e: IllegalStateException) { /* expected */ }
        assertEquals(0, store.ticketDao.count()); assertEquals(0, store.outboxDao.count())
    }

    @Test fun doubleTapMakesOneOutboxRow() = runBlocking {
        val results = List(5) { async { joins.join(input) } }.awaitAll()
        assertEquals(1, results.filterIsInstance<JoinResult.Created>().size)
        assertEquals(4, results.filterIsInstance<JoinResult.AlreadyInQueue>().size)
        assertEquals(1, store.outboxDao.count()); assertEquals(1, store.ticketDao.count())
    }

    @Test fun invalidPhoneWritesNothing() = runBlocking {
        val r = joins.join(input.copy(phone = "12345"))
        assertTrue(r is JoinResult.Invalid && r.phoneInvalid)
        assertEquals(0, store.outboxDao.count())
    }

    @Test fun retryReusesTheKeyAndTheExactBytes() = runBlocking {
        joins.join(input)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)) // attempt 1: connection lost
        server.enqueue(ok())                                                                    // attempt 2
        processor.flush()
        assertEquals(1, store.outboxDao.all().single().attempt)
        assertEquals(OutboxState.PENDING, store.outboxDao.all().single().state)
        assertEquals(TicketState.PENDING_SYNC, store.ticketDao.observeActive().let { store.ticketDao.findActive() }?.status)

        now += 6_000 // past the 5 s backoff
        processor.flush()
        val first = server.takeRequest(); val second = server.takeRequest()
        assertNotNull(first.getHeader("Idempotency-Key"))
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
        assertEquals("/v1/queues/q1/tickets", second.path)
        val row = store.outboxDao.all().single()
        assertEquals(OutboxState.DONE, row.state)
        assertEquals(first.getHeader("Idempotency-Key"), row.idempotencyKey)
        assertEquals("A014", store.ticketDao.findActive()?.displayNumber)
    }

    @Test fun backoffHoldsTheRowUntilItsTime() = runBlocking {
        joins.join(input)
        server.enqueue(error(500, "oops")); processor.flush()
        processor.flush() // too early: must not send
        assertEquals(1, server.requestCount)
        assertEquals(now + 5_000, store.outboxDao.all().single().nextAttemptAt)
    }

    @Test fun replay200DoesNotCreateASecondTicket() = runBlocking {
        joins.join(input)
        server.enqueue(ok(200)); processor.flush()
        assertEquals(1, store.ticketDao.count())
        assertEquals(TicketState.WAITING, store.ticketDao.findActive()?.status)
    }

    @Test fun existingTicketReturnedByServerIsMergedNotDuplicated() = runBlocking {
        // A confirmed local row already owns server id tkt_1; a second local join comes back with the same id.
        val first = (joins.join(input) as JoinResult.Created).localRef
        server.enqueue(ok()); processor.flush()
        val dto = ApiJson.decodeFromString(TicketDto.serializer(), TICKET_JSON)
        // simulate a second local row created before the first confirmation was known
        val dup = store.ticketDao.find(first)!!.copy(localRef = "dup", serverId = null, status = TicketState.PENDING_SYNC)
        store.ticketDao.insert(dup)
        tickets.applyServerTicket("dup", dto)
        assertEquals(1, store.ticketDao.count())
        assertNotNull(store.ticketDao.find(first))
    }

    @Test fun pausedQueueStaysPendingAndRetryAfterIsHonoured() = runBlocking {
        joins.join(input)
        server.enqueue(error(400, envelope("QUEUE_PAUSED", true), retryAfter = 120)); processor.flush()
        val row = store.outboxDao.all().single()
        assertEquals(OutboxState.PENDING, row.state); assertEquals("QUEUE_PAUSED", row.lastErrorCode)
        assertEquals(now + 120_000, row.nextAttemptAt)
        assertEquals(TicketState.PENDING_SYNC, store.ticketDao.findActive()?.status)
    }

    @Test fun nonRetryable400RejectsAndFailsTheTicket() = runBlocking {
        val ref = (joins.join(input) as JoinResult.Created).localRef
        server.enqueue(error(400, envelope("CLINIC_CLOSED", false, "This clinic is closed right now."))); processor.flush()
        val row = store.outboxDao.all().single()
        assertEquals(OutboxState.REJECTED, row.state); assertEquals("This clinic is closed right now.", row.lastErrorMessage)
        assertEquals(TicketState.FAILED, store.ticketDao.find(ref)?.status)
    }

    @Test fun keyReusedIsRejectedWithoutAMessageAndNeverRetriedWithANewKey() = runBlocking {
        joins.join(input)
        server.enqueue(error(400, envelope("IDEMPOTENCY_KEY_REUSED", false))); processor.flush()
        now += 10_000_000; processor.flush()
        assertEquals(1, server.requestCount)
        assertEquals(OutboxState.REJECTED, store.outboxDao.all().single().state)
    }

    @Test fun validation422Rejects() = runBlocking {
        joins.join(input)
        server.enqueue(error(422, envelope("VALIDATION_FAILED", false))); processor.flush()
        assertEquals(OutboxState.REJECTED, store.outboxDao.all().single().state)
    }

    @Test fun http429UsesRetryAfterHeader() = runBlocking {
        joins.join(input)
        server.enqueue(error(429, envelope("RATE_LIMITED", true).replace("\"retryable\":true", "\"retryable\":true"), retryAfter = 30)); processor.flush()
        val row = store.outboxDao.all().single()
        assertEquals(OutboxState.PENDING, row.state); assertEquals(now + 30_000, row.nextAttemptAt)
    }

    @Test fun staleInFlightRowsResetToPendingOnStart() = runBlocking {
        joins.join(input)
        store.outboxDao.markInFlight(store.outboxDao.all().map { it.id }, now)
        assertEquals(OutboxState.IN_FLIGHT, store.outboxDao.all().single().state)
        processor.recoverAfterRestart()
        assertEquals(OutboxState.PENDING, store.outboxDao.all().single().state)
    }

    @Test fun entriesOlderThan24HoursExpireInsteadOfReplaying() = runBlocking {
        val ref = (joins.join(input) as JoinResult.Created).localRef
        now += 25 * 3_600_000L
        processor.flush()
        assertEquals(0, server.requestCount)
        assertEquals(OutboxState.EXPIRED, store.outboxDao.all().single().state)
        assertEquals(TicketState.FAILED, store.ticketDao.find(ref)?.status)
    }

    // --- sync/push outcome mapping ------------------------------------------------------------------

    private fun pushResult(outcome: String, key: String, ref: String, ticket: Boolean = true, error: ErrorBody? = null) =
        SyncItemResult(key, ref, outcome, if (ticket) ApiJson.decodeFromString(TicketDto.serializer(), TICKET_JSON) else null, error)

    @Test fun everySyncPushOutcomeMapsToTheRightState() = runBlocking {
        val api = ApiFactory.create(server.url("/").toString(), ApiFactory.httpClient(null))
        val push = SyncPushTransport(api, cipher) { "device-1234" }
        val refs = (1..5).map { i ->
            joins.join(input.copy(clinicId = "c$i")); store.outboxDao.all().last()
        }
        // results deliberately in a different order than the request: matching is by key, not position
        val results = listOf(
            pushResult("rejected", refs[4].idempotencyKey, refs[4].localRef, false, ErrorBody("CLINIC_CLOSED", "Closed.", false)),
            pushResult("conflict_pending", refs[3].idempotencyKey, refs[3].localRef, false),
            pushResult("conflict_resolved", refs[2].idempotencyKey, refs[2].localRef),
            pushResult("duplicate", refs[1].idempotencyKey, refs[1].localRef),
            pushResult("applied", refs[0].idempotencyKey, refs[0].localRef),
        )
        val outcomes = push.mapResults(refs, SyncPushResponse(results))
        assertTrue(outcomes[0] is SendOutcome.Applied); assertTrue(outcomes[1] is SendOutcome.Applied)
        assertTrue(outcomes[2] is SendOutcome.Applied); assertEquals(SendOutcome.ConflictPending, outcomes[3])
        assertEquals("Closed.", (outcomes[4] as SendOutcome.Rejected).message)

        // and the processor turns them into outbox/ticket states
        val fixed = object : OutboxTransport { override suspend fun send(entries: List<com.example.swiftq.data.local.OutboxEntity>) = outcomes }
        OutboxProcessor(store.outboxDao, tickets, fixed) { now }.flush()
        val states = store.outboxDao.all().map { it.state }
        assertEquals(listOf("done", "done", "done", "conflict_pending", "rejected"), states)
        assertEquals(TicketState.FAILED, store.ticketDao.find(refs[4].localRef)?.status)
        assertEquals(TicketState.PENDING_SYNC, store.ticketDao.find(refs[3].localRef)?.status)
    }
}
