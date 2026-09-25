package com.clipsync.core

/** Only hashes live here; never retain clipboard text or an unbounded history. */
class HashGuard {
    var lastAppliedHash: String? = null
        private set
    fun isEcho(text: String): Boolean = hash(text.toByteArray(Charsets.UTF_8)) == lastAppliedHash
    fun shouldApply(fingerprint: String): Boolean = fingerprint != lastAppliedHash
    fun applied(fingerprint: String) { lastAppliedHash = fingerprint }
    fun observeLocal(text: String): Boolean {
        if (isEcho(text)) return true
        lastAppliedHash = null
        return false
    }
}

/** Receive-only adapter around the Phase 3 state machine and hash guard. */
class ReceiveSession(private val writeClipboard: (String) -> Unit) {
    val engine = SyncEngine()
    var received = 0
        private set
    fun connected(paused: Boolean) { engine.connect(); if (paused) engine.pause() }
    fun setPaused(paused: Boolean) {
        if (engine.state == EngineState.DISCONNECTED) return
        if (paused) engine.pause() else engine.resume()
    }
    fun disconnect() = engine.disconnect()
    fun receive(text: String): Boolean {
        if (engine.state != EngineState.CONNECTED) return false
        val fingerprint = hash(text.toByteArray(Charsets.UTF_8))
        if (!engine.hashGuard.shouldApply(fingerprint)) return false
        // A failed platform write must not poison the dedup/echo guard.
        writeClipboard(text)
        engine.apply(ClipVersion(0, "pc", fingerprint, ClipMessage(MessageType.TEXT)))
        received++
        return true
    }
}
