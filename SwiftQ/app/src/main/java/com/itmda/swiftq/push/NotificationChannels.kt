package com.itmda.swiftq.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.itmda.swiftq.R

object NotificationChannels {
    /** Also the default channel in the manifest, so notifications FCM draws itself land here. */
    const val TURN = "swiftq_turn"
    const val UPDATES = "swiftq_updates"

    /** Safe to call on every start: creating an existing channel changes nothing. */
    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(TURN, context.getString(R.string.channel_turn_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_turn_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 150, 400, 150, 400)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(UPDATES, context.getString(R.string.channel_updates_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_updates_description)
            }
        )
    }
}
