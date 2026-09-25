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
import androidx.compose.runtime.collectAsState
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
    private var pendingAction: String? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction; pendingAction = null
        if (granted && action != null) startConnection(action)
        else SyncRuntime.update { it.copy(error = "Notification permission was declined. Tap Connect again to allow the background status notification.") }
    }
    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) exportLogs(uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = SyncSettings(this)
        pendingAction = savedInstanceState?.getString("pending_connection")
        ClipboardReadStore.initialize(this)
        SyncRuntime.initialize(this)
        val lastCrash = CrashHandler.consumeLastCrash()
        FileLogger.info("Phase 5 main screen opened")
        setContent {
            ClipSyncTheme {
                MainScreen(
                    state = SyncRuntime.state.collectAsState().value,
                    lastCrash = lastCrash,
                    onConnect = ::connect,
                    onPause = ::pause,
                    onStop = ::stopConnection,
                    onPairingAnswer = SyncRuntime::answerPairing,
                    onCopyLogs = ::copyLogs,
                    onShareLogs = ::shareLogs,
                    onSaveLogs = ::saveLogs,
                    onBatterySettings = { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
                )
            }
        }
        // Restarts only an already enabled, paired session. No background discovery.
        if (ClipboardReadStore.isServiceEnabled(this) && !SyncRuntime.state.value.running && settings.hasPin) {
            runCatching { ContextCompat.startForegroundService(this, Intent(this, ClipboardWatchService::class.java)) }
                .onFailure { SyncRuntime.update { state -> state.copy(error = "Tap Reconnect to restart the background connection.") } }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_connection", pendingAction)
        super.onSaveInstanceState(outState)
    }
    private fun connect(address: String, pairing: Boolean) {
        val valid = try { ManualAddress.parse(address) } catch (e: IllegalArgumentException) {
            SyncRuntime.update { it.copy(error = e.message) }; return
        }
        settings.address = valid
        SyncRuntime.update { it.copy(address = valid, error = null) }
        val action = if (pairing) ClipboardWatchService.ACTION_PAIR else ClipboardWatchService.ACTION_CONNECT
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingAction = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startConnection(action)
    }
    private fun startConnection(action: String) {
        ClipboardReadStore.setServiceEnabled(true)
        try {
            ContextCompat.startForegroundService(this, Intent(this, ClipboardWatchService::class.java).setAction(action))
            requestBatteryExemptionOnce()
        } catch (e: Exception) {
            ClipboardReadStore.setServiceEnabled(false)
            FileLogger.warn("Service start failed: "+e.javaClass.simpleName)
            SyncRuntime.update { it.copy(error = "Could not start the background connection. Try connecting again.") }
        }
    }
    private fun requestBatteryExemptionOnce() {
        if (settings.batteryRequested) return
        settings.batteryRequested = true
        if (getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            FileLogger.warn("Battery exemption prompt unavailable: "+e.javaClass.simpleName)
        }
    }
    private fun pause(paused: Boolean) {
        settings.paused = paused
        SyncRuntime.receiver.setPaused(paused)
        SyncRuntime.update { it.copy(paused = paused) }
        if (SyncRuntime.state.value.running) startService(Intent(this, ClipboardWatchService::class.java)
            .setAction(ClipboardWatchService.ACTION_PAUSE).putExtra("paused", paused))
    }
    private fun stopConnection() {
        ClipboardReadStore.setServiceEnabled(false)
        stopService(Intent(this, ClipboardWatchService::class.java))
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
