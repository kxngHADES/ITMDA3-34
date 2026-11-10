package com.swiftq.staff.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TicketResponse(
    val id: String? = null,

    @SerialName("display_number")
    val displayNumber: String? = null,

    @SerialName("sequence_no")
    val sequenceNo: Int? = null,

    val status: String? = null,

    @SerialName("queue_id")
    val queueId: String? = null,

    @SerialName("service_point_id")
    val servicePointId: String? = null,

    val channel: String? = null,

    val position: Int? = null,

    @SerialName("people_ahead")
    val peopleAhead: Int? = null,

    @SerialName("estimated_wait")
    val estimatedWait: WaitRangeResponse? = null,

    @SerialName("counter_label")
    val counterLabel: String? = null,

    val priority: TicketPriorityResponse? = null,

    @SerialName("late_flag")
    val lateFlag: Boolean = false,

    @SerialName("no_show_count")
    val noShowCount: Int = 0,

    @SerialName("display_name")
    val displayName: String? = null,

    @SerialName("phone_last4")
    val phoneLast4: String? = null,

    @SerialName("notification_channel")
    val notificationChannel: String? = null,

    val language: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("called_at")
    val calledAt: String? = null,

    @SerialName("closed_at")
    val closedAt: String? = null
)

@Serializable
data class TicketPriorityResponse(
    @SerialName("requested_category")
    val requestedCategory: String? = null,

    @SerialName("staff_confirmed")
    val staffConfirmed: Boolean = false,

    @SerialName("confirmed_by")
    val confirmedBy: String? = null,

    @SerialName("confirmed_at")
    val confirmedAt: String? = null
)

@Serializable
data class WaitRangeResponse(
    @SerialName("min_minutes")
    val minMinutes: Int,

    @SerialName("max_minutes")
    val maxMinutes: Int,

    @SerialName("cold_start")
    val coldStart: Boolean = false
)
