package com.swiftq.staff.data.remote

import android.content.Context
import com.swiftq.staff.data.QueueCache
import com.swiftq.staff.notifications.PriorityAlerts
import com.swiftq.staff.notifications.PriorityRequestTracker
import kotlinx.coroutines.withTimeoutOrNull

class StaffSessionManager(context: Context) {

    private val appContext = context.applicationContext

    private val tokenManager = TokenManager(appContext)

    fun hasSession(): Boolean {
        return !tokenManager.getToken().isNullOrBlank()
    }

    // Stops push alerts for this device, then clears every trace of the
    // session: token, staff profile, cached queue and pending alerts.
    suspend fun signOut() {
        if (hasSession()) {
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
                PushTokenRegistrar(appContext).unregister()
            }
        }

        endSession()
    }

    fun endSession() {
        tokenManager.clearSession()
        QueueCache(appContext).clear()
        PriorityRequestTracker(appContext).clear()
        PriorityAlerts.cancelAll(appContext)
    }

    companion object {
        private const val UNREGISTER_TIMEOUT_MS = 3_000L
    }
}
