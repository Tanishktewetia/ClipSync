package com.clipsync.core

import java.nio.ByteBuffer

/** CSP1 extension, negotiated by HELLO cs7|<origin>|<replay>. No clipboard is persisted. */
object SessionProtocol {
    const val PREFIX = "cs7|"
    const val MAX_TEXT = FrameCodec.MAX - 266
    fun hello(latest: ClipMessage?, replay: Boolean) = ClipMessage(MessageType.HELLO,
        lamport = latest?.lamport ?: 0, deviceId = "$PREFIX${latest?.deviceId ?: ""}|${if (replay) 1 else 0}")
    fun origin(hello: ClipMessage): String? = hello.deviceId?.takeIf { it.startsWith(PREFIX) }?.split('|')?.takeIf { it.size == 3 }?.get(1)
    fun replay(hello: ClipMessage) = hello.deviceId?.endsWith("|1") == true
    fun newer(a: ClipMessage, clock: Long, origin: String) = a.lamport > clock || (a.lamport == clock && (a.deviceId ?: "") > origin)
    fun encodeState(m: ClipMessage): ByteArray {
        val id = requireNotNull(m.deviceId).toByteArray(Charsets.UTF_8)
        val text = (m.text ?: "").toByteArray(Charsets.UTF_8)
        require(m.lamport in 1 until (Long.MAX_VALUE - 1) && id.size in 1..256 && text.size <= MAX_TEXT)
        return ByteBuffer.allocate(10 + id.size + text.size).putLong(m.lamport).putShort(id.size.toShort()).put(id).put(text).array()
    }
    fun decodeState(p: ByteArray): ClipMessage {
        require(p.size >= 11)
        val b = ByteBuffer.wrap(p); val clock = b.long; val size = b.short.toInt() and 65535
        require(clock in 1 until (Long.MAX_VALUE - 1) && size in 1..256 && size <= b.remaining())
        val id = ByteArray(size).also { b.get(it) }.toString(Charsets.UTF_8)
        val text = ByteArray(b.remaining()).also { b.get(it) }.toString(Charsets.UTF_8)
        require(text.toByteArray().size <= MAX_TEXT)
        return ClipMessage(MessageType.STATE, text = text, lamport = clock, deviceId = id)
    }
}

/** Single memory-only newest slot. Clock is logical, ties use ordinal device ID, not wall time. */
class ReconnectJournal(private val deviceId: String) {
    private var clock = 0L
    @Volatile var latest: ClipMessage? = null; private set
    @Synchronized fun local(text: String): ClipMessage {
        require(text.toByteArray().size <= SessionProtocol.MAX_TEXT)
        check(clock < Long.MAX_VALUE - 1)
        return ClipMessage(MessageType.STATE, text = text, lamport = ++clock, deviceId = deviceId).also { latest = it }
    }
    @Synchronized fun accept(message: ClipMessage): Boolean {
        require(message.lamport in 1 until (Long.MAX_VALUE - 1))
        check(clock < Long.MAX_VALUE - 1)
        clock = maxOf(clock, message.lamport) + 1
        val current = latest
        if (current != null && !SessionProtocol.newer(message, current.lamport, current.deviceId ?: "")) return false
        latest = message
        return true
    }
    @Synchronized fun observe(value: Long) { require(value in 0 until (Long.MAX_VALUE - 1)); check(clock < Long.MAX_VALUE - 1); clock = maxOf(clock, value) + 1 }
    @Synchronized fun forgetIf(message: ClipMessage) { if (latest == message) latest = null }
    @Synchronized fun clear() { latest = null }
}
