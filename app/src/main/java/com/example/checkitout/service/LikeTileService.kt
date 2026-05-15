package com.example.checkitout.service

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.checkitout.CheckItOutApp
import com.example.checkitout.action.LikeAction
import com.example.checkitout.data.TrackInfo

class LikeTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        refreshTile()
    }

    override fun onClick() {
        val track = currentTrack()
        if (track == null) {
            // No track — keep tile unavailable, do nothing
            refreshTile()
            return
        }
        LikeAction.trigger(applicationContext, historyIndex = 0)

        // Brief visual feedback: show "✓" for 1.5 s then restore
        qsTile?.apply {
            label = "✓ ${track.title}"
            state = Tile.STATE_ACTIVE
            updateTile()
        }
        handler.postDelayed({ refreshTile() }, 1500)
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val track = currentTrack()
        if (track != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(com.example.checkitout.R.string.tile_label)
            tile.subtitle = track.displayName()
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(com.example.checkitout.R.string.tile_label)
            tile.subtitle = getString(com.example.checkitout.R.string.tts_no_track)
        }
        tile.updateTile()
    }

    private fun currentTrack(): TrackInfo? {
        val app = applicationContext as? CheckItOutApp ?: return null
        return app.container.recentBuffer.bestCandidate(System.currentTimeMillis())
    }
}
