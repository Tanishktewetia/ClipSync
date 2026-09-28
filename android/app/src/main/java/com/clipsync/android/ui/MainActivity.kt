package com.clipsync.android.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.clipsync.android.clipboard.ClipboardReadStore
import com.clipsync.android.logging.CrashHandler
import com.clipsync.android.logging.FileLogger
import com.clipsync.android.security.ManualAddress
import com.clipsync.android.service.ClipboardWatchService
import com.clipsync.android.service.SyncRuntime
import com.clipsync.android.store.SyncSettings
import com.clipsync.android.ui.theme.ClipSyncTheme

class MainActivity : ComponentActivity() {
    private lateinit var settings: SyncSettings
    private var setupRevision by mutableIntStateOf(0)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { setupRevision++ }
    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) exportLogs(uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = SyncSettings(this)
        ClipboardReadStore.initialize(this)
        SyncRuntime.initialize(this)
        val lastCrash = CrashHandler.consumeLastCrash()
        FileLogger.info("Phase 8.5 dashboard/setup opened")
        setContent {
            var replay by remember { mutableStateOf(settings.replayOnConnect) }
            var step by remember { mutableIntStateOf(settings.onboardingStep.coerceIn(0, 3)) }
            var done by remember { mutableStateOf(settings.onboardingDone) }
            val revision = setupRevision
            val notifications = remember(revision) { androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled() }
            val battery = remember(revision) { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) }
            val tile = remember(revision) { settings.tileAdded || settings.tileDismissed }
            ClipSyncTheme {
                if (!done) OnboardingScreen(step, notifications, battery, settings.tileAdded,
                    advance = { step = it; settings.onboardingStep = it },
                    finish = { done = true; settings.onboardingDone = true },
                    requestNotifications = ::requestNotifications, requestBattery = ::requestBattery, addTile = ::addSendTile)
                else MainScreen(state = SyncRuntime.state.collectAsState().value, lastCrash = lastCrash,
                    notifications = notifications, battery = battery, tile = tile,
                    onNotifications = ::requestNotifications, onBattery = ::requestBattery, onTile = ::addSendTile,
                    onDismissTile = { settings.tileDismissed = true; setupRevision++ },
                    onRedo = { step = 0; settings.onboardingStep = 0; done = false; settings.onboardingDone = false },
                    onConnect = { command(ClipboardWatchService.ACTION_CONNECT, it) },
                    onPair = ::pair, onPause = ::pause,
                    onForget = { command(ClipboardWatchService.ACTION_FORGET, it) },
                    onRename = { id, name ->
                        val devices = SyncRuntime.state.value.devices.map { if (it.id == id) it.copy(displayName = name) else it }
                        settings.saveDevices(devices); SyncRuntime.update { it.copy(devices = devices) }
                    },
                    onPairCode = { id, code -> SyncRuntime.onPairCode?.invoke(id, code) },
                    onCopyLogs = ::copyLogs, onShareLogs = ::shareLogs, onSaveLogs = ::saveLogs,
                    replayOnConnect = replay, onReplayChanged = { replay = it; settings.replayOnConnect = it })
            }
        }
        // Restarts only an already enabled, paired discovery session.
        if (ClipboardReadStore.isServiceEnabled(this) && !SyncRuntime.state.value.running && settings.devices().isNotEmpty()) {
            runCatching { ContextCompat.startForegroundService(this, Intent(this, ClipboardWatchService::class.java)) }
                .onFailure { SyncRuntime.update { state -> state.copy(error = "Tap Reconnect to restart the background connection.") } }
        }
    }
    override fun onResume() {
        super.onResume()
        setupRevision++
        if (::settings.isInitialized && SyncRuntime.state.value.running) {
            startService(Intent(this, ClipboardWatchService::class.java).setAction(ClipboardWatchService.ACTION_RECOVER))
        }
    }
    private fun addSendTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                    android.content.ComponentName(this, SendClipboardTileService::class.java), "Send to PC",
                    android.graphics.drawable.Icon.createWithResource(this, com.clipsync.android.R.drawable.ic_clipsync_status), mainExecutor,
                ) { result ->
                    if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) settings.tileAdded = true
                    setupRevision++
                }
            } catch (e: Exception) { FileLogger.warn("Tile add request unavailable: "+e.javaClass.simpleName) }
        } else {
            Toast.makeText(this, "Swipe down twice, tap Edit, and add Send to PC.", Toast.LENGTH_LONG).show()
        }
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !settings.notificationRequested) {
            settings.notificationRequested = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else runCatching {
            val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }
    private fun requestBattery() {
        if (getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) return
        runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
    }
    private fun pair(address: String, replacement: String?) {
        try { if (address.isNotBlank()) com.clipsync.android.service.PairingController.endpoint(address) }
        catch (e: IllegalArgumentException) { SyncRuntime.update { it.copy(pairingMessage = e.message, pairingFailed = true) }; return }
        command(ClipboardWatchService.ACTION_PAIR, replacement, address)
    }
    private fun command(action: String, deviceId: String? = null, address: String = "") {
        ClipboardReadStore.setServiceEnabled(true)
        try {
            ContextCompat.startForegroundService(this, Intent(this, ClipboardWatchService::class.java).setAction(action)
                .putExtra("deviceId", deviceId).putExtra("address", address))
        } catch (e: Exception) {
            FileLogger.warn("Service start failed: " + e.javaClass.simpleName)
            SyncRuntime.update { it.copy(error = "Could not start background sync. Try again from the dashboard.") }
        }
    }
    private fun pause(paused: Boolean) {
        settings.paused = paused
        SyncRuntime.receiver.setPaused(paused)
        if (paused) SyncRuntime.sender.clear()
        SyncRuntime.update { it.copy(paused = paused, pendingUnlock = SyncRuntime.receiver.hasDeferred) }
        if (SyncRuntime.state.value.running) startService(Intent(this, ClipboardWatchService::class.java)
            .setAction(ClipboardWatchService.ACTION_PAUSE).putExtra("paused", paused))
    }
    private fun copyLogs() {
        val text = FileLogger.getRecentLogs()
        SyncRuntime.receiver.engine.hashGuard.observeLocal(text)
        val clip = ClipData.newPlainText("ClipSync Diagnostics", text).apply {
            description.extras = PersistableBundle().apply { putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true) }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        FileLogger.info("Diagnostics copied by user")
    }
    private fun shareLogs() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "ClipSync Diagnostics")
            putExtra(Intent.EXTRA_TEXT, FileLogger.getRecentLogs())
        }, "Share ClipSync diagnostics"))
    }
    private fun saveLogs() {
        if (Build.VERSION.SDK_INT < 29) { saveDocument.launch("clipsync-logs.txt"); return }
        var uri: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "clipsync-logs-${System.currentTimeMillis()}.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("No Downloads destination")
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(FileLogger.getRecentLogs()) } ?: error("No output stream")
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            Toast.makeText(this, "Diagnostics saved to Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            uri?.let { runCatching { contentResolver.delete(it, null, null) } }
            FileLogger.warn("Diagnostics save failed: ${e.javaClass.simpleName}")
            Toast.makeText(this, "Could not save. Use Share logs instead.", Toast.LENGTH_LONG).show()
        }
    }
    private fun exportLogs(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(FileLogger.getRecentLogs()) } ?: error("No output stream")
            Toast.makeText(this, "Diagnostics saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { FileLogger.warn("Diagnostics export failed: ${e.javaClass.simpleName}") }
    }
}
