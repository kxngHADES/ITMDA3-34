package com.swiftq.staff.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.Response

// Raised when the API rejects a request with a readable error.
class ApiException(
    message: String,
    val httpCode: Int,
    val errorCode: String? = null
) : Exception(message)

// Raised on HTTP 401 so screens can send staff back to sign-in.
class SessionExpiredException(
    message: String
) : Exception(message)

// Raised when the device could not reach the SwiftQ API at all.
class NetworkUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object ApiErrors {

    @Serializable
    private data class ErrorEnvelope(
        val error: ErrorBody? = null
    )

    @Serializable
    private data class ErrorBody(
        val code: String? = null,
        val message: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    // The API returns {"error": {"code": ..., "message": ...}} on failure.
    fun from(response: Response<*>, fallbackMessage: String): Exception {
        val body = runCatching {
            response.errorBody()
                ?.string()
                ?.let { json.decodeFromString<ErrorEnvelope>(it).error }
        }.getOrNull()

        val serverMessage = body?.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (response.code() == 401) {
            return SessionExpiredException(
                serverMessage ?: "Your session has expired. Please sign in again."
            )
        }

        return ApiException(
            message = serverMessage
                ?: "$fallbackMessage (HTTP ${response.code()})",
            httpCode = response.code(),
            errorCode = body?.code
        )
    }
}
