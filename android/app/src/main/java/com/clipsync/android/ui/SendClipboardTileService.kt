package com.clipsync.android.ui

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.clipsync.android.service.SyncRuntime

class SendClipboardTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        SyncRuntime.initialize(this)
        qsTile?.apply {
            label = "Send to PC"
            state = if (SyncRuntime.state.value.connected && !SyncRuntime.state.value.paused) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openReader() } else openReader()
    }
    // PendingIntent overload exists only on API 34+; retain the guarded API 24–33 path.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openReader() {
        val intent = ClipboardReadActivity.createIntent(this, "tile")
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2204, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
