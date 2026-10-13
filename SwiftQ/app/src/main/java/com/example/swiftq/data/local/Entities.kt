package com.example.swiftq.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local ticket statuses. `pending_sync` and `failed` exist only on the device; the rest mirror the API's TicketStatus. */
object TicketState {
    const val PENDING_SYNC = "pending_sync"
    const val WAITING = "waiting"
    const val CALLED = "called"
    const val SERVED = "served"
    const val NO_SHOW = "no_show"
    const val CANCELLED = "cancelled"
    const val FAILED = "failed"

    /** The patient still holds a place (mirrors the server's "active" = waiting/called, plus our unsent join). */
    val ACTIVE = listOf(PENDING_SYNC, WAITING, CALLED)
}

/** Outbox row states (Chapter 3 §3.3.2). */
object OutboxState {
    const val PENDING = "pending"
    const val IN_FLIGHT = "in_flight"
    const val DONE = "done"
    const val REJECTED = "rejected"
    const val CONFLICT_PENDING = "conflict_pending"
    const val EXPIRED = "expired"
}

/** SyncOp values, same strings as the API. */
object OutboxOp {
    const val JOIN = "join"
    const val UPDATE_TICKET = "update_ticket"
    const val CANCEL = "cancel"
}

@Entity(tableName = "clinics")
data class ClinicEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String?,
    @ColumnInfo(name = "is_open") val isOpen: Boolean,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "opening_hours_json") val openingHoursJson: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

@Entity(
    tableName = "service_points",
    foreignKeys = [
        ForeignKey(
            entity = ClinicEntity::class,
            parentColumns = ["id"],
            childColumns = ["clinic_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clinic_id")],
)
data class ServicePointEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "clinic_id") val clinicId: String,
    val name: String,
    val icon: String?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    /** Null means no queue is open today. */
    @ColumnInfo(name = "queue_id") val queueId: String?,
    @ColumnInfo(name = "concurrent_servers", defaultValue = "1") val concurrentServers: Int = 1,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/**
 * The patient's own tickets. The row exists before the server knows about the ticket:
 * [localRef] is a UUIDv4 made on the device when Join is tapped, [serverId] fills in later.
 */
@Entity(
    tableName = "tickets",
    indices = [
        Index(value = ["server_id"], unique = true),
        Index(value = ["clinic_id", "status"]),
    ],
)
data class TicketEntity(
    @PrimaryKey @ColumnInfo(name = "local_ref") val localRef: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "queue_id") val queueId: String,
    @ColumnInfo(name = "service_point_id") val servicePointId: String,
    @ColumnInfo(name = "clinic_id") val clinicId: String,
    @ColumnInfo(name = "display_number") val displayNumber: String? = null,
    @ColumnInfo(name = "sequence_no") val sequenceNo: Int? = null,
    val status: String,
    @ColumnInfo(defaultValue = "app") val channel: String = "app",
    val position: Int? = null,
    @ColumnInfo(name = "people_ahead") val peopleAhead: Int? = null,
    @ColumnInfo(name = "wait_min_minutes") val waitMinMinutes: Int? = null,
    @ColumnInfo(name = "wait_max_minutes") val waitMaxMinutes: Int? = null,
    @ColumnInfo(name = "wait_cold_start") val waitColdStart: Boolean? = null,
    @ColumnInfo(name = "counter_label") val counterLabel: String? = null,
    @ColumnInfo(name = "priority_requested", defaultValue = "none") val priorityRequested: String = "none",
    @ColumnInfo(name = "priority_confirmed", defaultValue = "0") val priorityConfirmed: Boolean = false,
    @ColumnInfo(name = "late_flag", defaultValue = "0") val lateFlag: Boolean = false,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    /** AES-GCM ciphertext (Android Keystore). Never plain text. */
    @ColumnInfo(name = "phone_enc") val phoneEnc: String? = null,
    @ColumnInfo(name = "phone_last4") val phoneLast4: String? = null,
    /** Alert address when the patient picked Email. AES-GCM ciphertext like [phoneEnc]; never sent to the API. */
    @ColumnInfo(name = "email_enc") val emailEnc: String? = null,
    @ColumnInfo(name = "notification_channel") val notificationChannel: String,
    val language: String,
    @ColumnInfo(name = "client_created_at") val clientCreatedAt: String,
    @ColumnInfo(name = "created_at") val createdAt: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "called_at") val calledAt: String? = null,
    @ColumnInfo(name = "closed_at") val closedAt: String? = null,
    /** Epoch ms of the last successful server answer; drives "last updated" (P5). */
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null,
    /** P8: history screen can hide a closed ticket without deleting it. */
    @ColumnInfo(name = "hidden_from_history", defaultValue = "0") val hiddenFromHistory: Boolean = false,
)

/** One pending write. Flushed strictly in [id] order. */
@Entity(
    tableName = "outbox",
    foreignKeys = [
        ForeignKey(
            entity = TicketEntity::class,
            parentColumns = ["local_ref"],
            childColumns = ["local_ref"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["state", "next_attempt_at"]),
        Index(value = ["local_ref"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** UUIDv4, created once per patient intent and reused by every retry. */
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    val op: String,
    @ColumnInfo(name = "queue_id") val queueId: String? = null,
    /** Server id, or the local_ref of a join the server has not confirmed yet. */
    @ColumnInfo(name = "ticket_id") val ticketId: String? = null,
    @ColumnInfo(name = "local_ref") val localRef: String,
    /** The exact serialized JSON body, AES-GCM encrypted (it can hold a phone number). */
    @ColumnInfo(name = "payload_enc") val payloadEnc: String,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: String,
    val state: String,
    @ColumnInfo(defaultValue = "0") val attempt: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "last_error_message") val lastErrorMessage: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Key/value store: device_id, pull_cursor, last_pull_at. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String?,
)

object SyncKeys {
    const val DEVICE_ID = "device_id"
    const val PULL_CURSOR = "pull_cursor"
    const val LAST_PULL_AT = "last_pull_at"
}
