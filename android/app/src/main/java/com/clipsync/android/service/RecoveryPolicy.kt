package com.clipsync.android.service

import com.clipsync.android.transport.ConnectionStage
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

/** Recovery requires enabled intent and pinned trust, not a manually entered address. */
object RecoveryPolicy {
    fun delayMillis(attempt: Int) = longArrayOf(1000, 2000, 5000, 15000, 30000)[attempt.coerceIn(0, 4)]
    fun enabled(serviceEnabled: Boolean, paired: Boolean, address: String) = serviceEnabled && paired
    fun retry(stage: ConnectionStage, error: Throwable, pairing: Boolean): Boolean {
        if (pairing || error is CancellationException || error is SSLException) return false
        return stage == ConnectionStage.WIFI_ROUTE ||
            (error is IOException && stage in setOf(ConnectionStage.TCP_CONNECT, ConnectionStage.TLS_HANDSHAKE,
                ConnectionStage.PAIRING_OFFER, ConnectionStage.RECEIVING))
    }
}
