package com.swiftq.staff

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.swiftq.staff.data.QueueCache
import com.swiftq.staff.data.remote.AuthenticatedApiClient
import com.swiftq.staff.data.remote.ClinicResolver
import com.swiftq.staff.data.remote.NetworkUnavailableException
import com.swiftq.staff.data.remote.PushTokenRegistrar
import com.swiftq.staff.data.remote.QueueApiRepository
import com.swiftq.staff.data.remote.ServicePointsRepository
import com.swiftq.staff.data.remote.SessionExpiredException
import com.swiftq.staff.data.remote.StaffSessionManager
import com.swiftq.staff.data.remote.TokenManager
import com.swiftq.staff.data.remote.model.QueueResponse
import com.swiftq.staff.data.remote.model.ServicePoint
import com.swiftq.staff.data.remote.model.TicketResponse
import com.swiftq.staff.model.QueuePriority
import com.swiftq.staff.model.QueueStatus
import com.swiftq.staff.model.QueueTicket
import com.swiftq.staff.notifications.PriorityAlerts
import com.swiftq.staff.notifications.PriorityRequestTracker
import com.swiftq.staff.notifications.isPendingPriorityRequest
import com.swiftq.staff.ui.QueueRenderer
import com.swiftq.staff.ui.SessionNavigation
import com.swiftq.staff.ui.TicketActionDialogs
import com.swiftq.staff.ui.TicketBadges
import com.swiftq.staff.ui.TimeFormat
import com.swiftq.staff.ui.WalkInSheet
import com.swiftq.staff.ui.applySystemBarInsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class QueueDashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SwiftQDashboard"

        // How often the open dashboard re-checks the queue. Unchanged
        // queues answer 304, so frequent polling stays cheap.
        private const val REFRESH_INTERVAL_MS = 10_000L

        private const val UP_NEXT_LIMIT = 3

        private const val STATE_TAB = "dashboard_tab"
        private const val STATE_FILTER = "queue_filter"
    }

    private enum class DashboardTab { OVERVIEW, QUEUE }

    private enum class QueueFilter { ALL, WAITING, CALLED, PRIORITY }

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var dashboardScroll: NestedScrollView

    private lateinit var greetingText: TextView
    private lateinit var liveIndicator: TextView
    private lateinit var queueSyncStatus: TextView
    private lateinit var servicePointButton: MaterialButton
    private lateinit var refreshQueueButton: MaterialButton
    private lateinit var lastUpdatedValue: TextView
    private lateinit var offlineBanner: TextView

    private lateinit var overviewSection: View
    private lateinit var waitingValue: TextView
    private lateinit var priorityValue: TextView
    private lateinit var requestsValue: TextView
    private lateinit var nowServingValue: TextView
    private lateinit var nowServingHint: TextView
    private lateinit var calledContainer: LinearLayout
    private lateinit var callNextButton: MaterialButton
    private lateinit var addWalkInButton: MaterialButton
    private lateinit var priorityRequestsSection: View
    private lateinit var priorityRequestsContainer: LinearLayout
    private lateinit var upNextContainer: LinearLayout
    private lateinit var upNextEmpty: View

    private lateinit var queueSection: View
    private lateinit var queueFilterGroup: ChipGroup
    private lateinit var queueContainer: LinearLayout
    private lateinit var emptyQueueState: View
    private lateinit var emptyQueueTitle: TextView
    private lateinit var emptyQueueDescription: TextView

    private lateinit var navOverview: View
    private lateinit var navQueue: View
    private lateinit var navWalkin: View
    private lateinit var navSettings: View

    private lateinit var queueRenderer: QueueRenderer
    private lateinit var queueApiRepository: QueueApiRepository
    private lateinit var clinicResolver: ClinicResolver
    private lateinit var queueCache: QueueCache
    private lateinit var priorityTracker: PriorityRequestTracker
    private lateinit var tokenManager: TokenManager

    private var clinicId: String? = null
    private var currentQueue: QueueResponse? = null
    private var currentTickets: List<QueueTicket> = emptyList()
    private var availableServicePoints: List<ServicePoint> = emptyList()

    private var selectedServicePointId: String? = null
    private var selectedServicePointName: String? = null
    private var queueEtag: String? = null
    private var lastSyncedAt: Long? = null
    private var isShowingCachedData = false

    private var currentTab = DashboardTab.OVERVIEW
    private var queueFilter = QueueFilter.ALL

    private var queueRefreshJob: Job? = null
    private var isQueueLoading = false
    private var isActionInProgress = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    R.string.notification_permission_denied,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent access to the dashboard without a stored session.
        if (!StaffSessionManager(applicationContext).hasSession()) {
            SessionNavigation.returnToLogin(this)
            return
        }

        Log.d(TAG, "Opening SwiftQ Queue Dashboard")
        setContentView(R.layout.activity_queue_dashboard)

        initialiseViews()
        applyWindowInsets()
        initialiseQueueApi()

        queueRenderer = QueueRenderer(this) { selectedTicket ->
            openPatientDetail(selectedTicket)
        }

        savedInstanceState?.let { state ->
            currentTab = state.getString(STATE_TAB)
                ?.let { runCatching { DashboardTab.valueOf(it) }.getOrNull() }
                ?: DashboardTab.OVERVIEW
            queueFilter = state.getString(STATE_FILTER)
                ?.let { runCatching { QueueFilter.valueOf(it) }.getOrNull() }
                ?: QueueFilter.ALL
        }

        showInitialState()
        setupBottomNavigation()
        setupQueueControls()
        setupQueueFilters()
        restoreCachedQueue()
        showTab(currentTab, scrollToTop = false)

        setupPriorityAlerts()

        Log.d(TAG, "Dashboard initialisation completed")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, currentTab.name)
        outState.putString(STATE_FILTER, queueFilter.name)
    }

    // Live updates: poll while the dashboard is visible.
    override fun onStart() {
        super.onStart()

        if (!::queueApiRepository.isInitialized) return
        if (queueRefreshJob?.isActive == true) return

        queueRefreshJob = lifecycleScope.launch {
            while (isActive) {
                syncQueue()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onStop() {
        queueRefreshJob?.cancel()
        queueRefreshJob = null
        super.onStop()
    }

    private fun initialiseQueueApi() {
        val api = AuthenticatedApiClient.create(this)
        val servicePointsRepository = ServicePointsRepository(api)

        tokenManager = TokenManager(applicationContext)
        clinicResolver = ClinicResolver(api, tokenManager)
        queueCache = QueueCache(applicationContext)
        priorityTracker = PriorityRequestTracker(applicationContext)

        queueApiRepository = QueueApiRepository(
            api = api,
            servicePointsRepository = servicePointsRepository
        )
    }

    private fun <T : View> requireDashboardView(id: Int): T {
        return findViewById<T>(id)
            ?: throw IllegalStateException(
                "Missing dashboard view: " +
                        resources.getResourceEntryName(id)
            )
    }

    private fun initialiseViews() {
        swipeRefresh = requireDashboardView(R.id.swipeRefresh)
        dashboardScroll = requireDashboardView(R.id.dashboardScroll)

        greetingText = requireDashboardView(R.id.greetingText)
        liveIndicator = requireDashboardView(R.id.liveIndicator)
        queueSyncStatus = requireDashboardView(R.id.queueSyncStatus)
        servicePointButton = requireDashboardView(R.id.servicePointButton)
        refreshQueueButton = requireDashboardView(R.id.refreshQueueButton)
        lastUpdatedValue = requireDashboardView(R.id.lastUpdatedValue)
        offlineBanner = requireDashboardView(R.id.offlineBanner)

        overviewSection = requireDashboardView(R.id.overviewSection)
        waitingValue = requireDashboardView(R.id.waitingValue)
        priorityValue = requireDashboardView(R.id.priorityValue)
        requestsValue = requireDashboardView(R.id.requestsValue)
        nowServingValue = requireDashboardView(R.id.nowServingValue)
        nowServingHint = requireDashboardView(R.id.nowServingHint)
        calledContainer = requireDashboardView(R.id.calledContainer)
        callNextButton = requireDashboardView(R.id.callNextButton)
        addWalkInButton = requireDashboardView(R.id.addWalkInButton)
        priorityRequestsSection = requireDashboardView(R.id.priorityRequestsSection)
        priorityRequestsContainer = requireDashboardView(R.id.priorityRequestsContainer)
        upNextContainer = requireDashboardView(R.id.upNextContainer)
        upNextEmpty = requireDashboardView(R.id.upNextEmpty)

        queueSection = requireDashboardView(R.id.queueSection)
        queueFilterGroup = requireDashboardView(R.id.queueFilterGroup)
        queueContainer = requireDashboardView(R.id.queueContainer)
        emptyQueueState = requireDashboardView(R.id.emptyQueueState)
        emptyQueueTitle = requireDashboardView(R.id.emptyQueueTitle)
        emptyQueueDescription = requireDashboardView(R.id.emptyQueueDescription)

        navOverview = requireDashboardView(R.id.navOverview)
        navQueue = requireDashboardView(R.id.navQueue)
        navWalkin = requireDashboardView(R.id.navWalkin)
        navSettings = requireDashboardView(R.id.navSettings)

        Log.d(TAG, "Dashboard views initialised")
    }

    // The app draws edge to edge: keep the header clear of the status bar
    // and the navigation clear of the gesture bar.
    private fun applyWindowInsets() {
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        requireDashboardView<View>(R.id.dashboardRoot)
            .applySystemBarInsets(top = true, horizontal = false)
        requireDashboardView<View>(R.id.swipeRefresh)
            .applySystemBarInsets(horizontal = true)
        requireDashboardView<View>(R.id.bottomNavigation)
            .applySystemBarInsets(bottom = true)
    }

    private fun showInitialState() {
        val firstName = tokenManager.getStaffName()
            ?.trim()
            ?.substringBefore(' ')
            ?.takeIf { it.isNotEmpty() }

        greetingText.text = firstName
            ?.let { getString(R.string.dashboard_greeting_named, it) }
            ?: getString(R.string.dashboard_greeting)

        waitingValue.setText(R.string.dashboard_count_unavailable)
        priorityValue.setText(R.string.dashboard_count_unavailable)
        requestsValue.setText(R.string.dashboard_count_unavailable)

        queueSyncStatus.setText(R.string.dashboard_awaiting_sync)
        lastUpdatedValue.setText(R.string.dashboard_not_synced)
        servicePointButton.setText(R.string.dashboard_select_service_point)

        renderQueue()
    }

    // ---------------------------------------------------------
    // CONTROLS
    // ---------------------------------------------------------

    private fun setupQueueControls() {
        swipeRefresh.setColorSchemeResources(R.color.action_primary)
        swipeRefresh.setOnRefreshListener { refreshNow(forceServicePoints = true) }

        servicePointButton.setOnClickListener {
            lifecycleScope.launch {
                if (availableServicePoints.isEmpty()) {
                    val clinic = resolveClinicId() ?: return@launch
                    if (!loadServicePointOptions(clinic)) return@launch
                }

                showServicePointSelector()
            }
        }

        refreshQueueButton.setOnClickListener { refreshNow(forceServicePoints = true) }

        callNextButton.setOnClickListener { onCallNextClicked() }
        addWalkInButton.setOnClickListener { openWalkInForm() }

        requireDashboardView<View>(R.id.viewFullQueueButton).setOnClickListener {
            showTab(DashboardTab.QUEUE)
        }
    }

    private fun setupQueueFilters() {
        val chipForFilter = mapOf(
            QueueFilter.ALL to R.id.filterAll,
            QueueFilter.WAITING to R.id.filterWaiting,
            QueueFilter.CALLED to R.id.filterCalled,
            QueueFilter.PRIORITY to R.id.filterPriority
        )

        queueFilterGroup.check(chipForFilter.getValue(queueFilter))

        queueFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener

            queueFilter = chipForFilter.entries
                .firstOrNull { it.value == checkedId }
                ?.key
                ?: QueueFilter.ALL

            renderQueueList()
        }
    }

    private fun refreshNow(forceServicePoints: Boolean = false) {
        lifecycleScope.launch {
            queueEtag = null
            syncQueue(forceServicePoints = forceServicePoints)
        }
    }

    // ---------------------------------------------------------
    // SYNC (live updates + offline cache)
    // ---------------------------------------------------------

    private suspend fun resolveClinicId(): String? {
        clinicId?.let { return it }

        return clinicResolver.resolve()
            .onSuccess { clinicId = it }
            .onFailure { error ->
                Log.e(TAG, "Clinic lookup failed", error)
                handleSyncFailure(error)
            }
            .getOrNull()
    }

    private suspend fun syncQueue(forceServicePoints: Boolean = false) {
        if (isQueueLoading) return

        isQueueLoading = true
        refreshQueueButton.isEnabled = false

        try {
            val clinic = resolveClinicId() ?: return

            val selected = selectedServicePoint()

            // Service points are re-read when the selected one has no open
            // queue yet, since staff may open it from another device.
            val needsServicePoints = forceServicePoints ||
                    availableServicePoints.isEmpty() ||
                    selected?.queueId.isNullOrBlank()

            if (needsServicePoints && !loadServicePointOptions(clinic)) return

            val servicePoint = selectedServicePoint()

            if (servicePoint == null) {
                queueSyncStatus.setText(R.string.dashboard_select_service_point)
                return
            }

            val queueId = servicePoint.queueId

            if (queueId.isNullOrBlank()) {
                showNoOpenQueue(servicePoint)
                return
            }

            if (lastSyncedAt == null) {
                queueSyncStatus.setText(R.string.queue_loading)
            }

            val etag = queueEtag.takeIf { currentQueue?.id == queueId }

            queueApiRepository.loadQueue(queueId, etag)
                .onSuccess { fetch ->
                    val now = System.currentTimeMillis()

                    when (fetch) {
                        is QueueApiRepository.QueueFetch.Updated -> {
                            queueEtag = fetch.etag

                            applyQueue(
                                queue = fetch.queue,
                                syncedAt = now,
                                fromCache = false
                            )

                            queueCache.save(
                                QueueCache.Snapshot(
                                    clinicId = clinic,
                                    servicePointId = servicePoint.id,
                                    servicePointName = servicePoint.name,
                                    queue = fetch.queue,
                                    syncedAtMillis = now
                                )
                            )

                            alertForNewPriorityRequests(fetch.queue.id)
                        }

                        QueueApiRepository.QueueFetch.NotModified -> {
                            queueCache.markSynced(now)
                            markOnline(now)
                        }
                    }

                    Log.d(TAG, "Queue successfully refreshed")
                }
                .onFailure { error ->
                    Log.e(TAG, "Queue request failed", error)
                    handleSyncFailure(error)
                }
        } finally {
            isQueueLoading = false
            refreshQueueButton.isEnabled = true
            swipeRefresh.isRefreshing = false
        }
    }

    private suspend fun loadServicePointOptions(clinic: String): Boolean {
        servicePointButton.isEnabled = false

        return try {
            val points = queueApiRepository.loadServicePoints(clinic)
                .getOrElse { error ->
                    Log.e(TAG, "Service-point request failed", error)
                    handleSyncFailure(error)
                    return false
                }

            availableServicePoints = points

            if (points.none { it.id == selectedServicePointId }) {
                selectedServicePointId = (
                        points.firstOrNull { !it.queueId.isNullOrBlank() }
                            ?: points.firstOrNull()
                        )?.id
            }

            selectedServicePoint()?.let {
                selectedServicePointName = it.name
                servicePointButton.text = it.name
            }

            if (points.isEmpty()) {
                queueSyncStatus.setText(R.string.dashboard_no_service_points)
                return false
            }

            true
        } finally {
            servicePointButton.isEnabled = true
        }
    }

    private fun selectedServicePoint(): ServicePoint? {
        return availableServicePoints.firstOrNull { it.id == selectedServicePointId }
    }

    private fun showServicePointSelector() {
        if (availableServicePoints.isEmpty()) {
            Toast.makeText(this, R.string.dashboard_no_service_points, Toast.LENGTH_SHORT).show()
            return
        }

        val names = availableServicePoints.map { point ->
            if (point.queueId.isNullOrBlank()) {
                getString(R.string.dashboard_service_point_closed, point.name)
            } else {
                point.name
            }
        }.toTypedArray()

        val checked = availableServicePoints.indexOfFirst { it.id == selectedServicePointId }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dashboard_select_service_point)
            .setSingleChoiceItems(names, checked) { dialog, index ->
                val selected = availableServicePoints[index]

                if (selected.id != selectedServicePointId) {
                    selectedServicePointId = selected.id
                    selectedServicePointName = selected.name
                    servicePointButton.text = selected.name

                    // Never show one service point's patients under another.
                    currentQueue = null
                    currentTickets = emptyList()
                    queueEtag = null
                    lastSyncedAt = null
                    renderQueue()
                    refreshNow()
                }

                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showNoOpenQueue(servicePoint: ServicePoint) {
        currentQueue = null
        currentTickets = emptyList()
        queueEtag = null
        renderQueue()

        queueSyncStatus.setText(R.string.queue_status_not_open)
        nowServingHint.text = getString(R.string.dashboard_queue_not_open, servicePoint.name)
        markOnline(System.currentTimeMillis())
    }

    private fun applyQueue(queue: QueueResponse, syncedAt: Long, fromCache: Boolean) {
        currentQueue = queue
        currentTickets = queueApiRepository.mapTickets(queue)

        renderQueue()

        queueSyncStatus.text = getString(
            when (queue.status.lowercase()) {
                "open" -> R.string.queue_status_open
                "paused" -> R.string.queue_status_paused
                "closed" -> R.string.queue_status_closed
                else -> R.string.queue_status_unknown
            }
        )

        if (fromCache) {
            lastSyncedAt = syncedAt
            isShowingCachedData = true
            updateLastUpdatedLabel()
            showOfflineBanner(getString(R.string.offline_banner_connecting, lastUpdatedText()))
        } else {
            markOnline(syncedAt)
        }
    }

    private fun markOnline(syncedAt: Long) {
        lastSyncedAt = syncedAt
        isShowingCachedData = false
        offlineBanner.visibility = View.GONE
        liveIndicator.setText(R.string.dashboard_live)
        updateLastUpdatedLabel()
    }

    private fun handleSyncFailure(error: Throwable) {
        if (error is SessionExpiredException) {
            expireSession(error.message)
            return
        }

        isShowingCachedData = currentQueue != null
        liveIndicator.setText(R.string.dashboard_staff_portal)

        val reason = if (error is NetworkUnavailableException) {
            getString(R.string.offline_reason_no_connection)
        } else {
            error.message ?: getString(R.string.queue_connection_error)
        }

        if (currentQueue != null && lastSyncedAt != null) {
            queueSyncStatus.setText(R.string.queue_status_offline)
            showOfflineBanner(
                getString(R.string.offline_banner_cached, reason, lastUpdatedText())
            )
        } else {
            queueSyncStatus.setText(R.string.queue_connection_error)
            showOfflineBanner(reason)
        }

        updateLastUpdatedLabel()
    }

    private fun showOfflineBanner(message: String) {
        offlineBanner.text = message
        offlineBanner.visibility = View.VISIBLE
    }

    private fun lastUpdatedText(): String {
        return lastSyncedAt
            ?.let { TimeFormat.lastUpdated(this, it) }
            ?: getString(R.string.dashboard_not_synced)
    }

    private fun updateLastUpdatedLabel() {
        lastUpdatedValue.text = lastUpdatedText()
    }

    // Show the last saved queue straight away, before the network answers.
    private fun restoreCachedQueue() {
        val snapshot = queueCache.load() ?: return
        val savedClinic = tokenManager.getClinicId()

        if (savedClinic != null && savedClinic != snapshot.clinicId) return

        clinicId = snapshot.clinicId
        selectedServicePointId = snapshot.servicePointId
        selectedServicePointName = snapshot.servicePointName
        servicePointButton.text = snapshot.servicePointName

        applyQueue(
            queue = snapshot.queue,
            syncedAt = snapshot.syncedAtMillis,
            fromCache = true
        )
    }

    // ---------------------------------------------------------
    // RENDERING
    // ---------------------------------------------------------

    private fun renderQueue() {
        val queue = currentQueue
        val active = currentTickets.filter {
            it.status == QueueStatus.WAITING || it.status == QueueStatus.CALLED
        }
        val waiting = active.filter { it.status == QueueStatus.WAITING }
        val called = active.filter { it.status == QueueStatus.CALLED }
        val pendingRequests = active.filter { it.isPendingPriorityRequest() }

        // Stats
        waitingValue.text = queue?.waitingCount?.toString()
            ?: getString(R.string.dashboard_count_unavailable)

        priorityValue.text = if (queue == null) {
            getString(R.string.dashboard_count_unavailable)
        } else {
            waiting.count { it.priority == QueuePriority.PRIORITY }.toString()
        }

        requestsValue.text = if (queue == null) {
            getString(R.string.dashboard_count_unavailable)
        } else {
            pendingRequests.size.toString()
        }

        // Now serving
        nowServingValue.text = queue?.nowServing
            ?: getString(R.string.dashboard_count_unavailable)

        nowServingHint.text = when {
            queue == null -> getString(R.string.dashboard_now_serving_hint)
            queue.status.equals("paused", ignoreCase = true) ->
                queue.pauseReason
                    ?.let { getString(R.string.dashboard_queue_paused_reason, it) }
                    ?: getString(R.string.queue_status_paused)

            called.isEmpty() && waiting.isEmpty() -> getString(R.string.queue_empty)
            called.isEmpty() -> resources.getQuantityString(
                R.plurals.dashboard_waiting_hint,
                waiting.size,
                waiting.size
            )

            else -> getString(R.string.dashboard_called_hint)
        }

        queueRenderer.renderCalled(
            container = calledContainer,
            tickets = called,
            onServed = ::confirmServed,
            onNoShow = ::confirmNoShow
        )

        val queueOpen = queue?.status.equals("open", ignoreCase = true)
        callNextButton.isEnabled = queueOpen && waiting.isNotEmpty() && !isActionInProgress
        addWalkInButton.isEnabled = queue != null && !isActionInProgress

        // Priority requests
        priorityRequestsSection.visibility =
            if (pendingRequests.isEmpty()) View.GONE else View.VISIBLE
        queueRenderer.render(priorityRequestsContainer, pendingRequests)

        // Up next (waiting patients only, in queue order)
        val upNext = waiting.sortedWith(QueueTicket.QUEUE_ORDER).take(UP_NEXT_LIMIT)
        queueRenderer.render(upNextContainer, upNext, upNextEmpty)

        renderQueueList()
        updateNavigationLabels()
    }

    private fun renderQueueList() {
        val filtered = when (queueFilter) {
            QueueFilter.ALL -> currentTickets
            QueueFilter.WAITING -> currentTickets.filter { it.status == QueueStatus.WAITING }
            QueueFilter.CALLED -> currentTickets.filter { it.status == QueueStatus.CALLED }
            QueueFilter.PRIORITY -> currentTickets.filter {
                it.priority != QueuePriority.REGULAR
            }
        }

        if (currentQueue == null) {
            emptyQueueTitle.setText(R.string.dashboard_empty_title)
            emptyQueueDescription.setText(R.string.dashboard_empty_description)
        } else {
            emptyQueueTitle.setText(R.string.queue_empty)
            emptyQueueDescription.setText(R.string.queue_empty_filter_description)
        }

        queueRenderer.render(queueContainer, filtered, emptyQueueState)

        updateFilterChipCounts()
    }

    private fun updateFilterChipCounts() {
        fun label(textRes: Int, count: Int) =
            if (currentQueue == null) getString(textRes) else "${getString(textRes)} ($count)"

        findViewById<Chip>(R.id.filterAll).text =
            label(R.string.filter_all, currentTickets.size)
        findViewById<Chip>(R.id.filterWaiting).text =
            label(R.string.filter_waiting, currentTickets.count { it.status == QueueStatus.WAITING })
        findViewById<Chip>(R.id.filterCalled).text =
            label(R.string.filter_called, currentTickets.count { it.status == QueueStatus.CALLED })
        findViewById<Chip>(R.id.filterPriority).text =
            label(R.string.filter_priority, currentTickets.count { it.priority != QueuePriority.REGULAR })
    }

    // ---------------------------------------------------------
    // CALL NEXT / SERVED / NO-SHOW
    // ---------------------------------------------------------

    private fun onCallNextClicked() {
        val queue = currentQueue ?: return
        val labels = queue.counterLabels.filter { it.isNotBlank() }

        when (labels.size) {
            0 -> callNext(queue.id, null)
            1 -> callNext(queue.id, labels.first())
            else -> {
                val remembered = queueCache.getCounterLabel()
                var choice = labels.indexOf(remembered).coerceAtLeast(0)

                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.call_next_counter_title)
                    .setSingleChoiceItems(labels.toTypedArray(), choice) { _, index ->
                        choice = index
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_call_next) { _, _ ->
                        val label = labels[choice]
                        queueCache.saveCounterLabel(label)
                        callNext(queue.id, label)
                    }
                    .show()
            }
        }
    }

    private fun callNext(queueId: String, counterLabel: String?) {
        runTicketAction(
            action = { queueApiRepository.callNextPatient(queueId, counterLabel) }
        ) { ticket ->
            callNextButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            val number = ticket.displayNumber ?: getString(R.string.notification_unknown_ticket)
            val details = listOfNotNull(
                ticket.displayName,
                (ticket.counterLabel ?: counterLabel)
                    ?.let { getString(R.string.call_next_counter, it) },
                ticket.priority
                    ?.takeIf { it.staffConfirmed }
                    ?.requestedCategory
                    ?.takeIf { it != "none" }
                    ?.let { TicketBadges.categoryLabel(this, it) }
            ).joinToString("\n")

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.call_next_result_title, number))
                .setMessage(details.ifEmpty { getString(R.string.call_next_result_message) })
                .setPositiveButton(R.string.action_ok, null)
                .apply {
                    ticket.id?.let { ticketId ->
                        setNeutralButton(R.string.queue_view_details) { _, _ ->
                            startActivity(
                                PatientDetailActivity.intent(this@QueueDashboardActivity, ticketId, number)
                            )
                        }
                    }
                }
                .show()
        }
    }

    private fun confirmServed(ticket: QueueTicket) {
        TicketActionDialogs.confirmServed(this, ticket.ticketNumber, ticket.displayName) {
            runTicketAction(
                action = { queueApiRepository.completeTicket(ticket.id) }
            ) {
                Toast.makeText(
                    this,
                    getString(R.string.served_success, ticket.ticketNumber),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun confirmNoShow(ticket: QueueTicket) {
        TicketActionDialogs.confirmNoShow(this, ticket.ticketNumber) {
            runTicketAction(
                action = { queueApiRepository.markTicketNoShow(ticket.id) }
            ) {
                Toast.makeText(
                    this,
                    getString(R.string.no_show_success, ticket.ticketNumber),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Runs one staff action at a time, then refreshes the queue so every
    // count and list reflects the change.
    private fun runTicketAction(
        action: suspend () -> Result<TicketResponse>,
        onSuccess: (TicketResponse) -> Unit
    ) {
        if (isActionInProgress) return

        isActionInProgress = true
        renderQueue()

        lifecycleScope.launch {
            val result = action()

            isActionInProgress = false

            result
                .onSuccess { ticket ->
                    onSuccess(ticket)
                    queueEtag = null
                    syncQueue()
                }
                .onFailure { error ->
                    renderQueue()

                    if (error is SessionExpiredException) {
                        expireSession(error.message)
                    } else {
                        TicketActionDialogs.showError(this@QueueDashboardActivity, error.message)
                    }
                }
        }
    }

    // ---------------------------------------------------------
    // WALK-IN
    // ---------------------------------------------------------

    private fun openWalkInForm() {
        val queue = currentQueue

        if (queue == null) {
            Toast.makeText(this, R.string.walk_in_no_queue, Toast.LENGTH_LONG).show()
            return
        }

        WalkInSheet(
            activity = this,
            queueId = queue.id,
            servicePointName = selectedServicePointName.orEmpty(),
            repository = queueApiRepository,
            onRegistered = { ticket ->
                val number = ticket.displayNumber ?: getString(R.string.notification_unknown_ticket)

                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.walk_in_success_title, number))
                    .setMessage(
                        ticket.position
                            ?.let { getString(R.string.walk_in_success_position, it) }
                            ?: getString(R.string.walk_in_success_message)
                    )
                    .setPositiveButton(R.string.action_ok, null)
                    .show()

                refreshNow()
            },
            onSessionExpired = ::expireSession
        ).show()
    }

    // ---------------------------------------------------------
    // PRIORITY ALERTS
    // ---------------------------------------------------------

    private fun setupPriorityAlerts() {
        PriorityAlerts.ensureChannel(this)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Lets the API push priority requests to this device.
        PushTokenRegistrar(applicationContext).registerCurrentToken(lifecycleScope)
    }

    private fun alertForNewPriorityRequests(queueId: String) {
        val newRequests = priorityTracker.newRequests(queueId, currentTickets)

        newRequests.forEach { ticket ->
            PriorityAlerts.notifyPriorityRequest(
                context = this,
                ticketId = ticket.id,
                displayNumber = ticket.ticketNumber,
                category = ticket.priorityCategory,
                patientName = ticket.displayName
            )
        }
    }

    // ---------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------

    private fun openPatientDetail(ticket: QueueTicket) {
        startActivity(PatientDetailActivity.intent(this, ticket.id, ticket.ticketNumber))
    }

    private fun setupBottomNavigation() {
        configureNavigationItem(navOverview, R.drawable.ic_nav_overview, R.string.dashboard_overview)
        configureNavigationItem(navQueue, R.drawable.ic_nav_queue, R.string.dashboard_queue_tab)
        configureNavigationItem(navWalkin, R.drawable.ic_nav_walkin, R.string.dashboard_walk_in)
        configureNavigationItem(navSettings, R.drawable.ic_nav_settings, R.string.dashboard_settings)

        navOverview.setOnClickListener { showTab(DashboardTab.OVERVIEW) }
        navQueue.setOnClickListener { showTab(DashboardTab.QUEUE) }
        navWalkin.setOnClickListener { openWalkInForm() }
        navSettings.setOnClickListener { showSettingsOptions() }
    }

    private fun configureNavigationItem(item: View, iconRes: Int, labelRes: Int) {
        item.findViewById<ImageView>(R.id.navIcon).setImageResource(iconRes)
        item.findViewById<TextView>(R.id.navLabel).setText(labelRes)
        item.contentDescription = getString(labelRes)
    }

    private fun updateNavigationLabels() {
        val waiting = currentTickets.count { it.status == QueueStatus.WAITING }

        navQueue.findViewById<TextView>(R.id.navLabel).text =
            if (currentQueue == null) {
                getString(R.string.dashboard_queue_tab)
            } else {
                getString(R.string.dashboard_queue_tab_count, waiting)
            }
    }

    private fun showTab(tab: DashboardTab, scrollToTop: Boolean = true) {
        currentTab = tab

        overviewSection.visibility =
            if (tab == DashboardTab.OVERVIEW) View.VISIBLE else View.GONE
        queueSection.visibility =
            if (tab == DashboardTab.QUEUE) View.VISIBLE else View.GONE

        selectNavigationItem(if (tab == DashboardTab.OVERVIEW) navOverview else navQueue)

        if (scrollToTop) dashboardScroll.smoothScrollTo(0, 0)
    }

    private fun selectNavigationItem(selectedItem: View) {
        listOf(navOverview, navQueue, navWalkin, navSettings).forEach { item ->
            val isSelected = item == selectedItem
            item.isSelected = isSelected

            item.background = if (isSelected) {
                ContextCompat.getDrawable(this, R.drawable.bg_nav_selected)
            } else {
                null
            }

            val color = ContextCompat.getColor(
                this,
                if (isSelected) R.color.nav_selected_text else R.color.nav_unselected_text
            )

            item.findViewById<ImageView>(R.id.navIcon)?.setColorFilter(color)
            item.findViewById<TextView>(R.id.navLabel)?.setTextColor(color)
        }
    }

    private fun showSettingsOptions() {
        val options = arrayOf(
            getString(R.string.settings_change_service_point),
            getString(R.string.dashboard_refresh),
            getString(R.string.settings_log_out)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dashboard_settings)
            .setItems(options) { _, choice ->
                when (choice) {
                    0 -> servicePointButton.performClick()
                    1 -> refreshNow(forceServicePoints = true)
                    2 -> confirmLogout()
                }
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_log_out)
            .setMessage(R.string.logout_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.settings_log_out) { _, _ ->
                performLogout()
            }
            .show()
    }

    // Clears the session, cache and alerts, then replaces the whole task
    // with sign-in so Back cannot reopen patient data.
    private fun performLogout() {
        queueRefreshJob?.cancel()
        queueRefreshJob = null

        lifecycleScope.launch {
            StaffSessionManager(applicationContext).signOut()
            SessionNavigation.returnToLogin(this@QueueDashboardActivity)
        }
    }

    private fun expireSession(message: String?) {
        queueRefreshJob?.cancel()
        queueRefreshJob = null

        StaffSessionManager(applicationContext).endSession()
        SessionNavigation.returnToLogin(
            this,
            message ?: getString(R.string.session_expired)
        )
    }
}
