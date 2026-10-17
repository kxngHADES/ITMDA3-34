package com.itmda.swiftq.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of POST /v1/auth/patients/register. The password goes out exactly as typed (no trimming). */
@Serializable
data class PatientRegisterRequest(
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
    val language: String,
    @SerialName("notification_channel") val notificationChannel: String,
    val phone: String? = null,
    @SerialName("priority_request") val priorityRequest: String? = null,
)

@Serializable
data class PatientLoginRequest(val email: String, val password: String)

@Serializable
data class PatientPriorityDto(
    val category: String = "none",
    val verified: Boolean = false,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("valid_until") val validUntil: String? = null, // null = permanent
    val active: Boolean = false, // verified and not expired
)

@Serializable
data class PatientProfileDto(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    val language: String,
    @SerialName("notification_channel") val notificationChannel: String,
    @SerialName("phone_last4") val phoneLast4: String? = null,
    val priority: PatientPriorityDto = PatientPriorityDto(),
)

/** What register, login and refresh all return. */
@Serializable
data class PatientAuthDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val patient: PatientProfileDto,
)
