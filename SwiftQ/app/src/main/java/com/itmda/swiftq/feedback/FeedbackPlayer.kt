package com.itmda.swiftq.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.itmda.swiftq.data.AppSettings
import com.itmda.swiftq.push.Cue

/**
 * Haptic and sound feedback for the moments that matter, following the patient's Settings > Notifications choices.
 *  - vibration only when "Vibrate" is on; sound only when "Sound" is on and the phone's ringer is not silent or on vibrate;
 *  - the pattern differs by cue so it can be told apart without looking (a screen reader user, a phone in a pocket).
 * A cue is played once even when the push and the in-app refresh both report it.
 */
class FeedbackPlayer(
    private val context: Context,
    private val settings: AppSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val lastPlayed = HashMap<Cue, Long>()

    fun play(cue: Cue) {
        val now = clock()
        if (now - (lastPlayed[cue] ?: Long.MIN_VALUE) < DEDUPE_MS) return
        lastPlayed[cue] = now
        if (settings.vibrateEnabled) vibrate(cue)
        if (settings.soundEnabled && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) chime()
    }

    private fun vibrate(cue: Cue) {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern(cue), -1))
    }

    private fun chime() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }?.play()
    }

    companion object {
        const val DEDUPE_MS = 5_000L

        /** Off/on lengths in ms, starting with a pause. One short tap, two taps, or a long triple for "go in now". */
        fun pattern(cue: Cue): LongArray = when (cue) {
            Cue.Confirmed -> longArrayOf(0, 60)
            Cue.AlmostTurn -> longArrayOf(0, 120, 100, 120)
            Cue.YourTurn -> longArrayOf(0, 400, 150, 400, 150, 400)
        }
    }
}
