package com.swiftq.staff.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.swiftq.staff.data.remote.model.ServicePoint

@Serializable
data class ServicePointsResponse(
    val items: List<ServicePoint>
)

