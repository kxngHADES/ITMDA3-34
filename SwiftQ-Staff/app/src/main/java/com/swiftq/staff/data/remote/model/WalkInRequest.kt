package com.swiftq.staff.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalkInRequest(
    @SerialName("display_name")
    val displayName: String? = null,

    val phone: String? = null,

    val email: String? = null,

    val language: String,

    @SerialName("notification_channel")
    val notificationChannel: String,

    @SerialName("priority_category")
    val priorityCategory: String = "none"
)
