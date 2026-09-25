package com.clipsync.android.clipboard

import android.content.Context
import com.clipsync.android.logging.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ClipboardReadStore {
    private const val PREFS = "phase2_clipboard"
    private const val KEY_RUNNING = "service_running"
    private const val KEY_ENABLED = "service_enabled"
    private const val KEY_COUNT = "clips_read"
    private const val KEY_LAST_LENGTH = "last_length"

    private var initialized = false
    private lateinit var prefs: android.content.SharedPreferences
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning
    private val _clipsRead = MutableStateFlow(0)
    val clipsRead: StateFlow<Int> = _clipsRead
    private val _lastLength = MutableStateFlow<Int?>(null)
    val lastLength: StateFlow<Int?> = _lastLength

    fun initialize(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _serviceRunning.value = false
        _clipsRead.value = prefs.getInt(KEY_COUNT, 0)
        _lastLength.value = prefs.getInt(KEY_LAST_LENGTH, -1).takeIf { it >= 0 }
        initialized = true
    }

    fun setServiceEnabled(enabled: Boolean) {
        if (!initialized) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isServiceEnabled(context: Context): Boolean {
        initialize(context)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setServiceRunning(running: Boolean) {
        if (!initialized) return
        _serviceRunning.value = running
    }

    fun recordRead(context: Context, text: String) {
        initialize(context)
        com.clipsync.android.service.SyncRuntime.initialize(context)
        if (com.clipsync.android.service.SyncRuntime.receiver.engine.hashGuard.observeLocal(text)) {
            FileLogger.info("Manual read ignored: our own received clipboard")
            return
        }
        val count = _clipsRead.value + 1
        val hash = com.clipsync.core.hash(text.toByteArray(Charsets.UTF_8)).take(6)
        _clipsRead.value = count
        _lastLength.value = text.length
        prefs.edit().putInt(KEY_COUNT, count).putInt(KEY_LAST_LENGTH, text.length).apply()
        FileLogger.info("Clipboard read: length=${text.length}, hash=$hash")
    }
}
