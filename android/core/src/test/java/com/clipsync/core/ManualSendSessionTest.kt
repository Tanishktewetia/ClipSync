package com.clipsync.core

import org.junit.Assert.*
import org.junit.Test

class ManualSendSessionTest {
    private class Fixture {
        val writes = mutableListOf<String>()
        val receive = ReceiveSession { writes += it }.apply { connected(false) }
        val send = ManualSendSession(receive)
        fun sent(text: String) { assertEquals(ManualSendResult.QUEUED, send.offer(text, false)); send.completed(send.take()!!) }
    }
    @Test fun manualReadQueuesRealCoreTextFrame() {
        val f = Fixture(); assertEquals(ManualSendResult.QUEUED, f.send.offer("phone fixture", false))
        val clip = f.send.take()!!
        assertEquals("phone fixture", FrameCodec.decode(FrameCodec.encode(clip.message)).text)
        assertEquals(1L, clip.lamport); assertEquals(0, f.send.sent)
        f.send.completed(clip); assertEquals(1, f.send.sent)
    }
    @Test fun duplicateTapWhileQueuedOrSendingOrSentProducesOneSend() {
        val f = Fixture(); f.send.offer("a", false)
        assertEquals(ManualSendResult.DUPLICATE, f.send.offer("a", false))
        val clip = f.send.take()!!; assertNull(f.send.take())
        assertEquals(ManualSendResult.DUPLICATE, f.send.offer("a", false))
        f.send.completed(clip)
        assertEquals(ManualSendResult.DUPLICATE, f.send.offer("a", false)); assertNull(f.send.take())
    }
    @Test fun sensitiveExternalClipNeverEntersScheduler() {
        val f = Fixture(); assertEquals(ManualSendResult.SENSITIVE, f.send.offer("secret fixture", true))
        assertNull(f.send.take()); assertEquals(0, f.send.sent)
    }
    @Test fun ownSensitiveWriteIsRecognizedAsEcho() {
        val f = Fixture(); f.receive.receive("remote fixture")
        assertEquals(ManualSendResult.ECHO, f.send.offer("remote fixture", true)); assertNull(f.send.take())
    }
    @Test fun pausedReadDoesNotQueue() {
        val f = Fixture(); f.receive.setPaused(true)
        assertEquals(ManualSendResult.PAUSED, f.send.offer("a", false)); assertNull(f.send.take())
    }
    @Test fun disconnectedReadDoesNotQueueForSurpriseLaterSend() {
        val f = Fixture(); f.receive.disconnect()
        assertEquals(ManualSendResult.DISCONNECTED, f.send.offer("a", false)); f.receive.connected(false)
        assertNull(f.send.take())
    }
    @Test fun emptyAndOversizeTextDoNotQueue() {
        val f = Fixture(); assertEquals(ManualSendResult.EMPTY, f.send.offer("", false))
        assertEquals(ManualSendResult.TOO_LARGE, f.send.offer("☕".repeat(FrameCodec.MAX / 2), false))
        assertNull(f.send.take())
    }
    @Test fun rapidManualRequestsKeepLatestOnly() {
        val f = Fixture(); (1..5).forEach { f.send.offer("fixture-$it", false) }
        assertEquals("fixture-5", f.send.take()!!.message.text)
    }
    @Test fun inflightSendAllowsOnlyOneLatestSuccessor() {
        val f = Fixture(); f.send.offer("a", false); val first = f.send.take()!!
        f.send.offer("b", false); f.send.offer("c", false)
        assertNull(f.send.take()); f.send.completed(first)
        assertEquals("c", f.send.take()!!.message.text)
    }
    @Test fun differentCopyThenSameTextCanBeSentAgain() {
        val f = Fixture(); f.sent("a"); f.sent("b"); f.sent("a"); assertEquals(3, f.send.sent)
    }
    @Test fun failureClearsInFlightWithoutPoisoningRetry() {
        val f = Fixture(); f.send.offer("a", false); val old = f.send.take()!!; f.send.clear()
        f.send.completed(old); assertEquals(0, f.send.sent)
        assertEquals(ManualSendResult.QUEUED, f.send.offer("a", false))
    }
    @Test fun outgoingEchoDoesNotWriteBackToPhone() {
        val f = Fixture(); f.sent("a")
        assertFalse(f.send.remoteArrived("a")); assertTrue(f.writes.isEmpty())
    }
    @Test fun newRemoteClipCancelsQueuedLocalAndResetsSendDedup() {
        val f = Fixture(); f.sent("a"); f.send.offer("pending", false)
        assertTrue(f.send.remoteArrived("remote")); f.receive.receive("remote")
        assertNull(f.send.take()); assertEquals(ManualSendResult.QUEUED, f.send.offer("a", false))
    }
    @Test fun remoteDuringInflightDoesNotPoisonLaterLocalCopy() {
        val f = Fixture(); f.send.offer("a", false); val first = f.send.take()!!
        assertTrue(f.send.remoteArrived("remote")); f.receive.receive("remote"); f.send.completed(first)
        assertEquals(ManualSendResult.QUEUED, f.send.offer("a", false))
    }
    @Test fun pauseClearPreventsAnAlreadyQueuedSendFromLeakingAfterResume() {
        val f = Fixture(); f.send.offer("a", false); f.receive.setPaused(true); f.send.clear()
        f.receive.setPaused(false); assertNull(f.send.take())
    }
    @Test fun successfulManualReadSupersedesDeferredRemoteClip() {
        val f = Fixture(); f.receive.receive("remote", locked = true)
        assertTrue(f.receive.hasDeferred); f.send.offer("new local", false)
        assertFalse(f.receive.hasDeferred)
    }
}
