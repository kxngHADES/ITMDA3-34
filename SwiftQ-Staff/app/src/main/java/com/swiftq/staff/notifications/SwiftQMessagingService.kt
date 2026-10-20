package com.swiftq.staff.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.swiftq.staff.data.remote.PushTokenRegistrar
import com.swiftq.staff.data.remote.StaffSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SwiftQMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (!StaffSessionManager(applicationContext).hasSession()) return

        serviceScope.launch {
            PushTokenRegistrar(applicationContext).register(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Never show clinic data on a device that has signed out.
        if (!StaffSessionManager(applicationContext).hasSession()) return

        val data = message.data
        val type = (data["type"] ?: data["event"]).orEmpty().lowercase()
        val category = data["priority_category"]
            ?: data["requested_category"]
            ?: data["category"]

        Log.d(TAG, "Push received: type=$type")

        if ("priority" in type || category != null) {
            PriorityAlerts.notifyPriorityRequest(
                context = applicationContext,
                ticketId = data["ticket_id"],
                displayNumber = data["display_number"],
                category = category,
                patientName = data["display_name"]
            )
            return
        }

        val title = message.notification?.title ?: data["title"]
        val body = message.notification?.body ?: data["body"]

        if (title != null && body != null) {
            PriorityAlerts.notifyGeneral(applicationContext, title, body)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SwiftQPush"
    }
}
