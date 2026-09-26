package com.clipsync.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class SessionProtocolTest {
    @Test fun versionedUnicodeRoundTripAndFragmentation() {
        val original = ClipMessage(MessageType.STATE, text = "fixture ☕ हिन्दी", lamport = 42, deviceId = "phone")
        val reader = FrameReader(); val messages = mutableListOf<ClipMessage>()
        FrameCodec.encode(original).forEach { messages += reader.push(byteArrayOf(it)) }
        assertEquals(listOf(original), messages)
    }
    @Test fun stateWireVectorMatchesWindows() {
        val bytes = FrameCodec.encode(ClipMessage(MessageType.STATE, text = "hi", lamport = 7, deviceId = "a"))
        assertEquals("43535031060000000d00000000000000070001616869", bytes.joinToString("") { "%02x".format(it) })
    }
    @Test fun newestSlotAndDeterministicTie() {
        val a = ReconnectJournal("a"); val b = ReconnectJournal("b")
        val first = a.local("first"); val second = b.local("second")
        assertTrue(a.accept(second)); assertFalse(b.accept(first)); assertEquals(second, a.latest)
        val third = a.local("third"); assertEquals(3L, third.lamport); assertTrue(b.accept(third))
    }
    @Test fun helloClockObservedWithoutReplay() { val j = ReconnectJournal("a"); j.observe(100); assertEquals(102L, j.local("fixture").lamport) }
    @Test fun onlyLatestRetainedAndClearPreservesClock() {
        val journal = ReconnectJournal("a"); repeat(200) { journal.local("fixture-$it") }
        assertEquals(200L, journal.latest?.lamport); journal.clear(); assertNull(journal.latest)
        assertEquals(201L, journal.local("after pause").lamport)
    }
    @Test fun helloAdvertisesReplayAndOriginWithoutText() {
        val latest = ReconnectJournal("a").local("not in hello")
        val hello = FrameCodec.decode(FrameCodec.encode(SessionProtocol.hello(latest, false)))
        assertEquals("a", SessionProtocol.origin(hello)); assertFalse(SessionProtocol.replay(hello)); assertNull(hello.text)
        assertNull(SessionProtocol.origin(ClipMessage(MessageType.HELLO, deviceId = "legacy")))
    }
    @Test fun pingPongRoundTrip() {
        for (type in listOf(MessageType.PING, MessageType.PONG)) assertEquals(type, FrameCodec.decode(FrameCodec.encode(ClipMessage(type))).type)
    }
    @Test fun malformedStateAndHelloAreRejected() {
        for (payload in listOf(byteArrayOf(), ByteArray(10), ByteBuffer.allocate(11).putLong(-1).putShort(1).put(97).array())) {
            val frame = "CSP1".toByteArray() + byteArrayOf(6) + ByteBuffer.allocate(4).putInt(payload.size).array() + payload
            assertThrows(IllegalArgumentException::class.java) { FrameCodec.decode(frame) }
        }
        val hello = "CSP1".toByteArray() + byteArrayOf(2) + ByteBuffer.allocate(4).putInt(0).array()
        assertThrows(IllegalArgumentException::class.java) { FrameCodec.decode(hello) }
    }
    @Test fun payloadLimitAndClockOverflowRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReconnectJournal("a").local("x".repeat(FrameCodec.MAX)) }
        assertThrows(IllegalArgumentException::class.java) { SessionProtocol.encodeState(ClipMessage(MessageType.STATE, text = "x", lamport = Long.MAX_VALUE, deviceId = "a")) }
    }
}
