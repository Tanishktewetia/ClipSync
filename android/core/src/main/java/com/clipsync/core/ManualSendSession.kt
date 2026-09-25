package com.clipsync.core

enum class ManualSendResult { QUEUED, ECHO, DUPLICATE, SENSITIVE, PAUSED, DISCONNECTED, EMPTY, TOO_LARGE }

/** Explicit user reads only. Main-thread owner; I/O runs separately. No clipboard listener,
 * disk queue, or image conversion. A burst keeps at most one scheduled and one in-flight clip.
 */
class ManualSendSession(private val receiver: ReceiveSession) {
    private val clock = LamportClock()
    private var inFlight: ClipVersion? = null
    private var lastSentHash: String? = null
    private var remoteRevision = 0L
    private var inFlightRevision = 0L
    var sent = 0
        private set
    fun offer(text: String, sensitive: Boolean): ManualSendResult {
        if (text.isEmpty()) return ManualSendResult.EMPTY
        if (text.length > FrameCodec.MAX || text.toByteArray(Charsets.UTF_8).size > FrameCodec.MAX) return ManualSendResult.TOO_LARGE
        val fingerprint = hash(text.toByteArray(Charsets.UTF_8))
        // Received clips are sensitive too: recognize them as echoes, never send them back.
        if (fingerprint == receiver.engine.hashGuard.lastAppliedHash) return ManualSendResult.ECHO
        if (sensitive) return ManualSendResult.SENSITIVE
        if (receiver.engine.state == EngineState.PAUSED) return ManualSendResult.PAUSED
        if (receiver.engine.state != EngineState.CONNECTED) return ManualSendResult.DISCONNECTED
        val scheduled = receiver.engine.scheduler.text
        if (fingerprint == scheduled?.hash || (scheduled == null && fingerprint == ((if (inFlightRevision == remoteRevision) inFlight?.hash else null) ?: lastSentHash)))
            return ManualSendResult.DUPLICATE
        receiver.clearDeferred()
        receiver.engine.hashGuard.observeLocal(text)
        receiver.engine.local(ClipVersion(clock.local(), "phone", fingerprint, ClipMessage(MessageType.TEXT, text)))
        return ManualSendResult.QUEUED
    }
    fun take(): ClipVersion? {
        if (inFlight != null || receiver.engine.state != EngineState.CONNECTED) return null
        return receiver.engine.scheduler.next().also { inFlight = it; inFlightRevision = remoteRevision }
    }
    fun completed(clip: ClipVersion) {
        if (inFlight !== clip) return
        if (inFlightRevision == remoteRevision) lastSentHash = clip.hash
        sent++; inFlight = null
    }
    fun clear() {
        receiver.engine.scheduler.text = null; receiver.engine.scheduler.image = null
        inFlight = null; lastSentHash = null
    }
    /** A genuinely new PC update supersedes a still-queued manual send and its dedup state. */
    fun remoteArrived(text: String): Boolean {
        val fingerprint = hash(text.toByteArray(Charsets.UTF_8))
        if (fingerprint == inFlight?.hash || fingerprint == lastSentHash) return false
        receiver.engine.scheduler.text = null
        remoteRevision++
        lastSentHash = null
        return true
    }
}
