package com.itmda.swiftq

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itmda.swiftq.data.local.RoomTransactor
import com.itmda.swiftq.data.local.SwiftQDatabase
import com.itmda.swiftq.data.repository.JoinInput
import com.itmda.swiftq.data.repository.JoinRepository
import com.itmda.swiftq.data.repository.JoinResult
import com.itmda.swiftq.security.KeystoreCipher
import com.itmda.swiftq.sync.UuidV4KeyFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataLayerInstrumentedTest {
    private lateinit var db: SwiftQDatabase
    private val cipher = KeystoreCipher("swiftq_test_key")
    private val input = JoinInput("c1", "sp1", "q1", "Thabo", "0821234567", "sms", "none", "en")

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, SwiftQDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun keystoreAesGcmRoundTripReturnsIdenticalBytes() {
        val plain = """{"language":"en","phone":"+27821234567","client_created_at":"2026-10-13T10:00:00.123Z"}""".toByteArray()
        val stored = cipher.encrypt(plain)
        assertArrayEquals(plain, cipher.decrypt(stored))
        assertFalse(stored.contains("27821234567"))
        assertNotEquals("fresh IV every time", stored, cipher.encrypt(plain)) // GCM with a random IV
    }

    @Test fun realSqliteJoinWritesOneTicketOneOutboxRowAndDoubleTapAddsNone() = runBlocking {
        val repo = JoinRepository(db.ticketDao(), db.outboxDao(), RoomTransactor(db), cipher, UuidV4KeyFactory())
        val results = List(4) { async { repo.join(input) } }.awaitAll()
        assertEquals(1, results.filterIsInstance<JoinResult.Created>().size)
        assertEquals(1, db.ticketDao().count()); assertEquals(1, db.outboxDao().count())
        val row = db.outboxDao().all().single()
        assertFalse(row.payloadEnc.contains("+27"))
        assertTrue(String(cipher.decrypt(row.payloadEnc)).contains("+27821234567"))
    }

    @Test fun transactionRollsBackBothRowsWhenTheSecondInsertFails() = runBlocking {
        val keys = object : com.itmda.swiftq.sync.IdempotencyKeyFactory {
            override fun newKey() = "00000000-0000-4000-8000-000000000001" // local_ref and idempotency key collide? no: different tables
        }
        val repo = JoinRepository(db.ticketDao(), db.outboxDao(), RoomTransactor(db), cipher, UuidV4KeyFactory())
        repo.join(input)
        // Pre-seed a row that makes the NEXT outbox insert violate UNIQUE(idempotency_key).
        val seeded = db.outboxDao().all().single()
        val repo2 = JoinRepository(db.ticketDao(), db.outboxDao(), RoomTransactor(db), cipher, object : com.itmda.swiftq.sync.IdempotencyKeyFactory {
            private var n = 0
            override fun newKey() = if (n++ == 0) java.util.UUID.randomUUID().toString() else seeded.idempotencyKey
        })
        try { repo2.join(input.copy(clinicId = "other")); error("expected a constraint failure") } catch (e: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals("ticket insert must be rolled back", 1, db.ticketDao().count())
        assertEquals(1, db.outboxDao().count())
    }

    @Test fun staleInFlightRowsGoBackToPending() = runBlocking {
        val repo = JoinRepository(db.ticketDao(), db.outboxDao(), RoomTransactor(db), cipher, UuidV4KeyFactory())
        repo.join(input)
        db.outboxDao().markInFlight(db.outboxDao().all().map { it.id }, 1L)
        assertEquals(1, db.outboxDao().resetStaleInFlight(2L))
        assertEquals("pending", db.outboxDao().all().single().state)
    }
}
