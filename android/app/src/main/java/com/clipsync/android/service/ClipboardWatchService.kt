package com.clipsync.android.service

import android.app.*
import android.content.*
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.clipsync.android.clipboard.ClipboardReadStore
import com.clipsync.android.logging.FileLogger
import com.clipsync.android.store.SyncSettings
import com.clipsync.android.transport.*
import com.clipsync.android.ui.ClipboardReadActivity
import com.clipsync.android.ui.MainActivity
import com.clipsync.core.ManualSendResult
import com.clipsync.core.hash
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The service, never an Activity, owns sync lifetime and its I/O workers. */
class ClipboardWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: NotificationManager
    private lateinit var settings: SyncSettings
    private lateinit var networks: ConnectivityManager
    private lateinit var keyguard: KeyguardManager
    private var wifiLock: WifiManager.WifiLock? = null
    private var client: TlsClipboardClient? = null
    private var connectionJob: Job? = null
    private var retryJob: Job? = null
    private var generation = 0L
    private var destroyed = false
    private var recoveryBlocked = false
    private var networkRegistered = false
    private var unlockRegistered = false
    private val sendSignal = Channel<Unit>(Channel.CONFLATED)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { scope.launch { recover("Wi-Fi available") } }
        override fun onLost(network: Network) { scope.launch {
            if (client?.network == network) {
                FileLogger.info("Active Wi-Fi route lost")
                client?.close()
            }
        } }
    }
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (keyguard.isKeyguardLocked) return
            if (intent?.action == Intent.ACTION_USER_PRESENT || intent?.action == Intent.ACTION_USER_UNLOCKED || intent?.action == Intent.ACTION_SCREEN_ON) {
                applyDeferred()
                // A socket can look connected after sleep even when the link has died.
                // Replace it on unlock instead of waiting for an unimplemented Phase 7 heartbeat.
                recover("Phone unlocked", replaceConnected = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = SyncSettings(this)
        ClipboardReadStore.initialize(this)
        SyncRuntime.initialize(this)
        notifications = getSystemService(NotificationManager::class.java)
        networks = getSystemService(ConnectivityManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "ClipSync status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet connection status and manual clipboard sending"
                setSound(null, null); enableVibration(false)
            })
        }
        startForeground(NOTIFICATION_ID, notification(SyncRuntime.state.value))
        ClipboardReadStore.setServiceRunning(true)
        SyncRuntime.update { it.copy(running = true) }
        SyncRuntime.onManualSend = ::offerManualSend
        scope.launch {
            SyncRuntime.state.map { it.copy(received = 0, sent = 0) }.distinctUntilChanged().collect {
                notifications.notify(NOTIFICATION_ID, notification(it))
            }
        }
        scope.launch { for (signal in sendSignal) drainSends() }
        try {
            ContextCompat.registerReceiver(this, unlockReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_USER_UNLOCKED); addAction(Intent.ACTION_SCREEN_ON)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            unlockRegistered = true
            networks.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), networkCallback)
            networkRegistered = true
        } catch (e: Exception) { FileLogger.warn("Lifecycle callback registration failed: "+e.javaClass.simpleName) }
        FileLogger.info("Foreground sync service started; UI-independent, manual sending enabled")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ClipboardReadStore.isServiceEnabled(this)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_PAUSE -> {
                settings.paused = intent.getBooleanExtra("paused", false)
                SyncRuntime.receiver.setPaused(settings.paused)
                if (settings.paused) SyncRuntime.sender.clear()
                SyncRuntime.update { it.copy(paused = settings.paused, pendingUnlock = SyncRuntime.receiver.hasDeferred) }
                FileLogger.info(if (settings.paused) "Sync paused" else "Sync resumed")
            }
            ACTION_CONNECT, ACTION_PAIR -> {
                recoveryBlocked = false
                if (intent.action == ACTION_PAIR || SyncRuntime.state.value.address != settings.address) SyncRuntime.receiver.clearDeferred()
                connect(intent.action == ACTION_PAIR)
            }
            else -> recover("Service resume")
        }
        return START_STICKY
    }

    private fun canRecover() = !destroyed && !recoveryBlocked &&
        RecoveryPolicy.enabled(ClipboardReadStore.isServiceEnabled(this), settings.hasPin, settings.address)

    private fun recover(reason: String, replaceConnected: Boolean = false) {
        if (!canRecover() || SyncRuntime.state.value.pairing != null) return
        if (connectionJob?.isActive == true && !(replaceConnected && SyncRuntime.state.value.connected)) return
        FileLogger.info("Restoring saved-PC connection: $reason")
        retryJob?.cancel(); retryJob = null
        connect(false)
    }

    private fun connect(pairing: Boolean) {
        generation++
        val attempt = generation
        retryJob?.cancel(); retryJob = null
        val previous = connectionJob
        client?.close(); previous?.cancel(); SyncRuntime.cancelPairing()
        releaseWifiLock(); SyncRuntime.sender.clear(); SyncRuntime.receiver.disconnect()
        SyncRuntime.update { it.copy(connected = false, connecting = true, error = null, address = settings.address, sendFeedback = null) }
        connectionJob = scope.launch {
            previous?.join()
            var shouldRetry = false
            try {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ClipSync:connection").apply {
                    setReferenceCounted(false); acquire()
                }
                val transport = TlsClipboardClient(applicationContext, settings)
                client = transport
                withContext(Dispatchers.IO) {
                    transport.run(settings.address, pairing,
                        confirm = { code -> withContext(Dispatchers.Main) {
                            check(attempt == generation)
                            withTimeout(120000) { SyncRuntime.requestPairing(attempt, code).await() }
                        } },
                        connected = { withContext(Dispatchers.Main) {
                            check(attempt == generation)
                            SyncRuntime.receiver.connected(settings.paused)
                            SyncRuntime.update { it.copy(connected = true, connecting = false, paired = true, error = null) }
                            FileLogger.info("Connected: TLS 1.3, pinned PC, manual two-way sync")
                            applyDeferred()
                        } },
                        receive = { text -> withContext(Dispatchers.Main) {
                            check(attempt == generation)
                            if (SyncRuntime.sender.remoteArrived(text)) {
                                val applied = SyncRuntime.receiver.receive(text, keyguard.isKeyguardLocked)
                                SyncRuntime.update { it.copy(received = SyncRuntime.receiver.received,
                                    pendingUnlock = SyncRuntime.receiver.hasDeferred) }
                                if (applied) FileLogger.info("Clipboard received: text length=${text.length} hash=${hash(text.toByteArray()).take(6)}")
                                else FileLogger.info(if (SyncRuntime.receiver.hasDeferred) "PC clipboard deferred until unlock (latest only)" else "Clipboard not applied: paused or duplicate")
                            } else FileLogger.info("Incoming clipboard suppressed: outgoing echo")
                        } })
                }
            } catch (e: TimeoutCancellationException) {
                if (attempt == generation) { recoveryBlocked = true; fail("Pairing expired. Open Pair new device on the PC and try again.", e) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (attempt == generation) {
                    val stage = client?.stage ?: ConnectionStage.IDLE
                    shouldRetry = RecoveryPolicy.retry(stage, e, pairing)
                    recoveryBlocked = !shouldRetry
                    val message = if (e is ConnectionProblem) e.message ?: "Connection failed." else ConnectionDiagnostics.userMessage(stage, e)
                    fail(if (shouldRetry) "Connection interrupted. Retrying the saved PC on Wi-Fi…" else message, e)
                }
            } finally {
                if (attempt == generation) {
                    client?.close(); client = null
                    SyncRuntime.cancelPairing(); SyncRuntime.sender.clear(); SyncRuntime.receiver.disconnect()
                    SyncRuntime.update { it.copy(connected = false, connecting = false) }
                    releaseWifiLock()
                    if (shouldRetry && canRecover()) retryJob = scope.launch {
                        delay(RecoveryPolicy.RETRY_MILLIS)
                        recover("Connection retry")
                    }
                }
            }
        }
    }

    private fun offerManualSend(text: String, sensitive: Boolean): ManualSendResult {
        val result = if (settings.paused) ManualSendResult.PAUSED else SyncRuntime.sender.offer(text, sensitive)
        SyncRuntime.update { it.copy(sendFeedback = when (result) {
            ManualSendResult.QUEUED -> "Sending clipboard to PC…"
            ManualSendResult.ECHO -> "Already received from PC; not sent back."
            ManualSendResult.DUPLICATE -> "Already sent; no duplicate needed."
            ManualSendResult.SENSITIVE -> "Sensitive clipboard skipped."
            ManualSendResult.PAUSED -> "Not sent: syncing is paused."
            ManualSendResult.DISCONNECTED -> "Not sent: wait for Connected, then tap Send again."
            ManualSendResult.EMPTY -> "No text to send."
            ManualSendResult.TOO_LARGE -> "Not sent: text exceeds the 1 MiB limit."
        }, pendingUnlock = SyncRuntime.receiver.hasDeferred) }
        FileLogger.info("Manual clipboard action: ${result.name}")
        if (result == ManualSendResult.QUEUED) sendSignal.trySend(Unit)
        return result
    }

    private suspend fun drainSends() {
        while (true) {
            val clip = SyncRuntime.sender.take() ?: return
            val transport = client ?: return
            val attempt = generation
            val watchdog = scope.launch {
                delay(5000)
                if (attempt == generation) { FileLogger.warn("Clipboard send deadline exceeded"); transport.close() }
            }
            try {
                withContext(Dispatchers.IO) { transport.sendText(clip.message.text ?: "") }
                if (attempt == generation && !settings.paused) {
                    SyncRuntime.sender.completed(clip)
                    SyncRuntime.update { it.copy(sent = SyncRuntime.sender.sent, sendFeedback = "Clipboard sent to PC.") }
                    FileLogger.info("Clipboard sent: text length=${clip.message.text?.length ?: 0} hash=${clip.hash.take(6)}")
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (attempt == generation) {
                    SyncRuntime.sender.clear()
                    SyncRuntime.update { it.copy(sendFeedback = "Send interrupted. Tap Send again after reconnecting.") }
                    FileLogger.warn("Manual send failed: ${e.javaClass.simpleName}")
                    transport.close()
                }
                return
            } finally { watchdog.cancel() }
        }
    }

    private fun applyDeferred() {
        if (keyguard.isKeyguardLocked || !SyncRuntime.receiver.hasDeferred) return
        try {
            if (SyncRuntime.receiver.flushAfterUnlock()) FileLogger.info("Deferred PC clipboard applied after unlock")
            SyncRuntime.update { it.copy(received = SyncRuntime.receiver.received, pendingUnlock = SyncRuntime.receiver.hasDeferred) }
        } catch (e: Exception) { FileLogger.warn("Deferred clipboard write failed: "+e.javaClass.simpleName) }
    }
    private fun fail(message: String, exception: Exception) {
        SyncRuntime.update { it.copy(error = message) }
        FileLogger.warn("Connection ended: " + ConnectionDiagnostics.summary(client?.stage ?: ConnectionStage.IDLE, exception))
    }
    private fun releaseWifiLock() { wifiLock?.let { if (it.isHeld) it.release() }; wifiLock = null }
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Do not stop, cancel I/O, clear enabled state, or invent a restart alarm here.
        FileLogger.info("UI task removed; foreground sync remains enabled")
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        destroyed = true; generation++; client?.close(); scope.cancel(); sendSignal.close()
        if (networkRegistered) runCatching { networks.unregisterNetworkCallback(networkCallback) }
        if (unlockRegistered) runCatching { unregisterReceiver(unlockReceiver) }
        SyncRuntime.onManualSend = null; SyncRuntime.cancelPairing(); SyncRuntime.sender.clear()
        SyncRuntime.receiver.disconnect(); SyncRuntime.receiver.clearDeferred(); releaseWifiLock()
        ClipboardReadStore.setServiceRunning(false)
        SyncRuntime.update { it.copy(running = false, connecting = false, connected = false, pendingUnlock = false, error = null) }
        FileLogger.info("Foreground sync service destroyed; enabled preference retained unless explicitly stopped")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun notification(state: SyncUiState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val send = PendingIntent.getActivity(this, 2205, ClipboardReadActivity.createIntent(this, "notification"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = NotificationCompat.Action.Builder(com.clipsync.android.R.drawable.ic_clipsync_status, "Send clipboard now", send)
            .setAuthenticationRequired(true).build()
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.clipsync.android.R.drawable.ic_clipsync_status).setColor(0xFF818CF8.toInt())
            .setContentTitle("ClipSync · ${state.status}")
            .setContentText(when {
                state.pairing != null -> "Open ClipSync to compare pairing codes"
                state.paused -> "Sync paused"
                state.pendingUnlock -> "PC clipboard waiting for unlock"
                state.sendFeedback != null -> state.sendFeedback
                state.connected -> "PC copies arrive automatically. Tap Send clipboard now to send."
                else -> "Waiting for your saved PC on Wi-Fi"
            })
            .setContentIntent(open).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (state.connected && !state.paused) builder.addAction(action)
        return builder.build()
    }
    companion object {
        const val ACTION_CONNECT = "com.clipsync.android.CONNECT"
        const val ACTION_PAIR = "com.clipsync.android.PAIR"
        const val ACTION_PAUSE = "com.clipsync.android.PAUSE"
        const val ACTION_RECOVER = "com.clipsync.android.RECOVER"
        private const val CHANNEL = "clipsync_status_phase2"
        private const val NOTIFICATION_ID = 2201
    }
}
