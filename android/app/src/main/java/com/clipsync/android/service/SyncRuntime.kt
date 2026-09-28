package com.clipsync.android.service

import android.content.Context
import com.clipsync.android.clipboard.ClipboardWriter
import com.clipsync.android.store.SyncSettings
import com.clipsync.core.ReceiveSession
import com.clipsync.core.ManualSendSession
import com.clipsync.core.ManualSendResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PairingPrompt(val id: Long, val code: String, val name: String = "PC", val error: String? = null, val busy: Boolean = false)
data class SyncUiState(
    val devices: List<com.clipsync.android.store.PairedDevice> = emptyList(),
    val searching: Boolean = false,
    val waitingPairs: Int = 0,
    val pairingMessage: String? = null,
    val pairingFailed: Boolean = false,
    val networkLabel: String = "Wi-Fi",
    val running: Boolean = false,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val paired: Boolean = false,
    val paused: Boolean = false,
    val address: String = "",
    val received: Int = 0,
    val sent: Int = 0,
    val pendingUnlock: Boolean = false,
    val sendFeedback: String? = null,
    val error: String? = null,
    val pairing: PairingPrompt? = null,
) {
    val status: String get() = when { paused -> "Paused"; error != null -> "Error"; connected -> "Connected"; else -> "Waiting" }
}

/** Process-local state. Mutated on the main thread, including clipboard writes. */
object SyncRuntime {
    private val mutableState = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = mutableState
    lateinit var receiver: ReceiveSession
        private set
    lateinit var sender: ManualSendSession
        private set
    var onManualSend: ((String, Boolean) -> ManualSendResult)? = null
    var onPairCode: ((Long, String?) -> Unit)? = null
    private var initialized = false
    var feedbackRevision: Long = 0
        private set
    fun clearPairingFeedback() {
        feedbackRevision++
        update { it.copy(pairingMessage = null, pairingFailed = false) }
    }
    fun updatePairingFeedback(revision: Long, transform: (SyncUiState) -> SyncUiState) {
        if (revision == feedbackRevision) update(transform)
    }
    fun initialize(context: Context) {
        if (initialized) return
        val settings = SyncSettings(context)
        receiver = ReceiveSession(ClipboardWriter(context.applicationContext)::write)
        sender = ManualSendSession(receiver)
        val devices = com.clipsync.android.store.DevicePolicy.restore(settings.devices())
        update { it.copy(devices = devices, paused = settings.paused, paired = devices.isNotEmpty(), address = settings.address) }
        initialized = true
    }
    fun resetSession(context: Context) {
        sender.clear(); receiver.disconnect(); receiver.clearDeferred()
        receiver = ReceiveSession(ClipboardWriter(context.applicationContext)::write)
        sender = ManualSendSession(receiver)
    }
    fun update(transform: (SyncUiState) -> SyncUiState) {
        val before = mutableState.value
        var next = transform(before)
        if (!before.connected && next.connected) {
            feedbackRevision++
            next = next.copy(pairingMessage = null, pairingFailed = false)
        }
        mutableState.value = next
    }
    fun cancelPairing() { update { it.copy(pairing = null, waitingPairs = 0, searching = false) } }
}
