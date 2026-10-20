package com.swiftq.staff.ui

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.swiftq.staff.R
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object TimeFormat {

    // Parses API timestamps such as 2026-10-20T09:12:44.512Z.
    fun parse(isoTimestamp: String?): Instant? {
        if (isoTimestamp.isNullOrBlank()) return null

        return runCatching { OffsetDateTime.parse(isoTimestamp).toInstant() }
            .recoverCatching { Instant.parse(isoTimestamp) }
            .getOrNull()
    }

    // "09:12" (or "9:12 AM" on 12-hour devices).
    fun clockTime(context: Context, isoTimestamp: String?): String? {
        val instant = parse(isoTimestamp) ?: return null
        return clockTime(context, instant.toEpochMilli())
    }

    fun clockTime(context: Context, epochMillis: Long): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"

        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMillis))
    }

    // "Last updated 14:32 · 2 min. ago", or with the date when not today.
    fun lastUpdated(context: Context, epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val absolute = if (DateUtils.isToday(epochMillis)) {
            clockTime(context, epochMillis)
        } else {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(epochMillis))
        }

        val relative = if (now - epochMillis < DateUtils.MINUTE_IN_MILLIS) {
            context.getString(R.string.time_just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(
                epochMillis,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        }

        return context.getString(R.string.dashboard_last_updated, absolute, relative)
    }

    // "Waiting 14 min" from the time the patient joined.
    fun waitedMinutes(isoTimestamp: String?, now: Long = System.currentTimeMillis()): Long? {
        val joined = parse(isoTimestamp) ?: return null
        val minutes = (now - joined.toEpochMilli()) / DateUtils.MINUTE_IN_MILLIS
        return minutes.coerceAtLeast(0)
    }
}
