package com.clipsync.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.clipsync.android.logging.CrashHandler
import com.clipsync.android.logging.FileLogger
import com.clipsync.android.ui.theme.ClipSyncTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check for crash from last session
        val lastCrash = CrashHandler.consumeLastCrash()

        FileLogger.info("MainActivity created")

        setContent {
            ClipSyncTheme {
                MainScreen(
                    lastCrash = lastCrash,
                    onCopyLogs = { copyLogsToClipboard() },
                    onShareLogs = { shareLogs() },
                    onSaveLogs = { saveLogsToDownloads() },
                    onConnectionTest = { host, port, message ->
                        withContext(Dispatchers.IO) { ConnectionTestClient.send(host, port, message) }
                    },
                )
            }
        }
    }

    private fun copyLogsToClipboard() {
        val logs = FileLogger.getRecentLogs()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ClipSync Logs", logs))
        Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        FileLogger.info("Logs copied to clipboard")
    }

    private fun shareLogs() {
        val logs = FileLogger.getRecentLogs()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ClipSync Diagnostics Logs")
            putExtra(Intent.EXTRA_TEXT, logs)
        }
        startActivity(Intent.createChooser(intent, "Share ClipSync logs"))
        FileLogger.info("Logs shared via share sheet")
    }

    private fun saveLogsToDownloads() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, "clipsync-logs.txt")
            destFile.writeText(FileLogger.getAllLogs())
            Toast.makeText(this, "Logs saved to Downloads/clipsync-logs.txt", Toast.LENGTH_SHORT).show()
            FileLogger.info("Logs saved to Downloads")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            FileLogger.error("Failed to save logs to Downloads", e)
        }
    }
}

