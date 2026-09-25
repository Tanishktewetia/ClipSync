package com.clipsync.android.service

import android.content.Context
import com.clipsync.android.clipboard.ClipboardWriter
import com.clipsync.android.store.SyncSettings
import com.clipsync.core.ReceiveSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PairingPrompt(val id: Long, val code: String)
data class SyncUiState(
    val running: Boolean = false,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val paired: Boolean = false,
    val paused: Boolean = false,
    val address: String = "",
    val received: Int = 0,
    val error: String? = null,
    val pairing: PairingPrompt? = null,
) {
    val status: String get() = when { error != null -> "Error"; paused -> "Paused"; connected -> "Connected"; else -> "Waiting" }
}

/** Process-local state. Mutated on the main thread, including clipboard writes. */
object SyncRuntime {
    private val mutableState = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = mutableState
    lateinit var receiver: ReceiveSession
        private set
    private var initialized = false
    private var decision: CompletableDeferred<Boolean>? = null
    fun initialize(context: Context) {
        if (initialized) return
        val settings = SyncSettings(context)
        receiver = ReceiveSession(ClipboardWriter(context.applicationContext)::write)
        update { it.copy(paused = settings.paused, paired = settings.hasPin, address = settings.address) }
        initialized = true
    }
    fun update(transform: (SyncUiState) -> SyncUiState) { mutableState.value = transform(mutableState.value) }
    fun requestPairing(id: Long, code: String): CompletableDeferred<Boolean> {
        cancelPairing()
        return CompletableDeferred<Boolean>().also { decision = it; update { state -> state.copy(pairing = PairingPrompt(id, code)) } }
    }
    fun answerPairing(id: Long, accepted: Boolean) {
        if (state.value.pairing?.id != id) return
        decision?.complete(accepted)
        update { it.copy(pairing = null) }
    }
    fun cancelPairing() { decision?.cancel(); decision = null; update { it.copy(pairing = null) } }
}
