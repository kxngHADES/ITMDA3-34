package com.example.swiftq.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/** Remembers the language the patient picked on first launch and applies it to the app. */
object AppLanguage {
    private const val PREFS_NAME = "swiftq_prefs"
    private const val KEY_LANGUAGE_CODE = "language_code"

    val supportedCodes = listOf("en", "af")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Null until a language has been chosen, i.e. on first launch. */
    fun savedCode(context: Context): String? = prefs(context).getString(KEY_LANGUAGE_CODE, null)

    /** The option to pre-select: the device language when we support it, otherwise English. */
    fun defaultCode(): String =
        Locale.getDefault().language.takeIf { it in supportedCodes } ?: supportedCodes.first()

    /** The language the app is showing now: the saved choice, or the default before one is made. */
    fun currentCode(context: Context): String = savedCode(context) ?: defaultCode()

    fun select(activity: Activity, code: String) {
        prefs(activity).edit { putString(KEY_LANGUAGE_CODE, code) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system persists this and recreates the activity when the locale changes.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(code)
        } else {
            activity.recreate()
        }
    }

    /** Before Android 13 there is no per-app language, so the activity wraps its own context. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val code = savedCode(base) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(code))
        return base.createConfigurationContext(config)
    }
}
