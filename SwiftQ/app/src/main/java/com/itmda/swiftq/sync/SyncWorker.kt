package com.itmda.swiftq.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.itmda.swiftq.SwiftQApp
import kotlinx.coroutines.CancellationException

/**
 * Runs one outbox flush in the background, then books the next wake-up from whatever is still waiting.
 *
 * Success means "the flush ran", not "everything was sent": rows that failed are already rescheduled in Room by
 * [BackoffPolicy], and [SyncScheduler.scheduleRetry] turns the earliest of them into the next run. Returning
 * `retry()` is reserved for the flush itself blowing up (database or Keystore trouble), where WorkManager's own
 * exponential backoff is the right tool; after [MAX_CRASH_ATTEMPTS] the periodic safety net takes over.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SwiftQApp).container
        try {
            container.outboxProcessor.flush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (runAttemptCount < MAX_CRASH_ATTEMPTS) Result.retry() else Result.failure()
        }
        // A token registration that failed earlier (no signal) is retried here too.
        runCatching { container.pushRegistrar.sync() }
        runCatching { container.ticketCleaner.runIfDue() }
        // Last on purpose: scheduleRetry REPLACEs the unique "retry" work, which may be the one running this.
        container.scheduler.scheduleRetry(container.outboxProcessor.nextDueAt())
        return Result.success()
    }

    private companion object { const val MAX_CRASH_ATTEMPTS = 5 }
}
