package com.swiftq.staff

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.swiftq.staff.data.remote.AuthenticatedApiClient
import com.swiftq.staff.data.remote.QueueApiRepository
import com.swiftq.staff.data.remote.ServicePointsRepository
import com.swiftq.staff.data.remote.SessionExpiredException
import com.swiftq.staff.data.remote.StaffSessionManager
import com.swiftq.staff.data.remote.model.TicketResponse
import com.swiftq.staff.model.QueuePriority
import com.swiftq.staff.model.QueueStatus
import com.swiftq.staff.ui.SessionNavigation
import com.swiftq.staff.ui.TicketActionDialogs
import com.swiftq.staff.ui.TicketBadges
import com.swiftq.staff.ui.TimeFormat
import com.swiftq.staff.ui.applySystemBarInsets
import kotlinx.coroutines.launch

class PatientDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TICKET_ID = "ticket_id"
        private const val EXTRA_DISPLAY_NUMBER = "display_number"

        fun intent(context: Context, ticketId: String, displayNumber: String?): Intent {
            return Intent(context, PatientDetailActivity::class.java)
                .putExtra(EXTRA_TICKET_ID, ticketId)
                .putExtra(EXTRA_DISPLAY_NUMBER, displayNumber)
        }
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var ticketNumber: TextView
    private lateinit var patientName: TextView
    private lateinit var statusPill: TextView
    private lateinit var priorityPill: TextView
    private lateinit var priorityReviewCard: View
    private lateinit var priorityReviewMessage: TextView
    private lateinit var confirmPriorityButton: MaterialButton
    private lateinit var declinePriorityButton: MaterialButton
    private lateinit var detailRows: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var actionsBar: View
    private lateinit var servedButton: MaterialButton
    private lateinit var noShowButton: MaterialButton

    private lateinit var repository: QueueApiRepository

    private lateinit var ticketId: String
    private var ticket: TicketResponse? = null
    private var isBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!StaffSessionManager(applicationContext).hasSession()) {
            SessionNavigation.returnToLogin(this)
            return
        }

        val id = intent.getStringExtra(EXTRA_TICKET_ID)
        if (id.isNullOrBlank()) {
            finish()
            return
        }
        ticketId = id

        setContentView(R.layout.activity_patient_detail)
        bindViews()

        val api = AuthenticatedApiClient.create(this)
        repository = QueueApiRepository(api, ServicePointsRepository(api))

        intent.getStringExtra(EXTRA_DISPLAY_NUMBER)?.let {
            ticketNumber.text = it
            toolbar.title = getString(R.string.patient_detail_title_number, it)
        }

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        swipeRefresh.setColorSchemeResources(R.color.action_primary)
        swipeRefresh.setOnRefreshListener { loadTicket() }

        servedButton.setOnClickListener { confirmServed() }
        noShowButton.setOnClickListener { confirmNoShow() }
        confirmPriorityButton.setOnClickListener { confirmPriority() }
        declinePriorityButton.setOnClickListener { declinePriority() }

        loadTicket()
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.detailToolbar)
        swipeRefresh = findViewById(R.id.detailSwipeRefresh)
        ticketNumber = findViewById(R.id.detailTicketNumber)
        patientName = findViewById(R.id.detailPatientName)
        statusPill = findViewById(R.id.detailStatus)
        priorityPill = findViewById(R.id.detailPriority)
        priorityReviewCard = findViewById(R.id.priorityReviewCard)
        priorityReviewMessage = findViewById(R.id.priorityReviewMessage)
        confirmPriorityButton = findViewById(R.id.confirmPriorityButton)
        declinePriorityButton = findViewById(R.id.declinePriorityButton)
        detailRows = findViewById(R.id.detailRows)
        errorText = findViewById(R.id.detailError)
        progress = findViewById(R.id.detailProgress)
        actionsBar = findViewById(R.id.detailActions)
        servedButton = findViewById(R.id.detailServedButton)
        noShowButton = findViewById(R.id.detailNoShowButton)

        findViewById<View>(R.id.detailRoot).applySystemBarInsets(top = true, bottom = false)
        actionsBar.applySystemBarInsets(bottom = true)
        // When the action bar is hidden, the scroll area needs the gesture-bar
        // padding instead.
        findViewById<View>(R.id.detailContent).applySystemBarInsets(bottom = true)
    }

    private fun loadTicket() {
        if (ticket == null) progress.visibility = View.VISIBLE
        errorText.visibility = View.GONE

        lifecycleScope.launch {
            val result = repository.getTicket(ticketId)

            progress.visibility = View.GONE
            swipeRefresh.isRefreshing = false

            result
                .onSuccess { bind(it) }
                .onFailure { error ->
                    if (error is SessionExpiredException) {
                        expireSession(error.message)
                        return@onFailure
                    }

                    errorText.text = error.message ?: getString(R.string.patient_detail_load_failed)
                    errorText.visibility = View.VISIBLE
                }
        }
    }

    private fun bind(loaded: TicketResponse) {
        ticket = loaded

        val number = loaded.displayNumber ?: intent.getStringExtra(EXTRA_DISPLAY_NUMBER).orEmpty()
        ticketNumber.text = number
        toolbar.title = getString(R.string.patient_detail_title_number, number)

        patientName.text = loaded.displayName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.patient_name_not_given)

        val status = QueueApiRepository.statusOf(loaded.status)

        if (status != null) {
            TicketBadges.bindStatus(statusPill, status)
            statusPill.visibility = View.VISIBLE
        } else {
            // e.g. "cancelled", which the queue list does not show.
            statusPill.text = loaded.status
                ?.replace('_', ' ')
                ?.replaceFirstChar { it.uppercase() }
            statusPill.visibility = if (loaded.status == null) View.GONE else View.VISIBLE
        }

        val requestedCategory = loaded.priority?.requestedCategory
        val priority = QueueApiRepository.priorityOf(
            category = requestedCategory,
            staffConfirmed = loaded.priority?.staffConfirmed == true
        )
        TicketBadges.bindPriority(
            priorityPill,
            priority,
            requestedCategory?.takeIf { it != "none" }
        )

        val isOpen = status == QueueStatus.WAITING || status == QueueStatus.CALLED

        // Priority review is only for open tickets with an unconfirmed request.
        val needsReview = priority == QueuePriority.REQUESTED && isOpen
        priorityReviewCard.visibility = if (needsReview) View.VISIBLE else View.GONE

        if (needsReview && requestedCategory != null) {
            priorityReviewMessage.text = getString(
                R.string.priority_review_message,
                TicketBadges.categoryLabel(this, requestedCategory)
            )
        }

        // Served / no-show apply once the patient has been called.
        actionsBar.visibility = if (status == QueueStatus.CALLED) View.VISIBLE else View.GONE

        renderDetailRows(loaded, status)
        setBusy(false)
    }

    private fun renderDetailRows(loaded: TicketResponse, status: QueueStatus?) {
        detailRows.removeAllViews()

        val rows = buildList {
            if (status == QueueStatus.WAITING) {
                loaded.position?.let {
                    add(getString(R.string.detail_position) to it.toString())
                }
                loaded.peopleAhead?.let {
                    add(getString(R.string.detail_people_ahead) to it.toString())
                }
                loaded.estimatedWait?.let { wait ->
                    add(
                        getString(R.string.detail_estimated_wait) to
                                getString(R.string.detail_wait_range, wait.minMinutes, wait.maxMinutes)
                    )
                }
            }

            loaded.counterLabel?.let { add(getString(R.string.detail_counter) to it) }

            TimeFormat.clockTime(this@PatientDetailActivity, loaded.createdAt)?.let {
                add(getString(R.string.detail_joined) to it)
            }
            TimeFormat.clockTime(this@PatientDetailActivity, loaded.calledAt)?.let {
                add(getString(R.string.detail_called) to it)
            }
            TimeFormat.clockTime(this@PatientDetailActivity, loaded.closedAt)?.let {
                add(getString(R.string.detail_closed) to it)
            }

            loaded.channel?.let { add(getString(R.string.detail_joined_via) to channelLabel(it)) }
            loaded.notificationChannel?.let {
                add(getString(R.string.detail_alerts) to channelLabel(it))
            }
            loaded.language?.let { add(getString(R.string.detail_language) to it.uppercase()) }
            loaded.phoneLast4?.let {
                add(getString(R.string.detail_phone) to getString(R.string.detail_phone_masked, it))
            }

            if (loaded.lateFlag) {
                add(getString(R.string.detail_late) to getString(R.string.detail_yes))
            }
            if (loaded.noShowCount > 0) {
                add(getString(R.string.detail_no_shows) to loaded.noShowCount.toString())
            }

            loaded.priority?.takeIf { it.staffConfirmed }?.confirmedAt
                ?.let { TimeFormat.clockTime(this@PatientDetailActivity, it) }
                ?.let { add(getString(R.string.detail_priority_confirmed) to it) }
        }

        val inflater = LayoutInflater.from(this)

        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                detailRows.addView(
                    View(this).apply {
                        setBackgroundResource(R.color.neutral_border)
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                )
            }

            val row = inflater.inflate(R.layout.item_detail_row, detailRows, false)
            row.findViewById<TextView>(R.id.detailRowLabel).text = label
            row.findViewById<TextView>(R.id.detailRowValue).text = value
            detailRows.addView(row)
        }
    }

    private fun channelLabel(channel: String): String {
        return when (channel.lowercase()) {
            "app" -> getString(R.string.channel_app)
            "sms" -> getString(R.string.walk_in_channel_sms)
            "staff_assisted" -> getString(R.string.channel_staff_assisted)
            "whatsapp" -> getString(R.string.walk_in_channel_whatsapp)
            "email" -> getString(R.string.walk_in_channel_email)
            "push" -> getString(R.string.channel_push)
            "none" -> getString(R.string.walk_in_channel_none)
            else -> channel
        }
    }

    // ---------------------------------------------------------
    // ACTIONS
    // ---------------------------------------------------------

    private fun confirmServed() {
        val current = ticket ?: return

        TicketActionDialogs.confirmServed(
            this,
            current.displayNumber.orEmpty(),
            current.displayName
        ) {
            runAction(getString(R.string.served_success, current.displayNumber.orEmpty())) {
                repository.completeTicket(ticketId)
            }
        }
    }

    private fun confirmNoShow() {
        val current = ticket ?: return

        TicketActionDialogs.confirmNoShow(this, current.displayNumber.orEmpty()) {
            runAction(getString(R.string.no_show_success, current.displayNumber.orEmpty())) {
                repository.markTicketNoShow(ticketId)
            }
        }
    }

    // Staff may correct the category the patient chose before confirming.
    private fun confirmPriority() {
        val current = ticket ?: return
        val categories = QueueApiRepository.PRIORITY_CATEGORIES.filter { it != "none" }
        val labels = categories.map { TicketBadges.categoryLabel(this, it) }.toTypedArray()
        var choice = categories.indexOf(current.priority?.requestedCategory).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.priority_confirm_title)
            .setSingleChoiceItems(labels, choice) { _, index -> choice = index }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm_priority) { _, _ ->
                val category = categories[choice]

                runAction(getString(R.string.priority_confirmed_success)) {
                    repository.confirmPriority(
                        ticketId = ticketId,
                        confirmed = true,
                        category = category.takeIf { it != current.priority?.requestedCategory }
                    )
                }
            }
            .show()
    }

    private fun declinePriority() {
        val current = ticket ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.priority_decline_title, current.displayNumber.orEmpty()))
            .setMessage(R.string.priority_decline_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_decline_priority) { _, _ ->
                runAction(getString(R.string.priority_declined_success)) {
                    repository.confirmPriority(ticketId = ticketId, confirmed = false)
                }
            }
            .show()
    }

    private fun runAction(
        successMessage: String,
        action: suspend () -> Result<TicketResponse>
    ) {
        if (isBusy) return
        setBusy(true)

        lifecycleScope.launch {
            action()
                .onSuccess { updated ->
                    Toast.makeText(this@PatientDetailActivity, successMessage, Toast.LENGTH_SHORT).show()

                    // Some actions return a partial ticket, so re-read it.
                    if (updated.id != null && updated.displayNumber != null) bind(updated)
                    loadTicket()
                }
                .onFailure { error ->
                    setBusy(false)

                    if (error is SessionExpiredException) {
                        expireSession(error.message)
                    } else {
                        TicketActionDialogs.showError(this@PatientDetailActivity, error.message)
                    }
                }
        }
    }

    private fun setBusy(busy: Boolean) {
        isBusy = busy
        servedButton.isEnabled = !busy
        noShowButton.isEnabled = !busy
        confirmPriorityButton.isEnabled = !busy
        declinePriorityButton.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun expireSession(message: String?) {
        StaffSessionManager(applicationContext).endSession()
        SessionNavigation.returnToLogin(this, message ?: getString(R.string.session_expired))
    }
}
