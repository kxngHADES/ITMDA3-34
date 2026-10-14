package com.itmda.swiftq.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.itmda.swiftq.MainActivity
import com.itmda.swiftq.R
import com.itmda.swiftq.SwiftQApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/**
 * Receives the API's FCM pushes. Payload (api/services/notification_service.py):
 * `notification {title, body}` + `data {kind, ticket_id, queue_id}`.
 *
 *  - App in the background: for a message with a `notification` block, FCM draws it itself on the "swiftq_turn" channel
 *    (manifest default) and this is not called. A data-only message does reach [onMessageReceived], where it is drawn here.
 *  - App open: nothing is drawn (the screen is already showing the queue). The patient gets haptic + sound, and the
 *    ticket is refreshed straight away instead of waiting for the next poll.
 * Settings > Notifications > Alerts off means nothing is shown (and the token has been removed from the API as well).
 */
class SwiftQMessagingService : FirebaseMessagingService() {
    private val container get() = (application as SwiftQApp).container

    override fun onNewToken(token: String) {
        container.scope.launch { container.pushRegistrar.onNewToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val cue = Cue.fromPushKind(message.data["kind"]) ?: return
        if (!container.settings.alertsEnabled) return
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            container.feedback.play(cue)
            container.scope.launch { container.refreshActiveTicket() }
        } else {
            show(cue, message)
        }
    }

    private fun show(cue: Cue, message: RemoteMessage) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val settings = container.settings
        val (titleRes, bodyRes) = when (cue) {
            Cue.YourTurn -> R.string.push_turn_title to R.string.push_turn_body
            else -> R.string.push_almost_title to R.string.push_almost_body
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, if (cue == Cue.YourTurn) NotificationChannels.TURN else NotificationChannels.UPDATES)
            .setSmallIcon(R.drawable.ic_stat_swiftq)
            // The server's text names the ticket and counter; ours is the language-matched fallback.
            .setContentTitle(message.notification?.title ?: getString(titleRes))
            .setContentText(message.notification?.body ?: getString(bodyRes))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (cue == Cue.YourTurn) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(!settings.soundEnabled && !settings.vibrateEnabled)
            .build()
        getSystemService(android.app.NotificationManager::class.java).notify(message.data["ticket_id"].hashCode(), notification)
    }
}
