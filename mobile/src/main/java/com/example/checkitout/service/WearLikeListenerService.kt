package com.example.checkitout.service

import android.util.Log
import com.example.checkitout.action.LikeAction
import com.example.checkitout.data.TriggerSource
import com.example.checkitout.ui.Permissions
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import net.sarotti.checkitout.shared.LikeRequest
import net.sarotti.checkitout.shared.LikeResult
import net.sarotti.checkitout.shared.WearProtocol
import kotlin.math.abs

/**
 * Receives like requests from the Wear OS app. Runs without any UI, so it works
 * while the phone is locked and avoids background-activity-start restrictions.
 */
class WearLikeListenerService : WearableListenerService() {

    // Called on a background thread owned by WearableListenerService.
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearProtocol.PATH_LIKE) return
        val request = LikeRequest.fromBytes(event.data) ?: return
        val now = System.currentTimeMillis()
        // Trust the watch's tap time only if the two clocks roughly agree.
        val pressedAt = request.tappedAt.takeIf { abs(it - now) <= MAX_CLOCK_SKEW_MS } ?: now

        val result = if (!Permissions.isNotificationListenerEnabled(this)) {
            LikeResult(request.requestId, LikeResult.Status.NO_PERMISSION)
        } else {
            val outcome = runCatching {
                runBlocking { LikeAction.like(applicationContext, TriggerSource.WEAR, pressedAt) }
            }.getOrElse {
                Log.e(TAG, "like failed", it)
                LikeAction.Outcome.Failed
            }
            when (outcome) {
                is LikeAction.Outcome.Saved -> LikeResult(
                    requestId = request.requestId,
                    status = LikeResult.Status.OK,
                    title = outcome.track.title,
                    artist = outcome.track.artist,
                )
                LikeAction.Outcome.NoTrack -> LikeResult(request.requestId, LikeResult.Status.NO_TRACK)
                LikeAction.Outcome.Failed -> LikeResult(request.requestId, LikeResult.Status.ERROR)
            }
        }

        runCatching {
            runBlocking {
                withTimeoutOrNull(REPLY_TIMEOUT_MS) {
                    Wearable.getMessageClient(this@WearLikeListenerService)
                        .sendMessage(event.sourceNodeId, WearProtocol.PATH_LIKE_RESULT, result.toBytes())
                        .await()
                }
            }
        }.onFailure { Log.w(TAG, "reply failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "WearLikeListener"
        const val MAX_CLOCK_SKEW_MS = 60_000L
        const val REPLY_TIMEOUT_MS = 5_000L
    }
}
