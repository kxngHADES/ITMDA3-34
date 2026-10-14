package com.itmda.swiftq.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** JSON is snake_case on the wire; unknown fields are ignored and nulls are left out. */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

@Serializable
data class ItemList<T>(val items: List<T> = emptyList())

@Serializable
data class OpeningHoursSlotDto(val day: String, val opens: String, val closes: String)

@Serializable
data class ClinicDto(
    val id: String,
    val name: String,
    val address: String? = null,
    @SerialName("is_open") val isOpen: Boolean,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("opening_hours") val openingHours: List<OpeningHoursSlotDto>? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ClinicPage(
    val items: List<ClinicDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class ServicePointDto(
    val id: String,
    @SerialName("clinic_id") val clinicId: String,
    val name: String,
    val icon: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("queue_id") val queueId: String? = null,
    @SerialName("concurrent_servers") val concurrentServers: Int = 1,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class WaitRangeDto(
    @SerialName("min_minutes") val minMinutes: Int,
    @SerialName("max_minutes") val maxMinutes: Int,
    @SerialName("cold_start") val coldStart: Boolean,
)

@Serializable
data class TicketPriorityDto(
    @SerialName("requested_category") val requestedCategory: String = "none",
    @SerialName("staff_confirmed") val staffConfirmed: Boolean = false,
)

@Serializable
data class TicketDto(
    val id: String,
    @SerialName("display_number") val displayNumber: String,
    @SerialName("sequence_no") val sequenceNo: Int,
    @SerialName("queue_id") val queueId: String,
    @SerialName("service_point_id") val servicePointId: String,
    @SerialName("clinic_id") val clinicId: String,
    val status: String,
    val channel: String = "app",
    val position: Int? = null,
    @SerialName("people_ahead") val peopleAhead: Int? = null,
    @SerialName("estimated_wait") val estimatedWait: WaitRangeDto? = null,
    @SerialName("counter_label") val counterLabel: String? = null,
    val priority: TicketPriorityDto = TicketPriorityDto(),
    @SerialName("late_flag") val lateFlag: Boolean = false,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("phone_last4") val phoneLast4: String? = null,
    @SerialName("notification_channel") val notificationChannel: String? = null,
    val language: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("called_at") val calledAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
)

/** Body of POST /v1/queues/{queue_id}/tickets and of a `join` outbox payload. Field set must match the server (extra = forbid). */
@Serializable
data class JoinPayload(
    @SerialName("display_name") val displayName: String? = null,
    val phone: String? = null,
    val language: String,
    @SerialName("notification_channel") val notificationChannel: String? = null,
    @SerialName("priority_request") val priorityRequest: String? = null,
    @SerialName("client_created_at") val clientCreatedAt: String,
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int? = null,
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class ErrorEnvelope(val error: ErrorBody)

// --- POST /v1/sync/push -------------------------------------------------------------------------

@Serializable
data class OutboxItemDto(
    @SerialName("idempotency_key") val idempotencyKey: String,
    val op: String,
    @SerialName("queue_id") val queueId: String? = null,
    @SerialName("ticket_id") val ticketId: String? = null,
    @SerialName("local_ref") val localRef: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
    val attempt: Int? = null,
    val payload: JsonElement,
)

@Serializable
data class SyncPushRequest(
    @SerialName("device_id") val deviceId: String,
    val items: List<OutboxItemDto>,
)

@Serializable
data class SyncItemResult(
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("local_ref") val localRef: String? = null,
    val outcome: String,
    val ticket: TicketDto? = null,
    val error: ErrorBody? = null,
)

@Serializable
data class SyncPushResponse(
    val results: List<SyncItemResult>,
    @SerialName("server_time") val serverTime: String? = null,
)

/** One row of GET /v1/queues/{id}. Patients get only number, position and status; staff-only fields are dropped by the API. */
@Serializable
data class QueueEntryDto(
    @SerialName("ticket_id") val ticketId: String,
    @SerialName("display_number") val displayNumber: String,
    val position: Int,
    val status: String,
)

@Serializable
data class QueueDto(
    val id: String,
    val status: String = "open",
    @SerialName("now_serving") val nowServing: String? = null,
    @SerialName("waiting_count") val waitingCount: Int = 0,
    val entries: List<QueueEntryDto> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PushTokenRequestDto(
    @SerialName("device_id") val deviceId: String,
    val token: String,
    // No default: ApiJson skips default values (encodeDefaults = false), and the API requires this field.
    val platform: String,
)
