package com.itmda.swiftq

import com.itmda.swiftq.data.local.OutboxDao
import com.itmda.swiftq.data.local.OutboxEntity
import com.itmda.swiftq.data.local.TicketDao
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.local.Transactor
import com.itmda.swiftq.security.PayloadCipher
import com.itmda.swiftq.sync.IdempotencyKeyFactory
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory DAOs so the repository and processor logic runs on the JVM. Real SQLite is covered by the instrumented tests. */
class FakeStore {
    val tickets = MutableStateFlow<Map<String, TicketEntity>>(emptyMap())
    val outbox = MutableStateFlow<List<OutboxEntity>>(emptyList())
    var failOutboxInsert = false
    private var nextId = 1L

    val transactor = object : Transactor {
        // Mimics a SQL transaction: if the block throws, both tables roll back to how they were.
        override suspend fun <T> run(block: suspend () -> T): T {
            val t = tickets.value; val o = outbox.value; val id = nextId
            return try { block() } catch (e: Throwable) { tickets.value = t; outbox.value = o; nextId = id; throw e }
        }
    }

    val ticketDao = object : TicketDao {
        override suspend fun insert(ticket: TicketEntity) { tickets.value = tickets.value + (ticket.localRef to ticket) }
        override suspend fun update(ticket: TicketEntity) { tickets.value = tickets.value + (ticket.localRef to ticket) }
        override suspend fun delete(ticket: TicketEntity) { tickets.value = tickets.value - ticket.localRef }
        override suspend fun find(localRef: String) = tickets.value[localRef]
        override fun observe(localRef: String): Flow<TicketEntity?> = tickets.map { it[localRef] }
        override suspend fun findByServerId(serverId: String) = tickets.value.values.firstOrNull { it.serverId == serverId }
        private fun active() = tickets.value.values.filter { it.status in listOf("pending_sync", "waiting", "called") }
        override fun observeActive(): Flow<TicketEntity?> = tickets.map { _ -> active().maxByOrNull { it.clientCreatedAt } }
        override suspend fun findActive() = active().maxByOrNull { it.clientCreatedAt }
        override suspend fun findActiveAtClinic(clinicId: String) = active().filter { it.clinicId == clinicId }.maxByOrNull { it.clientCreatedAt }
        override suspend fun count() = tickets.value.size
        private val closed = listOf("served", "no_show", "cancelled", "failed")
        override fun observeHistory(): Flow<List<TicketEntity>> =
            tickets.map { m -> m.values.filter { it.status in closed && !it.hiddenFromHistory }.sortedByDescending { it.clientCreatedAt } }
        override suspend fun hide(localRef: String) {
            tickets.value[localRef]?.takeIf { it.status in closed }?.let { tickets.value = tickets.value + (localRef to it.copy(hiddenFromHistory = true)) }
        }
        override suspend fun hideAllFinished(): Int {
            val n = tickets.value.values.count { it.status in closed && !it.hiddenFromHistory }
            tickets.value = tickets.value.mapValues { (_, t) -> if (t.status in closed) t.copy(hiddenFromHistory = true) else t }; return n
        }
        override suspend fun deleteFinishedBefore(cutoffIso: String): Int {
            val gone = tickets.value.filterValues { it.status in closed && (it.closedAt ?: it.updatedAt) < cutoffIso }
            tickets.value = tickets.value - gone.keys; return gone.size
        }
    }

    val outboxDao = object : OutboxDao {
        override suspend fun insert(entry: OutboxEntity): Long {
            if (failOutboxInsert) error("simulated failure after the ticket insert")
            require(outbox.value.none { it.idempotencyKey == entry.idempotencyKey }) { "UNIQUE idempotency_key" }
            val saved = entry.copy(id = nextId++); outbox.value = outbox.value + saved; return saved.id
        }
        override suspend fun update(entry: OutboxEntity) { outbox.value = outbox.value.map { if (it.id == entry.id) entry else it } }
        override suspend fun nextBatch(now: Long, limit: Int) = outbox.value.filter { it.state == "pending" && it.nextAttemptAt <= now }.sortedBy { it.id }.take(limit)
        override suspend fun markInFlight(ids: List<Long>, now: Long) { outbox.value = outbox.value.map { if (it.id in ids) it.copy(state = "in_flight") else it } }
        override suspend fun resetStaleInFlight(now: Long): Int {
            val n = outbox.value.count { it.state == "in_flight" }
            outbox.value = outbox.value.map { if (it.state == "in_flight") it.copy(state = "pending") else it }; return n
        }
        override suspend fun allPending() = outbox.value.filter { it.state == "pending" }.sortedBy { it.id }
        override suspend fun earliestPendingAttempt() = allPending().minOfOrNull { it.nextAttemptAt }
        override suspend fun findByKey(key: String) = outbox.value.firstOrNull { it.idempotencyKey == key }
        override suspend fun all() = outbox.value
        override fun observeJoinFor(localRef: String): Flow<OutboxEntity?> = outbox.map { l -> l.lastOrNull { it.localRef == localRef && it.op == "join" } }
        override suspend fun repoint(from: String, to: String, ticketId: String?, now: Long) {
            outbox.value = outbox.value.map { if (it.localRef == from) it.copy(localRef = to, ticketId = ticketId) else it }
        }
        override suspend fun count() = outbox.value.size
        override suspend fun deleteFinishedBefore(cutoffMs: Long): Int {
            val gone = outbox.value.filter { it.state in listOf("done", "rejected", "expired") && it.updatedAt < cutoffMs }
            outbox.value = outbox.value - gone.toSet(); return gone.size
        }
    }
}

/** Real AES-GCM, so "decrypt gives back identical bytes" is meaningful on the JVM too. */
class TestCipher : PayloadCipher {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    override fun encrypt(plain: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return Base64.getEncoder().encodeToString(iv + c.doFinal(plain))
    }
    override fun decrypt(stored: String): ByteArray {
        val raw = Base64.getDecoder().decode(stored)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw, 0, 12)) }
        return c.doFinal(raw, 12, raw.size - 12)
    }
}

class RandomKeys : IdempotencyKeyFactory { override fun newKey() = UUID.randomUUID().toString() }

const val TICKET_JSON = """{"id":"tkt_1","display_number":"A014","sequence_no":14,"queue_id":"q1","service_point_id":"sp1","clinic_id":"c1","status":"waiting","channel":"app","position":4,"people_ahead":3,"estimated_wait":{"min_minutes":16,"max_minutes":24,"cold_start":false},"priority":{"requested_category":"none","staff_confirmed":false},"late_flag":false,"created_at":"2026-10-13T10:00:00Z","updated_at":"2026-10-13T10:00:00Z"}"""
