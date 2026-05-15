package com.example.checkitout.service

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
        if (c.packageName == packageName) return // Never ingest CheckItOut's own MediaSession
        metadata ?: return
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        if (!isPlaying) return // only record while actually playing
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val info = TrackInfo(
            title = title.trim(),
            artist = artist?.trim()?.takeIf { it.isNotEmpty() },
            album = album?.trim()?.takeIf { it.isNotEmpty() },
            packageName = c.packageName ?: "unknown",
            observedAt = System.currentTimeMillis(),
        )
        val app = applicationContext as CheckItOutApp
        app.container.recentBuffer.push(info)
    }

    companion object {
        private const val TAG = "MediaNL"
    }
}
