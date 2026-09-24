package com.example.checkitout.links

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.checkitout.data.AppDatabase
import java.util.concurrent.TimeUnit

/** Resolves Spotify / Apple Music URLs for one liked row after it has been saved. */
class LinkResolveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L).takeIf { it >= 0 } ?: return Result.failure()
        val dao = AppDatabase.get(applicationContext).likedTrackDao()
        val row = dao.getById(id) ?: return Result.success()
        if (row.spotifyUrl != null && row.appleMusicUrl != null) return Result.success()

        val links = LinkResolver.resolve(row)
        if (links.spotifyUrl != row.spotifyUrl || links.appleMusicUrl != row.appleMusicUrl) {
            dao.fillLinks(
                id = id,
                spotifyUrl = links.spotifyUrl,
                appleMusicUrl = links.appleMusicUrl,
                spotifyId = links.spotifyId,
                updatedAt = System.currentTimeMillis(),
            )
        }
        val incomplete = links.spotifyUrl == null || links.appleMusicUrl == null
        // Odesli / iTunes are rate-limited, so a miss may be transient.
        return if (incomplete && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_ID = "liked_id"
        private const val MAX_RETRIES = 2

        fun enqueue(context: Context, likedId: Long) {
            val request = OneTimeWorkRequestBuilder<LinkResolveWorker>()
                .setInputData(workDataOf(KEY_ID to likedId))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("links-$likedId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
