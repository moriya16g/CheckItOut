package com.example.checkitout.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.checkitout.CheckItOutApp
import com.example.checkitout.data.TrackInfo

/**
 * Captures currently-playing track info from *any* media app via the system
 * MediaSessionManager. Notification access permission is what unlocks this API
 * for non-system apps.
 *
 * We do not actually read notification *content*; we only use this service as
 * the privileged channel that grants [MediaSessionManager.getActiveSessions]
 * permission. The track data comes from the structured [MediaController].
 */
class MediaNotificationListener : NotificationListenerService() {

    private var sessionManager: MediaSessionManager? = null
    private val controllers = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        refreshControllers(list ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val component = ComponentName(this, MediaNotificationListener::class.java)
        sessionManager = (getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager).also {
            try {
                it.addOnActiveSessionsChangedListener(sessionListener, component)
                refreshControllers(it.getActiveSessions(component))
            } catch (t: Throwable) {
                Log.e(TAG, "failed to subscribe sessions", t)
            }
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        controllers.forEach { (c, cb) -> c.unregisterCallback(cb) }
        controllers.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { /* unused */ }

    private fun refreshControllers(active: List<MediaController>) {
        // unregister stale controllers
        val newSet = active.toSet()
        val gone = controllers.keys - newSet
        for (c in gone) {
            controllers.remove(c)?.let { c.unregisterCallback(it) }
        }
        // register new ones and immediately read their current metadata
        for (c in active) {
            if (controllers.containsKey(c)) continue
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    publish(c, metadata, c.playbackState)
                }
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    publish(c, c.metadata, state)
                }
                override fun onSessionDestroyed() {
                    controllers.remove(c)
                }
            }
            try {
                c.registerCallback(cb)
                controllers[c] = cb
                publish(c, c.metadata, c.playbackState)
            } catch (t: Throwable) {
                Log.e(TAG, "register failed for ${c.packageName}", t)
            }
        }
    }

    private fun publish(c: MediaController, metadata: MediaMetadata?, state: PlaybackState?) {
        val info = toTrackInfo(c, metadata, state, ownPackage = packageName) ?: return
        val app = applicationContext as CheckItOutApp
        app.container.recentBuffer.push(info)
    }

    companion object {
        private const val TAG = "MediaNL"
        private const val METADATA_KEY_MEDIA_URI = "android.media.metadata.MEDIA_URI"

        /** Reads the currently playing track directly, for when the buffer is still empty. */
        fun readNow(context: Context): TrackInfo? = try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(ComponentName(context, MediaNotificationListener::class.java))
                .firstNotNullOfOrNull { c ->
                    toTrackInfo(c, c.metadata, c.playbackState, ownPackage = context.packageName)
                }
        } catch (e: SecurityException) {
            null
        }

        private fun toTrackInfo(
            c: MediaController,
            metadata: MediaMetadata?,
            state: PlaybackState?,
            ownPackage: String,
        ): TrackInfo? {
            if (c.packageName == ownPackage) return null // Never ingest CheckItOut's own MediaSession
            metadata ?: return null
            // only record while actually playing
            val s = state?.takeIf { it.state == PlaybackState.STATE_PLAYING } ?: return null
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: return null
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
            val positionMs = run {
                val base = s.position
                val elapsed = SystemClock.elapsedRealtime() - s.lastPositionUpdateTime
                val rate = s.playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
                (base + (elapsed * rate).toLong()).coerceAtLeast(0L)
            }
            return TrackInfo(
                title = title.trim(),
                artist = artist?.trim()?.takeIf { it.isNotEmpty() },
                album = album?.trim()?.takeIf { it.isNotEmpty() },
                packageName = c.packageName ?: "unknown",
                observedAt = System.currentTimeMillis(),
                positionMs = positionMs,
                durationMs = durationMs,
                mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() },
                mediaUri = metadata.getString(METADATA_KEY_MEDIA_URI)?.takeIf { it.isNotBlank() },
            )
        }
    }
}
