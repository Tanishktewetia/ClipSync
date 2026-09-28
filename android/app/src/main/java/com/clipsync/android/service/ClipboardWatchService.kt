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
import com.clipsync.android.store.*
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
    private lateinit var pairingController: PairingController
    private var reachabilityJob: Job? = null
    private var retryAttempt = 0
    private lateinit var journal: com.clipsync.core.ReconnectJournal
    private var destroyed = false
    private var recoveryBlocked = false
    private var networkRegistered = false
    private var unlockRegistered = false
    private val sendSignal = Channel<Unit>(Channel.CONFLATED)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { scope.launch { refreshReachability(); recover("Wi-Fi available") } }
        override fun onLost(network: Network) { scope.launch {
            if (client?.network == network || !hasWifi()) {
                client?.close()
                SyncRuntime.update { it.copy(connected = false, connecting = false, devices = DevicePolicy.wifiLost(it.devices)) }
            }
        } }
    }
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (keyguard.isKeyguardLocked) return
            if (intent?.action == Intent.ACTION_USER_PRESENT || intent?.action == Intent.ACTION_USER_UNLOCKED || intent?.action == Intent.ACTION_SCREEN_ON) {
                applyDeferred()
                // A socket can look connected after sleep even when the link has died.
                // Unlock can outpace the heartbeat timeout; replace the route for immediate recovery.
                recover("Phone unlocked", replaceConnected = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = SyncSettings(this)
        journal = com.clipsync.core.ReconnectJournal(settings.deviceId)
        ClipboardReadStore.initialize(this)
        SyncRuntime.initialize(this)
        pairingController = PairingController(applicationContext, settings, scope, ::saveDevices)
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
        scope.launch { while (isActive) { refreshReachability(); delay(30000) } }
        FileLogger.info("Foreground sync service started; UI-independent, manual sending enabled")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ClipboardReadStore.isServiceEnabled(this)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_PAUSE -> {
                if (!SyncRuntime.state.value.connected) return START_STICKY
                settings.paused = intent.getBooleanExtra("paused", false)
                SyncRuntime.receiver.setPaused(settings.paused)
                if (settings.paused) { SyncRuntime.sender.clear(); journal.clear() }
                SyncRuntime.update { it.copy(paused = settings.paused, pendingUnlock = SyncRuntime.receiver.hasDeferred) }
                updateActive(if (settings.paused) DeviceState.Paused else DeviceState.Connected)
                FileLogger.info(if (settings.paused) "Sync paused" else "Sync resumed")
            }
            ACTION_PAIR -> pairingController.start(intent.getStringExtra("address") ?: "", intent.getStringExtra("deviceId"))
            ACTION_FORGET -> {
                val id = intent.getStringExtra("deviceId")
                if (SyncRuntime.state.value.devices.any { it.id == id && it.isActive }) {
                    generation++; client?.close(); connectionJob?.cancel(); retryJob?.cancel(); journal.clear(); releaseWifiLock()
                    SyncRuntime.resetSession(this); settings.paused = false
                    SyncRuntime.update { it.copy(connected = false, connecting = false, paused = false) }
                }
                saveDevices(SyncRuntime.state.value.devices.filterNot { it.id == id })
            }
            ACTION_CONNECT -> {
                val id = intent.getStringExtra("deviceId")
                val target = SyncRuntime.state.value.devices.firstOrNull { it.id == id }
                // Main-thread ownership is the connect-lock; reject competing activations until resolved.
                if (target != null && !SyncRuntime.state.value.connecting) {
                    if (!target.isActive) {
                        journal.clear(); SyncRuntime.resetSession(this); settings.paused = false
                    }
                    saveDevices(DevicePolicy.activate(SyncRuntime.state.value.devices, target.id))
                    recoveryBlocked = false; retryAttempt = 0; connect()
                }
            }
            else -> recover("Service resume")
        }
        return START_STICKY
    }

    private fun canRecover() = !destroyed && !recoveryBlocked &&
        ClipboardReadStore.isServiceEnabled(this) && SyncRuntime.state.value.devices.any { it.isActive } && hasWifi()

    private fun recover(reason: String, replaceConnected: Boolean = false) {
        if (!canRecover()) return
        if (connectionJob?.isActive == true && !(replaceConnected && SyncRuntime.state.value.connected)) return
        FileLogger.info("Restoring discovered-PC connection: $reason")
        retryJob?.cancel(); retryJob = null
        connect()
    }

    private fun connect() {
        val selected = SyncRuntime.state.value.devices.firstOrNull { it.isActive } ?: return
        if (!hasWifi()) { updateActive(DeviceState.Unreachable); return }
        generation++
        val attempt = generation
        retryJob?.cancel(); retryJob = null
        val previous = connectionJob
        client?.close(); previous?.cancel()
        releaseWifiLock(); SyncRuntime.sender.clear(); SyncRuntime.receiver.disconnect()
        SyncRuntime.update { it.copy(connected = false, connecting = true, error = null, address = settings.address, sendFeedback = null) }
        updateActive(DeviceState.Connecting)
        connectionJob = scope.launch {
            previous?.join()
            var shouldRetry = false
            try {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ClipSync:connection").apply {
                    setReferenceCounted(false); acquire()
                }
                val endpoint = withContext(Dispatchers.IO) {
                    com.clipsync.android.security.KeyStoreIdentity()
                    PcDiscovery(applicationContext, settings).race(false) { hint ->
                        val identity = try { ControlClient(applicationContext, selected.certFingerprint).probe(hint) }
                        catch (e: Exception) {
                            if (generateSequence<Throwable>(e) { it.cause }.any { it is com.clipsync.android.security.PeerPinMismatchException } && "${hint.address}:${hint.port}" == selected.lastKnownAddress)
                                withContext(Dispatchers.Main) { updateActive(DeviceState.IdentityChanged) }
                            throw e
                        }
                        if (!identity.fingerprint.equals(selected.certFingerprint, true)) {
                            if ("${hint.address}:${hint.port}" == selected.lastKnownAddress) withContext(Dispatchers.Main) { updateActive(DeviceState.IdentityChanged) }
                            throw java.io.IOException("Candidate does not match selected PC")
                        }
                        hint
                    }
                }
                val transport = TlsClipboardClient(applicationContext, settings, journal, selected.certFingerprint)
                client = transport
                withContext(Dispatchers.IO) {
                    transport.run(endpoint.address, endpoint.port,
                        connected = { withContext(Dispatchers.Main) {
                            check(attempt == generation)
                            check(hasWifi())
                            retryAttempt = 0
                            settings.lastAddress = endpoint.address; settings.lastPort = endpoint.port
                            val now = System.currentTimeMillis()
                            saveDevices(SyncRuntime.state.value.devices.map { if (it.id == selected.id) it.copy(lastKnownAddress = "${endpoint.address}:${endpoint.port}", lastConnectedAt = now, connectedSince = now, connectionState = if (settings.paused) DeviceState.Paused else DeviceState.Connected) else it })
                            SyncRuntime.receiver.connected(settings.paused)
                            SyncRuntime.update { it.copy(connected = true, connecting = false, paired = true, paused = settings.paused, error = null) }
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
                if (attempt == generation) { recoveryBlocked = true; fail("Connection timed out. Try Connect again.", e) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (attempt == generation) {
                    val stage = client?.stage ?: ConnectionStage.IDLE
                    shouldRetry = (client == null && e is java.io.IOException) || RecoveryPolicy.retry(stage, e, false)
                    recoveryBlocked = !shouldRetry
                    val message = if (e is ConnectionProblem) e.message ?: "Connection failed." else ConnectionDiagnostics.userMessage(stage, e)
                    fail(if (shouldRetry) "Connection interrupted. Searching for your PC on Wi-Fi…" else message, e)
                }
            } finally {
                if (attempt == generation) {
                    client?.close(); client = null
                    SyncRuntime.sender.clear(); SyncRuntime.receiver.disconnect()
                    val identityChanged = SyncRuntime.state.value.devices.any { it.isActive && it.connectionState == DeviceState.IdentityChanged }
                    if (identityChanged) { recoveryBlocked = true; shouldRetry = false } else updateActive(DeviceState.Unreachable)
                    SyncRuntime.update { it.copy(connected = false, connecting = false) }
                    releaseWifiLock()
                    if (shouldRetry && canRecover()) retryJob = scope.launch {
                        delay(RecoveryPolicy.delayMillis(retryAttempt++))
                        recover("Connection retry")
                    }
                }
            }
        }
    }

    private fun hasWifi(): Boolean {
        @Suppress("DEPRECATION")
        return networks.allNetworks.any { networks.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }
    private fun saveDevices(devices: List<PairedDevice>) {
        settings.saveDevices(devices)
        SyncRuntime.update { it.copy(devices = DevicePolicy.ordered(devices), paired = devices.isNotEmpty()) }
    }
    private fun updateActive(requested: DeviceState) {
        val state = if (hasWifi()) requested else DeviceState.Unreachable
        SyncRuntime.update { old -> old.copy(devices = old.devices.map { if (it.isActive) it.copy(connectionState = state,
            connectedSince = if (state in setOf(DeviceState.Connected, DeviceState.Paused)) it.connectedSince else null) else it }) }
    }
    private fun refreshReachability() {
        // An empty dashboard has nothing to monitor. Discovery runs only on explicit Add PC.
        if (SyncRuntime.state.value.devices.isEmpty()) return
        if (reachabilityJob?.isActive == true) return
        if (!hasWifi()) { SyncRuntime.update { it.copy(devices = DevicePolicy.wifiLost(it.devices)) }; return }
        @Suppress("DEPRECATION")
        val hotspot = networks.allNetworks.any { networks.getLinkProperties(it)?.routes?.any { route -> route.gateway?.hostAddress == "192.168.137.1" } == true }
        SyncRuntime.update { it.copy(networkLabel = if (hotspot) "PC hotspot (Wi-Fi)" else "Wi-Fi (network name private)") }
        reachabilityJob = scope.launch {
            val seen = mutableSetOf<String>()
            val scanned = SyncRuntime.state.value.devices.map { it.id }.toSet()
            try {
                PcDiscovery(applicationContext, settings).scan(7000) { endpoint ->
                    val probe = ControlClient(applicationContext)
                    val identity = try { withContext(Dispatchers.IO) { probe.probe(endpoint) } }
                    catch (e: Exception) {
                        val observed = probe.observedFingerprint
                        if (observed != null) withContext(Dispatchers.Main) {
                            if (!hasWifi()) return@withContext
                            SyncRuntime.update { old -> old.copy(devices = old.devices.map { d ->
                                if (!d.isActive && d.lastKnownAddress == "${endpoint.address}:${endpoint.port}" && !d.certFingerprint.equals(observed, true)) d.copy(connectionState = DeviceState.IdentityChanged) else d
                            }) }
                        }
                        throw e
                    }
                    withContext(Dispatchers.Main) {
                        if (!hasWifi()) return@withContext
                        val address = "${endpoint.address}:${endpoint.port}"
                        SyncRuntime.update { old -> old.copy(devices = old.devices.map { device ->
                            when {
                                device.certFingerprint.equals(identity.fingerprint, true) -> {
                                    seen.add(device.id)
                                    if (!device.isActive) device.copy(lastKnownAddress = address, connectionState = DeviceState.Available) else device
                                }
                                device.lastKnownAddress == address && !device.isActive -> device.copy(connectionState = DeviceState.IdentityChanged)
                                else -> device
                            }
                        }) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            if (!hasWifi()) { SyncRuntime.update { it.copy(devices = DevicePolicy.wifiLost(it.devices)) }; return@launch }
            SyncRuntime.update { old -> old.copy(devices = old.devices.map { if (!it.isActive && it.id in scanned && it.id !in seen && it.connectionState != DeviceState.IdentityChanged) it.copy(connectionState = DeviceState.Unreachable) else it }) }
        }
    }
    private fun offerManualSend(text: String, sensitive: Boolean): ManualSendResult {
        val offline = !SyncRuntime.state.value.connected
        val result = if (settings.paused) ManualSendResult.PAUSED
        else if (text.toByteArray().size > com.clipsync.core.SessionProtocol.MAX_TEXT) ManualSendResult.TOO_LARGE
        else if (offline) {
            when {
                text.isEmpty() -> ManualSendResult.EMPTY
                hash(text.toByteArray()) == SyncRuntime.receiver.engine.hashGuard.lastAppliedHash -> ManualSendResult.ECHO
                sensitive -> ManualSendResult.SENSITIVE
                SyncRuntime.state.value.devices.none { it.isActive } -> ManualSendResult.DISCONNECTED
                else -> { journal.local(text); SyncRuntime.receiver.clearDeferred(); ManualSendResult.QUEUED }
            }
        } else SyncRuntime.sender.offer(text, sensitive)
        SyncRuntime.update { it.copy(sendFeedback = when (result) {
            ManualSendResult.QUEUED -> if (offline) "Latest text held in memory for reconnect." else "Sending clipboard to PC…"
            ManualSendResult.ECHO -> "Already received from PC; not sent back."
            ManualSendResult.DUPLICATE -> "Already sent; no duplicate needed."
            ManualSendResult.SENSITIVE -> "Sensitive clipboard skipped."
            ManualSendResult.PAUSED -> "Not sent: syncing is paused."
            ManualSendResult.DISCONNECTED -> "Not sent: wait for Connected, then tap Send again."
            ManualSendResult.EMPTY -> "No text to send."
            ManualSendResult.TOO_LARGE -> "Not sent: text exceeds the 1 MiB limit."
        }, pendingUnlock = SyncRuntime.receiver.hasDeferred) }
        FileLogger.info("Manual clipboard action: ${result.name}")
        if (result == ManualSendResult.QUEUED && !offline) sendSignal.trySend(Unit)
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
        pairingController.close()
        destroyed = true; generation++; client?.close(); scope.cancel(); sendSignal.close()
        if (networkRegistered) runCatching { networks.unregisterNetworkCallback(networkCallback) }
        if (unlockRegistered) runCatching { unregisterReceiver(unlockReceiver) }
        SyncRuntime.onManualSend = null; SyncRuntime.cancelPairing(); SyncRuntime.sender.clear()
        SyncRuntime.receiver.disconnect(); SyncRuntime.receiver.clearDeferred(); releaseWifiLock()
        ClipboardReadStore.setServiceRunning(false)
        SyncRuntime.update { it.copy(running = false, connecting = false, connected = false, pendingUnlock = false, error = null, devices = DevicePolicy.restore(it.devices)) }
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
        if (state.paired && !state.paused) builder.addAction(action)
        return builder.build()
    }
    companion object {
        const val ACTION_FORGET = "com.clipsync.android.FORGET"
        const val ACTION_CONNECT = "com.clipsync.android.CONNECT"
        const val ACTION_PAIR = "com.clipsync.android.PAIR"
        const val ACTION_PAUSE = "com.clipsync.android.PAUSE"
        const val ACTION_RECOVER = "com.clipsync.android.RECOVER"
        private const val CHANNEL = "clipsync_status_phase2"
        private const val NOTIFICATION_ID = 2201
    }
}
