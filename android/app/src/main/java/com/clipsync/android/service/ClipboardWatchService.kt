package com.clipsync.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipsync.android.clipboard.ClipboardReadStore
import com.clipsync.android.logging.FileLogger
import com.clipsync.android.store.SyncSettings
import com.clipsync.android.transport.ConnectionProblem
import com.clipsync.android.transport.ConnectionDiagnostics
import com.clipsync.android.transport.ConnectionStage
import com.clipsync.android.transport.TlsClipboardClient
import com.clipsync.android.ui.MainActivity
import com.clipsync.core.hash
import kotlinx.coroutines.*

/** Retains the Phase 2 service component and boot preference; no second FGS. */
class ClipboardWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: NotificationManager
    private lateinit var settings: SyncSettings
    private var wifiLock: WifiManager.WifiLock? = null
    private var client: TlsClipboardClient? = null
    private var connectionJob: Job? = null
    private var generation = 0L

    override fun onCreate() {
        super.onCreate()
        settings = SyncSettings(this)
        ClipboardReadStore.initialize(this)
        SyncRuntime.initialize(this)
        notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "ClipSync status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet status for the secure clipboard connection"
                setSound(null, null); enableVibration(false)
            })
        }
        startForeground(NOTIFICATION_ID, notification(SyncRuntime.state.value))
        ClipboardReadStore.setServiceRunning(true)
        SyncRuntime.update { it.copy(running = true) }
        scope.launch { SyncRuntime.state.collect { notifications.notify(NOTIFICATION_ID, notification(it)) } }
        FileLogger.info("Receive-only foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ClipboardReadStore.isServiceEnabled(this)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_PAUSE -> {
                settings.paused = intent.getBooleanExtra("paused", false)
                SyncRuntime.receiver.setPaused(settings.paused)
                SyncRuntime.update { it.copy(paused = settings.paused) }
                FileLogger.info(if (settings.paused) "Receiving paused" else "Receiving resumed")
            }
            ACTION_CONNECT, ACTION_PAIR -> connect(intent.action == ACTION_PAIR)
            else -> if (connectionJob?.isActive != true && settings.address.isNotBlank() && settings.hasPin) connect(false)
        }
        return START_STICKY
    }

    private fun connect(pairing: Boolean) {
        generation++
        val attempt = generation
        val previous = connectionJob
        client?.close(); previous?.cancel(); SyncRuntime.cancelPairing()
        releaseWifiLock()
        SyncRuntime.receiver.disconnect()
        SyncRuntime.update { it.copy(connected = false, connecting = true, error = null, address = settings.address) }
        connectionJob = scope.launch {
            // Close first, then await the old worker: no overlapping identity creation or sockets.
            previous?.join()
            try {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ClipSync:receive").apply {
                    setReferenceCounted(false); acquire()
                }
                val transport = TlsClipboardClient(applicationContext, settings)
                client = transport
                withContext(Dispatchers.IO) {
                    transport.run(settings.address, pairing,
                        confirm = { code ->
                            withContext(Dispatchers.Main) {
                                check(attempt == generation)
                                withTimeout(120000) { SyncRuntime.requestPairing(attempt, code).await() }
                            }
                        },
                        connected = {
                            withContext(Dispatchers.Main) {
                                check(attempt == generation)
                                SyncRuntime.receiver.connected(settings.paused)
                                SyncRuntime.update { it.copy(connected = true, connecting = false, paired = true, error = null) }
                                FileLogger.info("Connected: TLS 1.3, pinned PC, receive-only")
                            }
                        },
                        receive = { text ->
                            withContext(Dispatchers.Main) {
                                check(attempt == generation)
                                val applied = SyncRuntime.receiver.receive(text)
                                SyncRuntime.update { it.copy(received = SyncRuntime.receiver.received) }
                                if (applied) FileLogger.info("Clipboard received: text length=${text.length} hash=${hash(text.toByteArray()).take(6)}")
                                else FileLogger.info("Clipboard not applied: paused or duplicate")
                            }
                        })
                }
            } catch (e: TimeoutCancellationException) {
                if (attempt == generation) fail("Pairing expired. Open Pair new device on the PC and try again.", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == generation) {
                    val stage = client?.stage ?: ConnectionStage.IDLE
                    val message = if (e is ConnectionProblem) e.message ?: "Connection failed."
                        else ConnectionDiagnostics.userMessage(stage, e)
                    fail(message, e)
                }
            } finally {
                if (attempt == generation) {
                    client?.close(); client = null
                    SyncRuntime.cancelPairing(); SyncRuntime.receiver.disconnect()
                    SyncRuntime.update { it.copy(connected = false, connecting = false) }
                    releaseWifiLock()
                }
            }
        }
    }
    private fun fail(message: String, exception: Exception) {
        SyncRuntime.update { it.copy(error = message) }
        // Never log peer-controlled responses or exception text that may include payloads.
        FileLogger.warn("Receive connection ended: " + ConnectionDiagnostics.summary(client?.stage ?: ConnectionStage.IDLE, exception))
    }
    private fun releaseWifiLock() { wifiLock?.let { if (it.isHeld) it.release() }; wifiLock = null }
    override fun onDestroy() {
        generation++; client?.close(); scope.cancel(); SyncRuntime.cancelPairing()
        SyncRuntime.receiver.disconnect(); releaseWifiLock()
        ClipboardReadStore.setServiceRunning(false)
        SyncRuntime.update { it.copy(running = false, connecting = false, connected = false, error = null) }
        FileLogger.info("Receive-only foreground service stopped")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun notification(state: SyncUiState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.clipsync.android.R.drawable.ic_clipsync_status).setColor(0xFF818CF8.toInt())
            .setContentTitle("ClipSync · ${state.status}")
            .setContentText(when { state.pairing != null -> "Open ClipSync to compare pairing codes"; state.paused -> "Receiving paused"; state.connected -> "Receiving PC clipboard text automatically"; else -> "Open ClipSync for connection settings" })
            .setContentIntent(open).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }
    companion object {
        const val ACTION_CONNECT = "com.clipsync.android.CONNECT"
        const val ACTION_PAIR = "com.clipsync.android.PAIR"
        const val ACTION_PAUSE = "com.clipsync.android.PAUSE"
        private const val CHANNEL = "clipsync_status_phase2"
        private const val NOTIFICATION_ID = 2201
    }
}
