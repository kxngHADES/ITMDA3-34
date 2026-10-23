package com.itmda.swiftq.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Everything the network can do, as a value. Raw exceptions never reach the UI or the outbox code.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T, val httpStatus: Int) : ApiResult<T>

    /** The server answered with an error status. [error] is null when the body was not our error envelope. */
    data class Failure(
        val httpStatus: Int,
        val error: ErrorBody?,
        val retryAfterSeconds: Int?,
    ) : ApiResult<Nothing>

    /** No usable answer: offline, DNS, timeout (Render's free tier can cold-start) or a broken body. */
    data class NetworkError(val timedOut: Boolean) : ApiResult<Nothing>
}

/** Runs one Retrofit call that returns the raw body and turns every outcome into an [ApiResult]. */
suspend fun <T> safeApiCall(parse: (String) -> T, call: suspend () -> Response<ResponseBody>): ApiResult<T> =
    try {
        val response = call()
        val text = response.body()?.string() ?: response.errorBody()?.string().orEmpty()
        if (response.isSuccessful) {
            ApiResult.Success(parse(text), response.code())
        } else {
            val retryAfter = response.headers()["Retry-After"]?.toIntOrNull()
            val body = runCatching { ApiJson.decodeFromString(ErrorEnvelope.serializer(), text).error }.getOrNull()
            ApiResult.Failure(response.code(), body, body?.retryAfterSeconds ?: retryAfter)
        }
    } catch (e: SocketTimeoutException) {
        ApiResult.NetworkError(timedOut = true)
    } catch (e: IOException) {
        ApiResult.NetworkError(timedOut = false)
    } catch (e: kotlinx.serialization.SerializationException) {
        // A 2xx we cannot read (for example an HTML page from a proxy): treat it like no answer.
        ApiResult.NetworkError(timedOut = false)
    } catch (e: IllegalArgumentException) {
        ApiResult.NetworkError(timedOut = false)
    }
