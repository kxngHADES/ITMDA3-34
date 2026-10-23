package com.swiftq.staff.ui

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.swiftq.staff.LoginActivity
import com.swiftq.staff.R

object TicketActionDialogs {

    // Closing a ticket cannot be undone, so staff always confirm it first.
    fun confirmServed(
        context: Context,
        displayNumber: String,
        patientName: String?,
        onConfirm: () -> Unit
    ) {
        val patient = patientName?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.patient_this_patient)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.confirm_served_title, displayNumber))
            .setMessage(context.getString(R.string.confirm_served_message, patient))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_mark_served) { _, _ -> onConfirm() }
            .show()
    }

    fun confirmNoShow(
        context: Context,
        displayNumber: String,
        onConfirm: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.confirm_no_show_title, displayNumber))
            .setMessage(R.string.confirm_no_show_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_mark_no_show) { _, _ -> onConfirm() }
            .show()
    }

    fun showError(context: Context, message: String?) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_failed_title)
            .setMessage(message ?: context.getString(R.string.action_failed_message))
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }
}

object SessionNavigation {

    // Opens sign-in as the only screen in the task, so Back cannot return
    // to queue data after the session ends.
    fun returnToLogin(activity: Activity, message: String? = null) {
        message?.let {
            Toast.makeText(activity.applicationContext, it, Toast.LENGTH_LONG).show()
        }

        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        activity.startActivity(intent)
        activity.finishAffinity()
    }
}
