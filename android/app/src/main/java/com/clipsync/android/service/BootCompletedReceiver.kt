package com.clipsync.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.clipsync.android.clipboard.ClipboardReadStore
import com.clipsync.android.logging.FileLogger

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ClipboardReadStore.isServiceEnabled(context)) {
            FileLogger.info("Boot completed; clipboard reader remains disabled")
            return
        }

        val serviceIntent = Intent(context, ClipboardWatchService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        FileLogger.info("Clipboard manual reader service requested after boot")
    }
}
