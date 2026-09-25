package com.clipsync.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipsync.android.clipboard.ClipboardReadStore

class ClipboardWatchService : Service() {
    private lateinit var notifications: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())
        ClipboardReadStore.initialize(this)
        ClipboardReadStore.setServiceRunning(true)
        com.clipsync.android.logging.FileLogger.info("Clipboard manual reader service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        ClipboardReadStore.setServiceRunning(false)
        super.onDestroy()
        com.clipsync.android.logging.FileLogger.info("Clipboard manual reader service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun serviceNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .setColor(0xFF818CF8.toInt())
        .setContentTitle("ClipSync is running")
        .setContentText("Use the Send to PC Quick Settings tile")
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ClipSync status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows that ClipSync is ready for the Send to PC tile"
                },
            )
        }
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 2201
        private const val CHANNEL_ID = "clipsync_status_phase2"
    }
}
