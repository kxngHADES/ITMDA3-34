package com.itmda.swiftq.sync

import android.content.Context
import androidx.work.BackoffPolicy as WorkBackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Background sync, on top of [OutboxProcessor].
 *
 * Two layers of backoff, on purpose:
 *  - **Per row (Chapter 3, Table 12)**: [BackoffPolicy] writes `next_attempt_at` on each outbox row
 *    (immediate, 5 s, 15 s, 45 s, 135 s, then hourly; 24 h cap). It survives process death because it is in Room.
 *  - **Per worker**: WorkManager only decides *when to wake up*. After each flush the worker is re-enqueued
 *    for the earliest `next_attempt_at` ([scheduleFromOutbox]). WorkManager's own exponential backoff
 *    (which cannot express Table 12: 10 s minimum, doubles) is used only when the flush itself crashes.
 *
 * Three jobs, each its own unique work name so none can cancel another:
 *  - [NOW]: "send what is due". Triggered by a join, the app coming to the foreground, or a periodic tick.
 *    APPEND_OR_REPLACE queues behind a running flush instead of cancelling it mid-request.
 *  - [RETRY]: "wake me at the next due time". REPLACE: only the earliest due row matters.
 *  - [PERIODIC]: a 15 min safety net for anything the other two missed (process killed before it could schedule).
 * All of them wait for a connected network, so a patient who joined offline is confirmed when signal returns.
 */
interface SyncScheduler {
    /** Send what is due as soon as the network allows. */
    fun requestSync()

    /** Wake the worker at [dueAtMs] (epoch ms), or never if null. */
    fun scheduleRetry(dueAtMs: Long?)

    /** Install the periodic safety net. Safe to call on every app start. */
    fun ensurePeriodic()
}

class WorkManagerSyncScheduler(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : SyncScheduler {
    private val work = WorkManager.getInstance(context.applicationContext)

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun requestSync() {
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request(delayMs = 0))
    }

    override fun scheduleRetry(dueAtMs: Long?) {
        if (dueAtMs == null) { work.cancelUniqueWork(RETRY); return }
        work.enqueueUniqueWork(RETRY, ExistingWorkPolicy.REPLACE, request(SyncSchedule.delayMs(dueAtMs, clock())))
    }

    override fun ensurePeriodic() {
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL.inWholeMinutes, TimeUnit.MINUTES)
            .setConstraints(connected)
            .setBackoffCriteria(WorkBackoffPolicy.EXPONENTIAL, CRASH_BACKOFF.toJavaDuration())
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    private fun request(delayMs: Long) = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(connected)
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        // Only used when doWork() throws; Table 12 retries are scheduled by scheduleRetry, not by this.
        .setBackoffCriteria(WorkBackoffPolicy.EXPONENTIAL, CRASH_BACKOFF.toJavaDuration())
        .build()

    companion object {
        const val NOW = "swiftq-sync-now"
        const val RETRY = "swiftq-sync-retry"
        const val PERIODIC = "swiftq-sync-periodic"
        val PERIODIC_INTERVAL = 15.minutes // WorkManager's minimum
        val CRASH_BACKOFF = 30.seconds
    }
}

/** The part of scheduling that is arithmetic, kept free of Android so it runs in plain JVM tests. */
object SyncSchedule {
    /**
     * Never wakes up in a tight loop: a row can be "due" (past `next_attempt_at`) yet still unsent, e.g. one that was
     * left alone to keep write order behind a failed row. Treat anything due within [MIN_GAP_MS] as [MIN_GAP_MS] away.
     */
    const val MIN_GAP_MS = 5_000L

    fun delayMs(dueAtMs: Long, nowMs: Long): Long = (dueAtMs - nowMs).coerceAtLeast(MIN_GAP_MS)
}
