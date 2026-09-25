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

/** Platform-independent incoming path; holds at most one remote clip while locked. */
class ReceiveSession(private val writeClipboard: (String) -> Unit) {
    val engine = SyncEngine()
    private var deferred: String? = null
    val hasDeferred: Boolean get() = deferred != null
    fun clearDeferred() { deferred = null }
    var received = 0
        private set
    fun connected(paused: Boolean) { engine.connect(); if (paused) engine.pause() }
    fun setPaused(paused: Boolean) {
        if (paused) clearDeferred()
        if (engine.state == EngineState.DISCONNECTED) return
        if (paused) engine.pause() else engine.resume()
    }
    fun disconnect() = engine.disconnect()
    fun flushAfterUnlock(): Boolean {
        val text = deferred ?: return false
        if (engine.state != EngineState.CONNECTED) return false
        return try { receive(text) } catch (error: Exception) { deferred = text; throw error }
    }
    fun receive(text: String, locked: Boolean = false): Boolean {
        if (engine.state != EngineState.CONNECTED) return false
        if (locked) { deferred = text; return false }
        deferred = null
        val fingerprint = hash(text.toByteArray(Charsets.UTF_8))
        if (!engine.hashGuard.shouldApply(fingerprint)) return false
        // A failed platform write must not poison the dedup/echo guard.
        writeClipboard(text)
        engine.apply(ClipVersion(0, "pc", fingerprint, ClipMessage(MessageType.TEXT)))
        received++
        return true
    }
}
