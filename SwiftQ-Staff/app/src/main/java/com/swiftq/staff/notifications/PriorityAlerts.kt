package com.swiftq.staff.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.swiftq.staff.PatientDetailActivity
import com.swiftq.staff.QueueDashboardActivity
import com.swiftq.staff.R
import com.swiftq.staff.ui.TicketBadges

object PriorityAlerts {

    private const val CHANNEL_ID = "priority_requests"
    private const val GENERAL_NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_priority),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(
                R.string.notification_channel_priority_description
            )
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        return permissionGranted &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // One notification per ticket, so a push and the in-app poll for the
    // same request replace each other rather than stacking.
    fun notifyPriorityRequest(
        context: Context,
        ticketId: String?,
        displayNumber: String?,
        category: String?,
        patientName: String? = null
    ) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val ticketLabel = displayNumber
            ?: context.getString(R.string.notification_unknown_ticket)

        val categoryLabel = category
            ?.let { TicketBadges.categoryLabel(context, it) }

        val body = listOfNotNull(
            patientName?.takeIf { it.isNotBlank() },
            categoryLabel?.let {
                context.getString(R.string.notification_priority_category, it)
            }
        ).joinToString(" · ")
            .ifEmpty { context.getString(R.string.notification_priority_body) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_priority)
            .setColor(ContextCompat.getColor(context, R.color.dashboard_priority))
            .setContentTitle(
                context.getString(R.string.notification_priority_title, ticketLabel)
            )
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$body\n" + context.getString(R.string.notification_priority_action)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, ticketId, displayNumber))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                ticketId?.hashCode() ?: GENERAL_NOTIFICATION_ID,
                notification
            )
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }

    fun notifyGeneral(context: Context, title: String, body: String) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_priority)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, null, null))
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(GENERAL_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun buildContentIntent(
        context: Context,
        ticketId: String?,
        displayNumber: String?
    ): PendingIntent? {
        val stack = TaskStackBuilder.create(context)
            .addNextIntent(
                android.content.Intent(context, QueueDashboardActivity::class.java)
            )

        if (!ticketId.isNullOrBlank()) {
            stack.addNextIntent(
                PatientDetailActivity.intent(context, ticketId, displayNumber)
            )
        }

        return stack.getPendingIntent(
            ticketId?.hashCode() ?: GENERAL_NOTIFICATION_ID,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
