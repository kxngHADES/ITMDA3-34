package com.swiftq.staff.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfirmPriorityRequest(
    val confirmed: Boolean,

    // Optional correction of the category the patient requested.
    val category: String? = null
)

@Serializable
data class PushTokenRequest(
    @SerialName("device_id")
    val deviceId: String,

    val token: String,

    val platform: String = "android"
)

@Serializable
data class MeResponse(
    val uid: String,
    val role: String,

    @SerialName("clinic_id")
    val clinicId: String? = null,

    @SerialName("display_name")
    val displayName: String? = null
)

@Serializable
data class ClinicsResponse(
    val items: List<ClinicResponse> = emptyList()
)

@Serializable
data class ClinicResponse(
    val id: String,
    val name: String,

    @SerialName("is_active")
    val isActive: Boolean? = null
)
