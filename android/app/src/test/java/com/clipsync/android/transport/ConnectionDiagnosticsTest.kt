package com.clipsync.android.transport

import com.clipsync.android.security.PeerPinMismatchException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class ConnectionDiagnosticsTest {
    @Test fun diagnosticShowsStageAndCauseClassesWithoutMessages() {
        val exception = SSLHandshakeException("secret TLS payload").apply { initCause(IOException("private fixture")) }
        val summary = ConnectionDiagnostics.summary(ConnectionStage.TLS_HANDSHAKE, exception)
        assertEquals("stage=TLS_HANDSHAKE causes=SSLHandshakeException > IOException", summary)
        assertFalse(summary.contains("secret")); assertFalse(summary.contains("private"))
    }
    @Test fun genericTlsFailureDoesNotFalselyClaimPeerPinMismatch() {
        val message = ConnectionDiagnostics.userMessage(ConnectionStage.TLS_HANDSHAKE, SSLHandshakeException("fixture"))
        assertTrue(message.contains("Secure connection setup failed"))
        assertFalse(message.contains("does not match"))
    }
    @Test fun establishedTlsFailureIsNotReportedAsInitialPairingFailure() {
        val message = ConnectionDiagnostics.userMessage(ConnectionStage.RECEIVING, SSLHandshakeException("fixture"))
        assertTrue(message.contains("interrupted"))
        assertFalse(message.contains("before pairing"))
    }
    @Test fun actualPinMismatchHasSpecificRecoveryAdvice() {
        val exception = SSLHandshakeException("fixture").apply { initCause(PeerPinMismatchException()) }
        assertTrue(ConnectionDiagnostics.userMessage(ConnectionStage.TLS_HANDSHAKE, exception).contains("does not match"))
    }
    @Test fun localKeyFailureIsDistinguishedFromPcFailure() {
        val message = ConnectionDiagnostics.userMessage(ConnectionStage.KEYSTORE_IDENTITY, IllegalStateException("fixture"))
        assertTrue(message.contains("phone's secure signing key"))
    }
    @Test fun longCauseChainsStayBounded() {
        var exception: Throwable = IOException("fixture")
        repeat(20) { exception = IOException("fixture", exception) }
        assertEquals(6, ConnectionDiagnostics.causes(exception).size)
    }
    @Test fun cyclicCausesCannotLoop() {
        val first = IOException("one"); val second = IOException("two")
        first.initCause(second); second.initCause(first)
        assertEquals(2, ConnectionDiagnostics.causes(first).size)
    }
}
