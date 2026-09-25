package com.clipsync.android.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.clipsync.android.clipboard.ClipboardReadStore

class SendClipboardTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        ClipboardReadStore.initialize(this)
        qsTile?.apply {
            label = "Send to PC"
            state = if (ClipboardReadStore.serviceRunning.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        ClipboardReadStore.initialize(this)
        val intent = ClipboardReadActivity.createIntent(this, "tile")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    2204,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
