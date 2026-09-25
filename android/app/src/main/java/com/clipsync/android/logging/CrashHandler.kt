package com.clipsync.android.logging

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught exception handler. Writes crash info to a file
 * and shows it on next launch via Diagnostics.
 */
object CrashHandler {
    private lateinit var crashFile: File
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        crashFile = File(context.filesDir, "last_crash.txt")
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            pw.println("Crash at ${dateFormat.format(Date())}")
            pw.println("Thread: ${thread.name}")
            pw.println()
            throwable.printStackTrace(pw)
            crashFile.writeText(sw.toString())
            FileLogger.error("CRASH on thread ${thread.name}", throwable)
        } catch (_: Exception) {
            // Last resort — don't throw from the crash handler
        }
    }

    /**
     * Returns last crash info and clears it, or null if no crash.
     */
    fun consumeLastCrash(): String? {
        return try {
            if (crashFile.exists()) {
                val text = crashFile.readText()
                crashFile.delete()
                text
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
