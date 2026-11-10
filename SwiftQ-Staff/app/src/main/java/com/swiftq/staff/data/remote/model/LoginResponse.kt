package com.swiftq.staff.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("expires_in")
    val expiresIn: Int,

    val user: ApiUser
)

@Serializable
data class ApiUser(
    val id: String,
    val email: String,
    val role: String,

    @SerialName("full_name")
    val fullName: String,

    @SerialName("clinic_id")
    val clinicId: String? = null
)
