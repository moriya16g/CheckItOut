package com.example.checkitout.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.checkitout.action.LikeAction

class LikeTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        LikeAction.trigger(applicationContext, historyIndex = 0)
    }
}
