package com.clipsync.android.service

import com.clipsync.android.transport.ConnectionStage
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

/** Recovery only to the configured, pinned address; never retry provisional pairing/trust errors. */
object RecoveryPolicy {
    const val RETRY_MILLIS = 5000L
    fun enabled(serviceEnabled: Boolean, paired: Boolean, address: String) = serviceEnabled && paired && address.isNotBlank()
    fun retry(stage: ConnectionStage, error: Throwable, pairing: Boolean): Boolean {
        if (pairing || error is CancellationException || error is SSLException) return false
        return stage == ConnectionStage.WIFI_ROUTE ||
            (error is IOException && stage in setOf(ConnectionStage.TCP_CONNECT, ConnectionStage.TLS_HANDSHAKE,
                ConnectionStage.PAIRING_OFFER, ConnectionStage.RECEIVING))
    }
}
