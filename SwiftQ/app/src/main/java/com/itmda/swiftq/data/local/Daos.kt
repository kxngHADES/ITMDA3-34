package com.itmda.swiftq.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ClinicDao {
    @Query("SELECT * FROM clinics ORDER BY name")
    fun observeAll(): Flow<List<ClinicEntity>>

    @Query("SELECT * FROM clinics WHERE id = :id")
    suspend fun find(id: String): ClinicEntity?

    // Upsert (not REPLACE) so a refresh never deletes the clinic row and cascades its service points away.
    @Upsert
    suspend fun upsertAll(clinics: List<ClinicEntity>)

    @Query("DELETE FROM clinics WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<String>)

    @Query("DELETE FROM clinics")
    suspend fun deleteAll()
}

@Dao
interface ServicePointDao {
    @Query("SELECT * FROM service_points WHERE clinic_id = :clinicId ORDER BY name")
    fun observeForClinic(clinicId: String): Flow<List<ServicePointEntity>>

    @Query("SELECT * FROM service_points WHERE id = :id")
    suspend fun find(id: String): ServicePointEntity?

    @Upsert
    suspend fun upsertAll(servicePoints: List<ServicePointEntity>)

    @Query("DELETE FROM service_points WHERE clinic_id = :clinicId AND id NOT IN (:keepIds)")
    suspend fun deleteForClinicNotIn(clinicId: String, keepIds: List<String>)
}

@Dao
interface TicketDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(ticket: TicketEntity)

    @Update
    suspend fun update(ticket: TicketEntity)

    @Delete
    suspend fun delete(ticket: TicketEntity)

    @Query("SELECT * FROM tickets WHERE local_ref = :localRef")
    suspend fun find(localRef: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE local_ref = :localRef")
    fun observe(localRef: String): Flow<TicketEntity?>

    @Query("SELECT * FROM tickets WHERE server_id = :serverId")
    suspend fun findByServerId(serverId: String): TicketEntity?

    /** The ticket the patient is currently holding, if any (newest first). */
    @Query(
        "SELECT * FROM tickets WHERE status IN ('pending_sync','waiting','called') " +
            "ORDER BY client_created_at DESC LIMIT 1",
    )
    fun observeActive(): Flow<TicketEntity?>

    @Query(
        "SELECT * FROM tickets WHERE status IN ('pending_sync','waiting','called') " +
            "ORDER BY client_created_at DESC LIMIT 1",
    )
    suspend fun findActive(): TicketEntity?

    /** Duplicate check (T-03.2): one active ticket per patient per clinic. */
    @Query(
        "SELECT * FROM tickets WHERE clinic_id = :clinicId AND status IN ('pending_sync','waiting','called') " +
            "ORDER BY client_created_at DESC LIMIT 1",
    )
    suspend fun findActiveAtClinic(clinicId: String): TicketEntity?

    @Query("SELECT COUNT(*) FROM tickets")
    suspend fun count(): Int

    /** Finished tickets the patient has not removed, newest first. Read from SQLite, so history works offline. */
    @Query(
        "SELECT * FROM tickets WHERE status IN ('served','no_show','cancelled','failed') AND hidden_from_history = 0 " +
            "ORDER BY client_created_at DESC",
    )
    fun observeHistory(): Flow<List<TicketEntity>>

    /** Hides a finished ticket from history. The row stays until cleanup, so nothing else that points at it breaks. */
    @Query("UPDATE tickets SET hidden_from_history = 1 WHERE local_ref = :localRef AND status IN ('served','no_show','cancelled','failed')")
    suspend fun hide(localRef: String)

    @Query("UPDATE tickets SET hidden_from_history = 1 WHERE status IN ('served','no_show','cancelled','failed')")
    suspend fun hideAllFinished(): Int

    /**
     * Deletes finished tickets that ended before [cutoffIso] (ISO-8601, compared as text). A ticket that is still
     * active is never touched, whatever its age.
     */
    @Query(
        "DELETE FROM tickets WHERE status IN ('served','no_show','cancelled','failed') " +
            "AND COALESCE(closed_at, updated_at) < :cutoffIso",
    )
    suspend fun deleteFinishedBefore(cutoffIso: String): Int
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: OutboxEntity): Long

    @Update
    suspend fun update(entry: OutboxEntity)

    /** Rows due now, oldest write first. Always flush in this order. */
    @Query("SELECT * FROM outbox WHERE state = 'pending' AND next_attempt_at <= :now ORDER BY id LIMIT :limit")
    suspend fun nextBatch(now: Long, limit: Int = 50): List<OutboxEntity>

    @Query("UPDATE outbox SET state = 'in_flight', updated_at = :now WHERE id IN (:ids)")
    suspend fun markInFlight(ids: List<Long>, now: Long)

    /** The process may have died mid-request, so anything still in flight at start goes back to pending. */
    @Query("UPDATE outbox SET state = 'pending', updated_at = :now WHERE state = 'in_flight'")
    suspend fun resetStaleInFlight(now: Long): Int

    @Query("SELECT * FROM outbox WHERE state = 'pending' ORDER BY id")
    suspend fun allPending(): List<OutboxEntity>

    @Query("SELECT MIN(next_attempt_at) FROM outbox WHERE state = 'pending'")
    suspend fun earliestPendingAttempt(): Long?

    @Query("SELECT * FROM outbox WHERE idempotency_key = :key")
    suspend fun findByKey(key: String): OutboxEntity?

    @Query("SELECT * FROM outbox ORDER BY id")
    suspend fun all(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE local_ref = :localRef AND op = 'join' ORDER BY id DESC LIMIT 1")
    fun observeJoinFor(localRef: String): Flow<OutboxEntity?>

    @Query("UPDATE outbox SET local_ref = :to, ticket_id = :ticketId, updated_at = :now WHERE local_ref = :from")
    suspend fun repoint(from: String, to: String, ticketId: String?, now: Long)

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    /**
     * Deletes entries that are over (sent, refused or past the 24 h window) and last changed before [cutoffMs].
     * pending, in_flight and conflict_pending entries are never deleted: they still have work to do.
     */
    @Query("DELETE FROM outbox WHERE state IN ('done','rejected','expired') AND updated_at < :cutoffMs")
    suspend fun deleteFinishedBefore(cutoffMs: Long): Int
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: SyncStateEntity)
}
