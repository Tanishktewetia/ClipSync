package com.clipsync.core

import org.junit.Assert.*
import org.junit.Test

class LockedReceiveTest {
    @Test fun lockedReceiveWaitsWithoutWritingOrMarkingApplied() {
        val written = mutableListOf<String>(); val r = ReceiveSession { written += it }.apply { connected(false) }
        assertFalse(r.receive("fixture", locked = true)); assertTrue(r.hasDeferred)
        assertNull(r.engine.hashGuard.lastAppliedHash); assertTrue(written.isEmpty())
        assertTrue(r.flushAfterUnlock()); assertEquals(listOf("fixture"), written)
        assertFalse(r.hasDeferred); assertFalse(r.flushAfterUnlock())
    }
    @Test fun onlyLatestOfManyLockedCopiesIsRetained() {
        var last = ""; val r = ReceiveSession { last = it }.apply { connected(false) }
        (1..100).forEach { r.receive("fixture-$it", locked = true) }
        r.flushAfterUnlock(); assertEquals("fixture-100", last); assertEquals(1, r.received)
    }
    @Test fun pausedCopiesAreNeverDeferredOrReplayed() {
        val r = ReceiveSession { fail("must not write") }.apply { connected(true) }
        assertFalse(r.receive("fixture", locked = true)); assertFalse(r.hasDeferred)
    }
    @Test fun pauseClearsExistingDeferredClip() {
        val r = ReceiveSession { fail("must not write") }.apply { connected(false) }
        r.receive("fixture", locked = true); r.setPaused(true); r.setPaused(false)
        assertFalse(r.hasDeferred); assertFalse(r.flushAfterUnlock())
    }
    @Test fun knownRemotePendingSurvivesDisconnectUntilPinnedReconnect() {
        var writes = 0; val r = ReceiveSession { writes++ }.apply { connected(false) }
        r.receive("fixture", locked = true); r.disconnect(); assertFalse(r.flushAfterUnlock())
        r.connected(false); assertTrue(r.flushAfterUnlock()); assertEquals(1, writes)
    }
    @Test fun explicitStopOrRePairCanEraseDeferredText() {
        val r = ReceiveSession { fail("must not write") }.apply { connected(false) }
        r.receive("fixture", locked = true); r.clearDeferred(); assertFalse(r.flushAfterUnlock())
    }
    @Test fun failedUnlockWriteRetainsOneLatestClipForRetry() {
        var failWrite = true
        val r = ReceiveSession { if (failWrite) error("fixture failure") }.apply { connected(false) }
        r.receive("fixture", locked = true)
        assertThrows(IllegalStateException::class.java) { r.flushAfterUnlock() }
        assertTrue(r.hasDeferred); assertNull(r.engine.hashGuard.lastAppliedHash)
        failWrite = false; assertTrue(r.flushAfterUnlock()); assertFalse(r.hasDeferred)
    }
    @Test fun freshUnlockedCopySupersedesDeferredCopy() {
        var last = ""; val r = ReceiveSession { last = it }.apply { connected(false) }
        r.receive("old", locked = true); r.receive("fresh"); assertFalse(r.flushAfterUnlock()); assertEquals("fresh", last)
    }
}
