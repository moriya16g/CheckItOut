package com.example.checkitout.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs [SyncManager.sync].
 *
 * - On failure (network error, file I/O) → automatically retries with
 *   exponential back-off (30 s → 1 min → 2 min → ...).
 * - Requires network connectivity, so offline enqueues are deferred
 *   until the device comes back online.
 *
 * Two scheduling modes:
 *  [enqueueOneShot] — manual "sync now" button
 *  [enqueuePeriodicIfConfigured] — 1-hour background sync (only if a folder is set)
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val imported = SyncManager.sync(applicationContext)
            Log.i(TAG, "sync ok, imported=$imported")
            Result.success()
        } catch (e: IllegalStateException) {
            // No folder configured — don't retry, it's a permanent failure
            Log.w(TAG, "sync not configured: ${e.message}")
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "sync failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val PERIODIC_WORK_NAME = "checkitout_periodic_sync"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Trigger an immediate one-shot sync. Retries on failure. */
        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /** Start periodic background sync (every 1 hour). */
        fun enqueuePeriodicIfConfigured(context: Context) {
            if (SyncManager.getSavedFolderUri(context) == null) {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancel periodic sync. */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
