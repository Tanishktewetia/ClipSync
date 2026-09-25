package com.clipsync.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.clipsync.android.clipboard.ClipboardReadStore
import com.clipsync.android.logging.FileLogger

/** Credential-protected identity/preferences become available after the first unlock. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (!ClipboardReadStore.isServiceEnabled(context)) {
            FileLogger.info("Boot/update: sync remains explicitly disabled")
            return
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, ClipboardWatchService::class.java)
                .setAction(ClipboardWatchService.ACTION_RECOVER))
            FileLogger.info("Boot/update: enabled foreground sync requested")
        } catch (e: Exception) {
            // Some OEM/OS policies disallow a background start. Keep preferences, report on next launch.
            FileLogger.warn("Boot/update service start blocked: "+e.javaClass.simpleName)
        }
    }
}
