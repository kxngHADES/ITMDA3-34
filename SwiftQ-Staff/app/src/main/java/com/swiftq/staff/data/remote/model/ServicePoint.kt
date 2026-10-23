package com.swiftq.staff.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServicePoint(
    val id: String,
    @SerialName("clinic_id")
    val clinicId: String,
    val name: String,
    val icon: String? = null,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("queue_id")
    val queueId: String? = null,
    @SerialName("concurrent_servers")
    val concurrentServers: Int
)