package com.itmda.swiftq.push

import com.itmda.swiftq.data.local.SyncKeys
import com.itmda.swiftq.data.local.SyncStateDao
import com.itmda.swiftq.data.local.SyncStateEntity
import com.itmda.swiftq.data.remote.ApiJson
import com.itmda.swiftq.data.remote.ApiResult
import com.itmda.swiftq.data.remote.PushTokenRequestDto
import com.itmda.swiftq.data.remote.SwiftQApi
import com.itmda.swiftq.data.remote.safeApiCall
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Keeps the API's `push_tokens` entry for this install in line with what the patient wants:
 *  - alerts on: send the current FCM token (PUT /v1/me/push-token) whenever it differs from the one the API last accepted;
 *  - alerts off: remove it (DELETE), so the Notification Manager skips push and falls back to SMS or email.
 * Cheap to call often (app start, foreground, a new token, a background sync): it does nothing when already in line.
 * A failed call is not recorded as done, so the next call retries it.
 */
class PushTokenRegistrar(
    private val api: SwiftQApi,
    private val syncState: SyncStateDao,
    private val deviceId: suspend () -> String,
    private val alertsEnabled: () -> Boolean,
    /** The current FCM token, or null when push is not set up on this build or phone. */
    private val currentToken: suspend () -> String?,
) {
    // App start, foreground and the Settings toggle can all call sync() at once; one at a time means one PUT.
    private val lock = Mutex()

    suspend fun sync() = lock.withLock {
        val registered = syncState.get(SyncKeys.PUSH_TOKEN)
        if (!alertsEnabled()) {
            if (registered != null) remove()
            return@withLock
        }
        val token = currentToken()
        if (token == null) { Log.w(TAG, "No FCM token (Firebase not set up on this build, or Google Play services could not issue one)"); return@withLock }
        if (token != registered) register(token)
    }

    /** Firebase issued a new token (first run, reinstall, or rotation). */
    suspend fun onNewToken(token: String) {
        if (alertsEnabled() && token != syncState.get(SyncKeys.PUSH_TOKEN)) register(token)
    }

    /** Sign-out style cleanup: the API stops pushing to this device. */
    suspend fun unregister() { if (syncState.get(SyncKeys.PUSH_TOKEN) != null) remove() }

    /** The user ID changed (sign in or out). Tokens are stored per user, so send it again even if it is the same token. */
    suspend fun reRegister() {
        syncState.put(SyncStateEntity(SyncKeys.PUSH_TOKEN, null))
        sync()
    }

    private suspend fun register(token: String) {
        val body = ApiJson.encodeToString(PushTokenRequestDto.serializer(), PushTokenRequestDto(deviceId(), token, platform = "android"))
        val result = safeApiCall({ }) { api.registerPushToken(body.toRequestBody(JSON)) }
        when (result) {
            is ApiResult.Success -> { syncState.put(SyncStateEntity(SyncKeys.PUSH_TOKEN, token)); Log.i(TAG, "Push token registered with the API") }
            is ApiResult.Failure -> Log.w(TAG, "API refused the push token: HTTP ${result.httpStatus} ${result.error?.code} ${result.error?.message}")
            is ApiResult.NetworkError -> Log.w(TAG, "Push token not sent: no answer from the API")
        }
    }

    private suspend fun remove() {
        val result = safeApiCall({ }) { api.removePushToken(deviceId()) }
        if (result is ApiResult.Success) syncState.put(SyncStateEntity(SyncKeys.PUSH_TOKEN, null))
    }

    private companion object {
        const val TAG = "SwiftQPush"
        val JSON = "application/json".toMediaType()
    }
}
