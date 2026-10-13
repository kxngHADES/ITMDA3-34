package com.example.swiftq

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swiftq.ui.screens.HomeActiveTicketScreen
import com.example.swiftq.ui.queue.LeaveConfirmView
import com.example.swiftq.ui.queue.LeftQueueView
import com.example.swiftq.ui.queue.LiveQueueView
import com.example.swiftq.ui.queue.LiveQueueViewModel
import com.example.swiftq.ui.status.StatusViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.swiftq.ui.join.JoinHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.swiftq.data.AppLanguage
import com.example.swiftq.data.AppSettings
import com.example.swiftq.data.ThemeMode
import com.example.swiftq.ui.screens.HomeScreen
import com.example.swiftq.ui.screens.LanguageSelectionScreen
import com.example.swiftq.ui.screens.SettingsAppearanceScreen
import com.example.swiftq.ui.screens.SettingsLanguageScreen
import com.example.swiftq.ui.screens.SettingsNotificationsScreen
import com.example.swiftq.ui.screens.SettingsScreen
import com.example.swiftq.ui.screens.SettingsTextSizeScreen
import com.example.swiftq.ui.screens.SplashScreen
import com.example.swiftq.ui.theme.SwiftQTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private val settings by lazy { AppSettings(applicationContext) }

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

                // System bar icons have to contrast with whichever screen is behind them.
                LaunchedEffect(screen, darkTheme) {
                    val style = if (screen == AppScreen.Splash || darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }

                BackHandler(enabled = screen.parent != null) { screen.parent?.let { screen = it } }

                when (screen) {
                    AppScreen.Splash -> SplashScreen(
                        onFinished = {
                            // No saved language means this is the first launch.
                            val firstLaunch = AppLanguage.savedCode(this) == null
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
                                AppLanguage.select(this, selectedCode)
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
                        languageCode = AppLanguage.currentCode(this),
                        notificationsEnabled = settings.alertsEnabled,
                        textSize = settings.textSize,
                        themeMode = settings.themeMode,
                        onLanguage = { screen = AppScreen.SettingsLanguage },
                        onNotifications = { screen = AppScreen.SettingsNotifications },
                        onTextSize = { screen = AppScreen.SettingsTextSize },
                        onAppearance = { screen = AppScreen.SettingsAppearance },
                        onPrivacy = {},
                        onHome = { screen = AppScreen.Home },
                        onProfile = {},
                    )

                    AppScreen.SettingsLanguage -> SettingsLanguageScreen(
                        selectedLanguageCode = AppLanguage.currentCode(this),
                        onLanguageSelected = { AppLanguage.select(this, it.code) },
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
}
