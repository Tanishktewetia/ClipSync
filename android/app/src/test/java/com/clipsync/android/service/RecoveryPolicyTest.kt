package com.clipsync.android.service

import com.clipsync.android.transport.ConnectionStage
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLHandshakeException

class RecoveryPolicyTest {
    @Test fun enabledNeedsPersistedIntentAndPeerButNotAnAddress() {
        assertTrue(RecoveryPolicy.enabled(true, true, "192.168.1.2"))
        assertFalse(RecoveryPolicy.enabled(false, true, "192.168.1.2"))
        assertFalse(RecoveryPolicy.enabled(true, false, "192.168.1.2"))
        assertTrue(RecoveryPolicy.enabled(true, true, ""))
    }
    @Test fun backoffCapsAtThirtySeconds() { assertEquals(listOf(1000L,2000L,5000L,15000L,30000L,30000L), (0..5).map(RecoveryPolicy::delayMillis)) }
    @Test fun wifiUnavailableAtBootCanRecoverLater() { assertTrue(RecoveryPolicy.retry(ConnectionStage.WIFI_ROUTE, Exception(), false)) }
    @Test fun droppedSocketAndConnectTimeoutAreRecoverable() {
        assertTrue(RecoveryPolicy.retry(ConnectionStage.RECEIVING, SocketException(), false))
        assertTrue(RecoveryPolicy.retry(ConnectionStage.TCP_CONNECT, SocketTimeoutException(), false))
    }
    @Test fun pinTlsAndKeyFailuresNeverLoopAutomatically() {
        assertFalse(RecoveryPolicy.retry(ConnectionStage.TLS_HANDSHAKE, SSLHandshakeException("fixture"), false))
        assertFalse(RecoveryPolicy.retry(ConnectionStage.PIN_STORAGE, IOException(), false))
        assertFalse(RecoveryPolicy.retry(ConnectionStage.KEYSTORE_IDENTITY, IOException(), false))
    }
    @Test fun provisionalPairingIsNeverAutomaticallyRetried() {
        assertFalse(RecoveryPolicy.retry(ConnectionStage.TCP_CONNECT, SocketException(), true))
    }
    @Test fun cancelledAttemptsNeverRestartThemselves() {
        assertFalse(RecoveryPolicy.retry(ConnectionStage.WIFI_ROUTE, CancellationException(), false))
    }
    @Test fun malformedFramesAreNotRetriedAsNetworkLoss() {
        assertFalse(RecoveryPolicy.retry(ConnectionStage.RECEIVING, IllegalArgumentException(), false))
    }
}
