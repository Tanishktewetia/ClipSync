package com.clipsync.android.logging

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple file logger. Writes to app-internal storage.
 * Never logs clipboard content — only type, size, and truncated hashes.
 */
object FileLogger {
    private lateinit var logDir: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val lock = Any()

    fun init(context: Context) {
        logDir = File(context.filesDir, "logs")
        logDir.mkdirs()
        cleanOldLogs()
    }

    fun getLogDir(): File = logDir

    fun info(message: String) = log("INFO", message)
    fun warn(message: String) = log("WARN", message)
    fun error(message: String) = log("ERROR", message)
    fun error(message: String, throwable: Throwable) = log("ERROR", "$message: ${throwable.stackTraceToString()}")

    fun log(level: String, message: String) {
        synchronized(lock) {
            try {
                val today = fileDateFormat.format(Date())
                val file = File(logDir, "clipsync-$today.log")
                FileWriter(file, true).use { writer ->
                    val timestamp = dateFormat.format(Date())
                    writer.appendLine("[$timestamp] [$level] $message")
                }
            } catch (e: Exception) {
                // Best effort — don't crash over logging
            }
        }
    }

    /**
     * Returns all log content as a string (most recent file first).
     */
    fun getAllLogs(): String {
        return try {
            val files = logDir.listFiles { f -> f.name.startsWith("clipsync-") && f.name.endsWith(".log") }
                ?.sortedByDescending { it.name }
                ?: return "No logs found."
            if (files.isEmpty()) return "No logs found."
            buildString {
                for ((index, file) in files.withIndex()) {
                    if (index > 0) appendLine()
                    appendLine("--- ${file.name} ---")
                    appendLine()
                    append(file.readText())
                }
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    /**
     * Returns the most recent log entries (last N lines from today's file).
     */
    fun getRecentLogs(maxLines: Int = 200): String {
        return try {
            val today = fileDateFormat.format(Date())
            val file = File(logDir, "clipsync-$today.log")
            if (!file.exists()) return "No logs for today."
            val lines = file.readLines()
            val start = maxOf(0, lines.size - maxLines)
            lines.subList(start, lines.size).joinToString("\n")
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    private fun cleanOldLogs() {
        try {
            val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            logDir.listFiles { f -> f.name.startsWith("clipsync-") && f.lastModified() < cutoff }
                ?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
