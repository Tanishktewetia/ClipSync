package com.clipsync.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class ReceiveSessionTest {
    @Test fun disconnectedDoesNotWrite() {
        var writes = 0; val receiver = ReceiveSession { writes++ }
        assertFalse(receiver.receive("fixture")); assertEquals(0, writes)
    }
    @Test fun receivingSuppressesConsecutiveDuplicatesAndLocalEcho() {
        var writes = 0; val receiver = ReceiveSession { writes++ }; receiver.connected(false)
        assertTrue(receiver.receive("fixture")); assertFalse(receiver.receive("fixture"))
        assertTrue(receiver.engine.hashGuard.observeLocal("fixture")); assertEquals(1, writes)
        receiver.engine.local(ClipVersion(1, "phone", hash("fixture".toByteArray()), ClipMessage(MessageType.TEXT, "fixture")))
        assertNull(receiver.engine.scheduler.next())
    }
    @Test fun repeatedOldTextAfterDifferentTextIsNotLost() {
        val written = mutableListOf<String>(); val receiver = ReceiveSession { written.add(it) }; receiver.connected(false)
        listOf("a", "b", "a").forEach { assertTrue(receiver.receive(it)) }
        assertEquals(listOf("a", "b", "a"), written); assertEquals(1, receiver.engine.seen.size)
    }
    @Test fun fiveRapidCopiesEndWithLatest() {
        var last = ""; val receiver = ReceiveSession { last = it }; receiver.connected(false)
        (1..5).forEach { receiver.receive("fixture-$it") }
        assertEquals("fixture-5", last); assertEquals(5, receiver.received)
    }
    @Test fun pauseDropsUpdatesWithoutReplayingOnResume() {
        var last = ""; val receiver = ReceiveSession { last = it }; receiver.connected(true)
        assertFalse(receiver.receive("paused")); receiver.setPaused(false)
        assertEquals("", last); assertTrue(receiver.receive("fresh")); assertEquals("fresh", last)
    }
    @Test fun failedWriteCanBeRetried() {
        var fail = true; val receiver = ReceiveSession { if (fail) error("test failure") }; receiver.connected(false)
        assertThrows(IllegalStateException::class.java) { receiver.receive("fixture") }
        assertNull(receiver.engine.hashGuard.lastAppliedHash); fail = false
        assertTrue(receiver.receive("fixture"))
    }
    @Test fun manualDifferentCopyResetsEchoGuardWithoutSending() {
        val receiver = ReceiveSession {}; receiver.connected(false); receiver.receive("old")
        assertFalse(receiver.engine.hashGuard.observeLocal("new")); assertTrue(receiver.receive("old"))
        assertNull(receiver.engine.scheduler.next())
    }
    @Test fun emptyAndUnicodeTextSurviveFramingAndReceive() {
        listOf("", "हिन्दी ☕\nline two").forEach { text ->
            val packet = FrameCodec.encode(ClipMessage(MessageType.TEXT, text)); val decoded = FrameReader().push(packet).single()
            var written: String? = null; val receiver = ReceiveSession { written = it }; receiver.connected(false)
            assertTrue(receiver.receive(decoded.text!!)); assertEquals(text, written)
        }
    }
    @Test fun everyFrameSplitIsAccepted() {
        val frame = FrameCodec.encode(ClipMessage(MessageType.TEXT, "fixture ☕"))
        for (split in 0..frame.size) {
            val reader = FrameReader()
            val result = reader.push(frame.copyOfRange(0, split)) + reader.push(frame.copyOfRange(split, frame.size))
            assertEquals("fixture ☕", result.single().text)
        }
    }
    @Test fun invalidLengthsRejectedAtHeaderBeforeAllocation() {
        for (length in listOf(-1, Int.MAX_VALUE, FrameCodec.MAX + 1)) {
            val header = "CSP1".toByteArray() + byteArrayOf(1) + ByteBuffer.allocate(4).putInt(length).array()
            assertThrows(IllegalArgumentException::class.java) { FrameReader().push(header) }
        }
    }
    @Test fun invalidMagicAndTypeRejectedWithoutWaitingForPayload() {
        val frame = FrameCodec.encode(ClipMessage(MessageType.TEXT, "fixture"))
        assertThrows(IllegalArgumentException::class.java) { FrameReader().push(frame.copyOf().also { it[0] = 0 }.copyOf(9)) }
        assertThrows(IllegalArgumentException::class.java) { FrameReader().push(frame.copyOf().also { it[4] = 99 }.copyOf(9)) }
    }
    @Test fun maximumPayloadAndEmptyControlFramesStayBounded() {
        val frame = FrameCodec.encode(ClipMessage(MessageType.TEXT, "x".repeat(FrameCodec.MAX)))
        val reader = FrameReader(); var count = 0
        frame.toList().chunked(8192).forEach { count += reader.push(it.toByteArray()).size }
        assertEquals(1, count)
        assertEquals(2, reader.push(FrameCodec.encode(ClipMessage(MessageType.ACK)) + FrameCodec.encode(ClipMessage(MessageType.PING))).size)
    }
}
