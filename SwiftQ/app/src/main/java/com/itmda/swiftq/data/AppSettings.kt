package com.itmda.swiftq.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** [scale] multiplies the phone's own font scale. */
enum class TextSize(val scale: Float) { Normal(1f), Large(1.15f), Largest(1.3f) }

enum class ThemeMode { Light, Dark, System }

/**
 * The patient's choices from the Settings screens, kept on the phone. Values are Compose state,
 * so the UI updates as soon as one changes. The language lives in [AppLanguage].
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("swiftq_prefs", Context.MODE_PRIVATE)

    var textSize by mutableStateOf(readEnum(KEY_TEXT_SIZE, TextSize.Normal))
        private set
    var themeMode by mutableStateOf(readEnum(KEY_THEME_MODE, ThemeMode.Light))
        private set

    // Alerts: off removes the push token from the API. Sound and vibrate shape the in-app and push feedback.
    var alertsEnabled by mutableStateOf(prefs.getBoolean(KEY_ALERTS, true))
        private set
    var soundEnabled by mutableStateOf(prefs.getBoolean(KEY_SOUND, true))
        private set
    var vibrateEnabled by mutableStateOf(prefs.getBoolean(KEY_VIBRATE, true))
        private set
    var warnMinutes by mutableIntStateOf(prefs.getInt(KEY_WARN_MINUTES, 5))
        private set

    fun updateTextSize(value: TextSize) {
        textSize = value
        prefs.edit { putString(KEY_TEXT_SIZE, value.name) }
    }

    fun updateThemeMode(value: ThemeMode) {
        themeMode = value
        prefs.edit { putString(KEY_THEME_MODE, value.name) }
    }

    fun updateAlertsEnabled(value: Boolean) {
        alertsEnabled = value
        prefs.edit { putBoolean(KEY_ALERTS, value) }
    }

    fun updateSoundEnabled(value: Boolean) {
        soundEnabled = value
        prefs.edit { putBoolean(KEY_SOUND, value) }
    }

    fun updateVibrateEnabled(value: Boolean) {
        vibrateEnabled = value
        prefs.edit { putBoolean(KEY_VIBRATE, value) }
    }

    fun updateWarnMinutes(value: Int) {
        warnMinutes = value
        prefs.edit { putInt(KEY_WARN_MINUTES, value) }
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T =
        prefs.getString(key, null)?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } } ?: default

    private companion object {
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ALERTS = "alerts_enabled"
        const val KEY_SOUND = "alert_sound"
        const val KEY_VIBRATE = "alert_vibrate"
        const val KEY_WARN_MINUTES = "warn_minutes"
    }
}
