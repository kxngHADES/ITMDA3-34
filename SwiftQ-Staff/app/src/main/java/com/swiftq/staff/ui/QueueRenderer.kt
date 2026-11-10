package com.swiftq.staff.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.swiftq.staff.R
import com.swiftq.staff.model.QueueStatus
import com.swiftq.staff.model.QueueTicket

class QueueRenderer(
    private val context: Context,
    private val onTicketSelected: (QueueTicket) -> Unit
) {

    private val inflater = LayoutInflater.from(context)

    private val cardSpacing =
        context.resources.getDimensionPixelSize(R.dimen.space_3)

    // Display patient tickets in the container, in rows of `columns`
    // cards so wide screens use the extra space.
    fun render(
        container: LinearLayout,
        tickets: List<QueueTicket>,
        emptyState: View? = null,
        columns: Int = context.resources.getInteger(R.integer.queue_columns)
    ) {
        container.removeAllViews()

        if (tickets.isEmpty()) {
            emptyState?.visibility = View.VISIBLE
            return
        }

        emptyState?.visibility = View.GONE

        val sortedTickets = tickets.sortedWith(QueueTicket.QUEUE_ORDER)

        if (columns <= 1) {
            sortedTickets.forEach { ticket ->
                container.addView(createCard(container, ticket))
            }
            return
        }

        sortedTickets.chunked(columns).forEach { rowTickets ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }

            for (index in 0 until columns) {
                val cell = rowTickets.getOrNull(index)
                    ?.let { createCard(row, it) }
                    ?: Space(context)

                row.addView(
                    cell,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1f
                    ).apply {
                        if (index > 0) marginStart = cardSpacing
                    }
                )
            }

            container.addView(row)
        }
    }

    // Rows for patients currently called to a counter, with the actions
    // staff take once the patient arrives (or does not).
    fun renderCalled(
        container: LinearLayout,
        tickets: List<QueueTicket>,
        onServed: (QueueTicket) -> Unit,
        onNoShow: (QueueTicket) -> Unit
    ) {
        container.removeAllViews()

        tickets.sortedWith(QueueTicket.QUEUE_ORDER).forEach { ticket ->
            val row = inflater.inflate(R.layout.item_called_ticket, container, false)

            row.findViewById<TextView>(R.id.calledTicketNumber).text = ticket.ticketNumber

            row.findViewById<TextView>(R.id.calledPatientName).apply {
                text = ticket.displayName
                    ?: context.getString(R.string.patient_name_not_given)
            }

            TicketBadges.bindPriority(
                row.findViewById(R.id.calledPriorityBadge),
                ticket.priority,
                ticket.priorityCategory
            )

            row.findViewById<MaterialButton>(R.id.calledServedButton)
                .setOnClickListener { onServed(ticket) }

            row.findViewById<MaterialButton>(R.id.calledNoShowButton)
                .setOnClickListener { onNoShow(ticket) }

            row.setOnClickListener { onTicketSelected(ticket) }

            container.addView(row)
        }
    }

    private fun createCard(parent: LinearLayout, ticket: QueueTicket): View {
        val card = inflater.inflate(R.layout.item_patient_queue, parent, false)
        bindTicket(card, ticket)
        return card
    }

    // Bind queue ticket information to its card.
    private fun bindTicket(view: View, ticket: QueueTicket) {
        view.findViewById<TextView>(R.id.ticketNumber).text = ticket.ticketNumber

        view.findViewById<TextView>(R.id.patientName).apply {
            text = ticket.displayName
            visibility = if (ticket.displayName.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        TicketBadges.bindPriority(
            view.findViewById(R.id.priorityBadge),
            ticket.priority,
            ticket.priorityCategory
        )

        TicketBadges.bindStatus(view.findViewById(R.id.ticketStatus), ticket.status)

        view.findViewById<TextView>(R.id.lateBadge).visibility =
            if (ticket.lateFlag) View.VISIBLE else View.GONE

        view.findViewById<TextView>(R.id.ticketMeta).apply {
            val details = listOfNotNull(
                ticket.position
                    ?.takeIf { ticket.status == QueueStatus.WAITING }
                    ?.let { context.getString(R.string.queue_position, it) },
                TimeFormat.clockTime(context, ticket.joinedAt)
                    ?.let { context.getString(R.string.queue_joined_at, it) },
                TimeFormat.waitedMinutes(ticket.joinedAt)
                    ?.takeIf { ticket.status == QueueStatus.WAITING }
                    ?.let { context.getString(R.string.queue_waited_minutes, it) }
            )

            text = details.joinToString(" · ")
            visibility = if (details.isEmpty()) View.GONE else View.VISIBLE
        }

        view.findViewById<View>(R.id.viewTicketButton).setOnClickListener {
            onTicketSelected(ticket)
        }

        // Allow staff to select the whole ticket card.
        view.setOnClickListener {
            onTicketSelected(ticket)
        }
    }
}
