package com.swiftq.staff.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueueResponse(
    val id: String,
    @SerialName("service_point_id")
    val servicePointId: String,
    @SerialName("clinic_id")
    val clinicId: String,
    @SerialName("service_date")
    val serviceDate: String,
    @SerialName("display_prefix")
    val displayPrefix: String,
    val status: String,
    @SerialName("pause_reason")
    val pauseReason: String? = null,
    @SerialName("now_serving")
    val nowServing: String? = null,
    @SerialName("waiting_count")
    val waitingCount: Int = 0,
    val entries: List<QueueEntryResponse> = emptyList(),
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("concurrent_servers")
    val concurrentServers: Int? = null,
    @SerialName("counter_labels")
    val counterLabels: List<String> = emptyList(),
    @SerialName("opened_by")
    val openedBy: String? = null,
    @SerialName("opened_at")
    val openedAt: String? = null,
    @SerialName("base_service_seconds")
    val baseServiceSeconds: Int? = null,
    val note: String? = null,
    @SerialName("closed_at")
    val closedAt: String? = null
)

@Serializable
data class QueueEntryResponse(
    @SerialName("ticket_id")
    val ticketId: String,
    @SerialName("display_number")
    val displayNumber: String,
    val position: Int,
    val status: String,
    @SerialName("is_priority")
    val isPriority: Boolean = false,
    @SerialName("late_flag")
    val lateFlag: Boolean = false,
    @SerialName("joined_at")
    val joinedAt: String? = null,
    val protected: Boolean? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("priority_category")
    val priorityCategory: String? = null,
    @SerialName("staff_confirmed")
    val staffConfirmed: Boolean? = null
)