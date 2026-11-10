package com.swiftq.staff.ui

import android.content.Context
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.swiftq.staff.R
import com.swiftq.staff.model.QueuePriority
import com.swiftq.staff.model.QueueStatus

// Shared styling for priority and status pills on cards and detail screens.
object TicketBadges {

    fun bindPriority(
        badge: TextView,
        priority: QueuePriority,
        category: String?
    ) {
        val context = badge.context
        val categoryLabel = category?.let { categoryLabel(context, it) }

        val (text, background, foreground) = when (priority) {
            QueuePriority.PRIORITY -> Triple(
                categoryLabel
                    ?.let { context.getString(R.string.queue_priority_with_category, it) }
                    ?: context.getString(R.string.queue_priority),
                R.color.warning_tint,
                R.color.warning_text
            )

            QueuePriority.REQUESTED -> Triple(
                context.getString(R.string.queue_priority_requested),
                R.color.swiftq_progress_tint,
                R.color.swiftq_progress_text
            )

            QueuePriority.REGULAR -> Triple(
                context.getString(R.string.queue_regular),
                R.color.blue_100,
                R.color.swiftq_blue_text
            )
        }

        badge.text = text
        tintPill(badge, background, foreground)

        badge.contentDescription = when (priority) {
            QueuePriority.REQUESTED -> context.getString(
                R.string.queue_priority_requested_description,
                categoryLabel ?: context.getString(R.string.queue_priority)
            )

            else -> text
        }
    }

    fun bindStatus(pill: TextView, status: QueueStatus) {
        val (text, background, foreground) = when (status) {
            QueueStatus.WAITING -> Triple(
                R.string.queue_waiting,
                R.color.ticket_waiting_bg,
                R.color.ticket_waiting_fg
            )

            QueueStatus.CALLED -> Triple(
                R.string.queue_called,
                R.color.ticket_called_bg,
                R.color.ticket_called_fg
            )

            QueueStatus.SERVED -> Triple(
                R.string.queue_served,
                R.color.ticket_completed_bg,
                R.color.ticket_completed_fg
            )

            QueueStatus.NO_SHOW -> Triple(
                R.string.queue_no_show,
                R.color.ticket_no_show_bg,
                R.color.ticket_no_show_fg
            )
        }

        pill.setText(text)
        tintPill(pill, background, foreground)
    }

    fun categoryLabel(context: Context, category: String): String {
        return when (category.lowercase()) {
            "elderly" -> context.getString(R.string.priority_category_elderly)
            "pregnant" -> context.getString(R.string.priority_category_pregnant)
            "disability" -> context.getString(R.string.priority_category_disability)
            "urgent" -> context.getString(R.string.priority_category_urgent)
            "none" -> context.getString(R.string.priority_category_none)
            else -> category.replaceFirstChar { it.uppercase() }
        }
    }

    private fun tintPill(view: TextView, backgroundRes: Int, foregroundRes: Int) {
        val context = view.context
        view.setBackgroundResource(R.drawable.bg_pill)
        view.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, backgroundRes)
        )
        view.setTextColor(ContextCompat.getColor(context, foregroundRes))
    }
}
