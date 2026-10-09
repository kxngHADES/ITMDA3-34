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

                    // Join and Profile are not built yet, so their taps go nowhere for now.
                    AppScreen.Home -> HomeScreen(
                        onJoinQueue = {},
                        onProfile = {},
                        onSettings = { screen = AppScreen.Settings },
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
    Settings(parent = Home),
    SettingsLanguage(parent = Settings),
    SettingsNotifications(parent = Settings),
    SettingsTextSize(parent = Settings),
    SettingsAppearance(parent = Settings),
}
