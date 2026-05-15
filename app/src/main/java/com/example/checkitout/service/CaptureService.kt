package com.example.checkitout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.example.checkitout.CheckItOutApp
import com.example.checkitout.R
import com.example.checkitout.action.LikeAction
import com.example.checkitout.data.TrackInfo
import com.example.checkitout.ui.MainActivity
import com.example.checkitout.widget.LikeWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service: keeps the app process alive (so the RecentBuffer
 * survives) AND publishes the persistent lock-screen notification with
 * "Like now" / "Like previous" action buttons. Also pushes updates to
 * the home-screen widget whenever the current track changes.
 */
class CaptureService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var observerJob: Job? = null
    private var mediaSession: MediaSessionCompat? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)

        // MediaSession makes the notification appear as a media controller on lock screen
        // and also receives headset media-button events for triple-click detection.
        mediaSession = MediaSessionCompat(this, "CheckItOut").apply {
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE
                    )
                    .build()
            )
            setCallback(mediaButtonCallback)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }

        startForeground(NOTIF_ID, buildNotification(currentTrack()))
        observerJob = scope.launch {
            (applicationContext as CheckItOutApp).container.recentBuffer.state
                .collectLatest { list ->
                    val track = list.firstOrNull()
                    updateMediaSessionMetadata(track)
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIF_ID, buildNotification(track))
                    LikeWidgetProvider.updateAll(applicationContext, track)
                }
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ---- Headset media-button triple-click detection ----

    private val btnTimestamps = ArrayDeque<Long>()
    private val btnLock = Any()

    private val mediaButtonCallback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            val event = mediaButtonEvent
                ?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                ?: return false
            if (event.action != KeyEvent.ACTION_DOWN) return false
            if (!isHeadsetButton(event.keyCode)) return false

            val now = SystemClock.uptimeMillis()
            synchronized(btnLock) {
                btnTimestamps.addLast(now)
                while (btnTimestamps.isNotEmpty() && now - btnTimestamps.first() > TRIPLE_WINDOW_MS) {
                    btnTimestamps.removeFirst()
                }
                if (btnTimestamps.size >= 3) {
                    btnTimestamps.clear()
                    LikeAction.trigger(applicationContext, historyIndex = 0)
                    return true   // consumed — don't pass to music player
                }
            }
            return false  // single/double press → let music player handle it
        }
    }

    private fun isHeadsetButton(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> true
        else -> false
    }

    private fun currentTrack(): TrackInfo? =
        (applicationContext as? CheckItOutApp)?.container?.recentBuffer?.current()

    private fun buildNotification(track: TrackInfo?): Notification {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val likeNow = pendingLike(historyIndex = 0, requestCode = REQ_LIKE_NOW)
        val likePrev = pendingLike(historyIndex = 1, requestCode = REQ_LIKE_PREV)

        val title = track?.let {
            if (!it.artist.isNullOrBlank()) "${it.artist} - ${it.title}" else it.title
        } ?: getString(R.string.fg_notif_title)
        val text = track?.packageName ?: getString(R.string.fg_notif_text)

        val mediaStyle = MediaNotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1) // Show both actions in compact view

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(getString(R.string.app_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(tap)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(mediaStyle)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.btn_star_big_on,
                    getString(R.string.action_like_now),
                    likeNow
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    getString(R.string.action_like_prev),
                    likePrev
                ).build()
            )
            .build()
    }

    private fun updateMediaSessionMetadata(track: TrackInfo?) {
        val session = mediaSession ?: return
        if (track != null) {
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist ?: "")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album ?: "")
                    .build()
            )
        } else {
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getString(R.string.fg_notif_title))
                    .build()
            )
        }
    }

    private fun pendingLike(historyIndex: Int, requestCode: Int): PendingIntent {
        val intent = Intent(this, LikeReceiver::class.java).apply {
            action = LikeReceiver.ACTION_LIKE
            putExtra(LikeReceiver.EXTRA_HISTORY_INDEX, historyIndex)
            setPackage(packageName)
        }
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val CHANNEL_ID = "capture"
        private const val NOTIF_ID = 1001
        private const val REQ_LIKE_NOW = 11
        private const val REQ_LIKE_PREV = 12
        private const val TRIPLE_WINDOW_MS = 900L

        fun start(context: Context) {
            val intent = Intent(context, CaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_capture_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.channel_capture_desc)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
