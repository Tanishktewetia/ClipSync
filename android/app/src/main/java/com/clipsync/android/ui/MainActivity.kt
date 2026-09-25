package com.clipsync.android.ui

import android.Manifest
import android.app.AlertDialog
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import com.clipsync.android.clipboard.ClipboardReadStore
import com.clipsync.android.logging.CrashHandler
import com.clipsync.android.logging.FileLogger
import com.clipsync.android.service.ClipboardWatchService
import com.clipsync.android.ui.theme.ClipSyncTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private var startAfterNotificationPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ClipboardReadStore.initialize(this)
        val lastCrash = CrashHandler.consumeLastCrash()
        FileLogger.info("MainActivity created")

        setContent {
            ClipSyncTheme {
                val running = ClipboardReadStore.serviceRunning.collectAsState().value
                val clipsRead = ClipboardReadStore.clipsRead.collectAsState().value
                val lastLength = ClipboardReadStore.lastLength.collectAsState().value
                MainScreen(
                    lastCrash = lastCrash,
                    serviceRunning = running,
                    clipsRead = clipsRead,
                    lastClipLength = lastLength,
                    onStartReader = { startReader() },
                    onStopReader = { stopReader() },
                    tileSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
                    onOpenTileSettings = { openTileSettings() },
                    onManualRead = { startManualRead() },
                    onCopyLogs = { copyLogsToClipboard() },
                    onShareLogs = { shareLogs() },
                    onSaveLogs = { saveLogsToDownloads() },
                )
            }
        }
    }

    private fun startReader() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            startAfterNotificationPermission = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            return
        }
        ClipboardReadStore.setServiceEnabled(true)
        val serviceIntent = Intent(this, ClipboardWatchService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
        FileLogger.info("Clipboard manual reader service requested")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST && startAfterNotificationPermission) {
            startAfterNotificationPermission = false
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startReader()
            else FileLogger.info("Notification permission denied; clipboard reader not started")
        }
    }

    private fun stopReader() {
        ClipboardReadStore.setServiceEnabled(false)
        stopService(Intent(this, ClipboardWatchService::class.java))
        FileLogger.info("Clipboard watch service stopped")
    }

    private fun openTileSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            statusBarManager.requestAddTileService(
                ComponentName(this, SendClipboardTileService::class.java),
                "Send to PC",
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_send),
                mainExecutor,
            ) { result ->
                FileLogger.info("Quick Settings tile add request completed: result=$result")
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                    Toast.makeText(this, "Send to PC tile added", Toast.LENGTH_SHORT).show()
                } else {
                    showTileInstructions()
                }
            }
            FileLogger.info("Quick Settings tile add request sent")
            return
        }
        showTileInstructions()
    }

    private fun showTileInstructions() {
        AlertDialog.Builder(this)
            .setTitle("Add Send to PC tile")
            .setMessage("1. Swipe down twice from the top of the screen.\n\n2. Tap the pencil or Edit button.\n\n3. Find Send to PC in the available tiles.\n\n4. Drag it into the active tiles area, then tap Done.\n\nAfter copying text, open Quick Settings and tap Send to PC.")
            .setPositiveButton("Got it", null)
            .show()
        FileLogger.info("Displayed manual Quick Settings tile instructions")
    }

    private fun startManualRead() {
        startActivity(ClipboardReadActivity.createIntent(this, "in-app"))
        FileLogger.info("Manual clipboard read requested from ClipSync")
    }

    private fun copyLogsToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ClipSync Logs", FileLogger.getRecentLogs()))
        Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        FileLogger.info("Logs copied to clipboard")
    }

    private fun shareLogs() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ClipSync Diagnostics Logs")
            putExtra(Intent.EXTRA_TEXT, FileLogger.getRecentLogs())
        }
        startActivity(Intent.createChooser(intent, "Share ClipSync logs"))
        FileLogger.info("Logs shared via share sheet")
    }

    private fun saveLogsToDownloads() {
        try {
            val destination = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "clipsync-logs.txt",
            )
            destination.writeText(FileLogger.getAllLogs())
            Toast.makeText(this, "Logs saved to Downloads/clipsync-logs.txt", Toast.LENGTH_SHORT).show()
            FileLogger.info("Logs saved to Downloads")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            FileLogger.error("Failed to save logs to Downloads", e)
        }
    }

    companion object { private const val NOTIFICATION_PERMISSION_REQUEST = 1001 }
}



