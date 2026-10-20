package com.swiftq.staff.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.swiftq.staff.data.remote.model.PushTokenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// Links this device's FCM token to the signed-in staff account so the
// API can push priority requests to it.
class PushTokenRegistrar(context: Context) {

    private val appContext = context.applicationContext

    private val devicePreferences = appContext.getSharedPreferences(
        "swiftq_device",
        Context.MODE_PRIVATE
    )

    // Stable install id. It survives logout so the API can replace the
    // previous token for this device rather than adding a new one.
    val deviceId: String
        get() {
            devicePreferences.getString(KEY_DEVICE_ID, null)?.let { return it }

            val created = UUID.randomUUID().toString()
            devicePreferences.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    fun registerCurrentToken(scope: CoroutineScope) {
        val messaging = runCatching { FirebaseMessaging.getInstance() }
            .getOrElse { error ->
                Log.w(TAG, "Firebase Messaging is unavailable", error)
                return
            }

        messaging.token
            .addOnSuccessListener { token ->
                scope.launch { register(token) }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to read the FCM token", error)
            }
    }

    suspend fun register(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (TokenManager(appContext).getToken().isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No staff session is active.")
            )
        }

        try {
            val response = AuthenticatedApiClient.create(appContext)
                .registerPushToken(
                    PushTokenRequest(
                        deviceId = deviceId,
                        token = token
                    )
                )

            if (response.isSuccessful) {
                Log.d(TAG, "Push token registered")
                Result.success(Unit)
            } else {
                Log.w(TAG, "Push token rejected: HTTP ${response.code()}")
                Result.failure(
                    ApiErrors.from(response, "Unable to register for alerts.")
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Push token registration failed", exception)
            Result.failure(exception)
        }
    }

    // Must run before the session token is cleared.
    suspend fun unregister(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = AuthenticatedApiClient.create(appContext)
                .removePushToken(deviceId)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    ApiErrors.from(response, "Unable to remove the push token.")
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    companion object {
        private const val TAG = "SwiftQPush"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
