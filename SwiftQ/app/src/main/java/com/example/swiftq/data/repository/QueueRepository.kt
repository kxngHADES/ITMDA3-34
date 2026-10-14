package com.example.swiftq.data.repository

import com.example.swiftq.data.remote.ApiJson
import com.example.swiftq.data.remote.ApiResult
import com.example.swiftq.data.remote.QueueDto
import com.example.swiftq.data.remote.SwiftQApi
import com.example.swiftq.data.remote.safeApiCall

sealed interface QueueFetch {
    data class Fresh(val queue: QueueDto, val etag: String?) : QueueFetch
    data object NotModified : QueueFetch
    /** Offline, or the server could not answer. The caller keeps showing what it already has. */
    data object Unavailable : QueueFetch
}

/** Reads the live queue the patient is standing in (GET /v1/queues/{id}). Not stored: it is only worth showing fresh. */
class QueueRepository(private val api: SwiftQApi) {
    suspend fun fetch(queueId: String, etag: String? = null): QueueFetch {
        var newEtag: String? = null
        val result = safeApiCall({ ApiJson.decodeFromString(QueueDto.serializer(), it) }) {
            api.getQueue(queueId, etag).also { newEtag = it.headers()["ETag"] }
        }
        return when (result) {
            is ApiResult.Success -> QueueFetch.Fresh(result.value, newEtag)
            is ApiResult.Failure -> if (result.httpStatus == 304) QueueFetch.NotModified else QueueFetch.Unavailable
            is ApiResult.NetworkError -> QueueFetch.Unavailable
        }
    }
}
