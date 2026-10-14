package com.itmda.swiftq

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.itmda.swiftq.data.local.TicketState
import com.itmda.swiftq.push.Cue
import com.itmda.swiftq.ui.screens.TurnAlarmScreen
import com.itmda.swiftq.ui.status.formatTicketNumber
import com.itmda.swiftq.ui.status.spokenTicketNumber
import com.itmda.swiftq.ui.history.HistoryScreen
import com.itmda.swiftq.ui.history.HistoryViewModel
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.itmda.swiftq.ui.components.OfflineBanner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.itmda.swiftq.ui.screens.HomeActiveTicketScreen
import com.itmda.swiftq.ui.queue.LeaveConfirmView
import com.itmda.swiftq.ui.queue.LeftQueueView
import com.itmda.swiftq.ui.queue.LiveQueueView
import com.itmda.swiftq.ui.queue.LiveQueueViewModel
import com.itmda.swiftq.ui.status.StatusViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.itmda.swiftq.ui.join.JoinHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.itmda.swiftq.data.AppLanguage
import com.itmda.swiftq.data.ThemeMode
import com.itmda.swiftq.ui.screens.HomeScreen
import com.itmda.swiftq.ui.screens.LanguageSelectionScreen
import com.itmda.swiftq.ui.screens.SettingsAppearanceScreen
import com.itmda.swiftq.ui.screens.SettingsLanguageScreen
import com.itmda.swiftq.ui.screens.SettingsNotificationsScreen
import com.itmda.swiftq.ui.screens.SettingsScreen
import com.itmda.swiftq.ui.screens.SettingsTextSizeScreen
import com.itmda.swiftq.ui.screens.SplashScreen
import com.itmda.swiftq.ui.theme.SwiftQTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private val settings by lazy { (application as SwiftQApp).container.settings }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = when (settings.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            SwiftQTheme(darkTheme = darkTheme, textScale = settings.textSize.scale) {
                var screen by rememberSaveable { mutableStateOf(AppScreen.Splash) }
                // Set when the patient already holds a ticket, so the join flow opens on its status screen.
                var joinStartRef by rememberSaveable { mutableStateOf<String?>(null) }
                // The number shown on the leave screens, kept after the ticket stops being active.
                var leftNumber by rememberSaveable { mutableStateOf<String?>(null) }
                val container = (application as SwiftQApp).container
                // One status view model for Home and the leave screens, so a leave in progress survives the screen change.
                val homeVm: StatusViewModel = viewModel(key = "home-ticket", factory = viewModelFactory {
                    initializer { StatusViewModel(null, container.ticketRepository, container.clinicRepository, container.outboxProcessor) }
                })
                LaunchedEffect(homeVm) { homeVm.keepFresh() }

                // Haptic + sound when the ticket is confirmed or called while the app is open.
                val activeTicket = homeVm.ui.collectAsStateWithLifecycle().value.ticket
                val ticketStatus = activeTicket?.status

                // Figma 15: the full-screen "It's your turn" while the ticket is called, until "I'm coming" is tapped.
                var turnAcknowledged by rememberSaveable { mutableStateOf<String?>(null) }
                val showTurn = activeTicket != null && activeTicket.status == TicketState.CALLED &&
                    activeTicket.localRef != turnAcknowledged && screen != AppScreen.Splash && screen != AppScreen.Language
                var lastStatus by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(ticketStatus) {
                    Cue.forTransition(lastStatus, ticketStatus)?.let { container.feedback.play(it) }
                    lastStatus = ticketStatus
                }

                // Alerts need the notification permission on Android 13+; ask once the patient actually holds a ticket.
                val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(ticketStatus != null, settings.alertsEnabled) {
                    if (ticketStatus != null && settings.alertsEnabled && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                // Turning alerts off removes this phone's token from the API; turning them on registers it again.
                LaunchedEffect(settings.alertsEnabled) { container.pushRegistrar.sync() }

                // System bar icons have to contrast with whichever screen is behind them.
                LaunchedEffect(screen, darkTheme, showTurn) {
                    // The turn screen and the splash are blue, so their bars need light icons in any theme.
                    val style = if (screen == AppScreen.Splash || darkTheme || showTurn) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }

                // Back on the turn screen dismisses it, same as "I'm coming".
                BackHandler(enabled = showTurn) { turnAcknowledged = activeTicket?.localRef }
                BackHandler(enabled = screen.parent != null && !showTurn) { screen.parent?.let { screen = it } }

                val online by remember { container.connectivity.isOnline }.collectAsStateWithLifecycle(initialValue = container.connectivity.current())
                val showBanner = !online && screen != AppScreen.Splash && screen != AppScreen.Language
                Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().background(SwiftQTheme.colors.background)) {
                if (showBanner) OfflineBanner()
                // The banner already pads for the status bar, so the screen below must not pad for it a second time.
                Box(Modifier.weight(1f).fillMaxWidth().then(if (showBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                when (screen) {
                    AppScreen.Splash -> SplashScreen(
                        onFinished = {
                            // No saved language means this is the first launch.
                            val firstLaunch = AppLanguage.savedCode(this@MainActivity) == null
                            screen = if (firstLaunch) AppScreen.Language else AppScreen.Home
                        }
                    )

                    AppScreen.Language -> {
                        var selectedCode by rememberSaveable { mutableStateOf(AppLanguage.defaultCode()) }
                        LanguageSelectionScreen(
                            selectedLanguageCode = selectedCode,
                            onLanguageSelected = { selectedCode = it.code },
                            onContinue = {
                                screen = AppScreen.Home
                                AppLanguage.select(this@MainActivity, selectedCode)
                            }
                        )
                    }

                    // Profile is not built yet, so its tap goes nowhere for now.
                    AppScreen.Home -> {
                        // Figma 03 when the patient holds a ticket, Figma 02 when not. Read from SQLite, so it also works offline.
                        val homeUi by homeVm.ui.collectAsStateWithLifecycle()
                        val activeTicket = homeUi.ticket
                        if (activeTicket != null) {
                            HomeActiveTicketScreen(
                                ui = homeUi,
                                ticket = activeTicket,
                                onSeeLiveQueue = { screen = AppScreen.LiveQueue },
                                onLeave = { screen = AppScreen.LeaveConfirm },
                                onProfile = {},
                                onSettings = { screen = AppScreen.Settings },
                            )
                        } else {
                            HomeScreen(
                                onJoinQueue = { joinStartRef = null; screen = AppScreen.Join },
                                onProfile = {},
                                onSettings = { screen = AppScreen.Settings },
                            )
                        }
                    }

                    // Figma 12 · Live queue.
                    AppScreen.LiveQueue -> {
                        val vm: LiveQueueViewModel = viewModel(key = "live-queue", factory = viewModelFactory {
                            initializer { LiveQueueViewModel(container.ticketRepository, container.clinicRepository, container.queueRepository) }
                        })
                        LaunchedEffect(vm) { vm.poll() }
                        val liveUi by vm.ui.collectAsStateWithLifecycle()
                        val failed by vm.failed.collectAsStateWithLifecycle()
                        val scope = rememberCoroutineScope()
                        LiveQueueView(ui = liveUi, failed = failed, onRetry = { scope.launch { vm.refresh() } }, onBack = { screen = AppScreen.Home })
                    }

                    // Figma 13 · Leave queue (confirm), then Figma 14 · Left queue.
                    AppScreen.LeaveConfirm -> {
                        val homeUi by homeVm.ui.collectAsStateWithLifecycle()
                        val leaveUi by homeVm.leave.collectAsStateWithLifecycle()
                        val ticket = homeUi.ticket
                        LaunchedEffect(ticket?.displayNumber) { ticket?.displayNumber?.let { leftNumber = it } }
                        LaunchedEffect(leaveUi.done) {
                            if (leaveUi.done) { homeVm.leaveHandled(); screen = AppScreen.LeftQueue }
                        }
                        LaunchedEffect(homeUi.loaded, ticket == null, leaveUi.done) {
                            // Nothing left to leave (it was called, served or closed meanwhile): back to Home.
                            if (homeUi.loaded && ticket == null && !leaveUi.done) screen = AppScreen.Home
                        }
                        LeaveConfirmView(
                            ticketNumber = ticket?.displayNumber ?: leftNumber,
                            leave = leaveUi,
                            onStay = { screen = AppScreen.Home },
                            onLeave = homeVm::leaveQueue,
                        )
                    }

                    AppScreen.LeftQueue -> LeftQueueView(ticketNumber = leftNumber, onHome = { screen = AppScreen.Home })

                    AppScreen.Join -> JoinHost(
                        container = (application as SwiftQApp).container,
                        startLocalRef = joinStartRef,
                        languageCode = { AppLanguage.currentCode(this@MainActivity) },
                        onExit = { screen = AppScreen.Home },
                    )

                    // Privacy (Figma frame 21) is not built yet, so its row goes nowhere for now.
                    AppScreen.Settings -> SettingsScreen(
                        languageCode = AppLanguage.currentCode(this@MainActivity),
                        notificationsEnabled = settings.alertsEnabled,
                        textSize = settings.textSize,
                        themeMode = settings.themeMode,
                        onLanguage = { screen = AppScreen.SettingsLanguage },
                        onNotifications = { screen = AppScreen.SettingsNotifications },
                        onTextSize = { screen = AppScreen.SettingsTextSize },
                        onAppearance = { screen = AppScreen.SettingsAppearance },
                        onPrivacy = {},
                        onHistory = { screen = AppScreen.History },
                        onHome = { screen = AppScreen.Home },
                        onProfile = {},
                    )

                    AppScreen.History -> {
                        val vm: HistoryViewModel = viewModel(key = "history", factory = viewModelFactory {
                            initializer { HistoryViewModel(container.ticketRepository, container.clinicRepository) }
                        })
                        val historyUi by vm.ui.collectAsStateWithLifecycle()
                        HistoryScreen(ui = historyUi, onRemove = vm::remove, onClearAll = vm::clearAll, onBack = { screen = AppScreen.Settings })
                    }

                    AppScreen.SettingsLanguage -> SettingsLanguageScreen(
                        selectedLanguageCode = AppLanguage.currentCode(this@MainActivity),
                        onLanguageSelected = { AppLanguage.select(this@MainActivity, it.code) },
                        onBack = { screen = AppScreen.Settings },
                    )

                    AppScreen.SettingsNotifications -> SettingsNotificationsScreen(
                        alertsEnabled = settings.alertsEnabled,
                        onAlertsEnabledChange = settings::updateAlertsEnabled,
                        soundEnabled = settings.soundEnabled,
                        onSoundEnabledChange = settings::updateSoundEnabled,
                        vibrateEnabled = settings.vibrateEnabled,
                        onVibrateEnabledChange = settings::updateVibrateEnabled,
                        warnMinutes = settings.warnMinutes,
                        onWarnMinutesChange = settings::updateWarnMinutes,
                        onBack = { screen = AppScreen.Settings },
                    )

                    AppScreen.SettingsTextSize -> SettingsTextSizeScreen(
                        selectedSize = settings.textSize,
                        onSizeSelected = settings::updateTextSize,
                        onBack = { screen = AppScreen.Settings },
                    )

                    AppScreen.SettingsAppearance -> SettingsAppearanceScreen(
                        selectedMode = settings.themeMode,
                        onModeSelected = settings::updateThemeMode,
                        onBack = { screen = AppScreen.Settings },
                    )
                }
                }
                }
                if (showTurn && activeTicket != null) {
                    TurnAlarmScreen(
                        ticketNumber = formatTicketNumber(activeTicket.displayNumber).orEmpty(),
                        spokenNumber = spokenTicketNumber(activeTicket.displayNumber)?.let { getString(R.string.a11y_ticket_number, it) },
                        roomLabel = activeTicket.counterLabel,
                        onComing = { turnAcknowledged = activeTicket.localRef },
                    )
                }
                }
            }
        }
    }
}

/** [parent] is where the system Back button leads; screens without one leave the app. */
private enum class AppScreen(val parent: AppScreen? = null) {
    Splash,
    Language,
    Home,
    Join(parent = Home),
    LiveQueue(parent = Home),
    LeaveConfirm(parent = Home),
    LeftQueue(parent = Home),
    Settings(parent = Home),
    SettingsLanguage(parent = Settings),
    SettingsNotifications(parent = Settings),
    SettingsTextSize(parent = Settings),
    SettingsAppearance(parent = Settings),
    History(parent = Settings),
}
