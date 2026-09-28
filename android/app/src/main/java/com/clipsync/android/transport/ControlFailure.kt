package com.clipsync.android.transport

import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Stage-specific user feedback. TLS rejection is not mislabeled as an IP timeout. */
object ControlFailure {
    fun message(stage: String, error: Throwable): String {
        if (error is ConnectionProblem) return error.message ?: "Pairing failed. Check the Windows panel."
        val causes = generateSequence(error) { it.cause }.take(12).toList()
        return when {
            causes.any { it is com.clipsync.android.security.PeerPinMismatchException } ->
                "This PC's identity changed. Use Pair again and compare its code before trusting it."
            causes.any { it is SSLException } || (stage in setOf("offer", "tls") && causes.any { it is EOFException || it is java.net.SocketException }) ->
                "Secure connection rejected or closed. Check Windows. If it shows a saved phone from before reinstalling, choose Forget paired phone, confirm, then Pair new device."
            causes.any { it is ConnectException } -> "PC connection refused. Open ClipSync on that PC and check that its firewall allows private networks."
            causes.any { it is SocketTimeoutException } || stage == "connect" -> "PC did not respond within 5 seconds. Check the IP address and that both devices are on the same Wi-Fi."
            stage == "protocol" -> "Update ClipSync on both devices, then open Pair new device on Windows and retry."
            else -> "Pairing could not finish. Check the Windows panel and share Diagnostics if it happens again."
        }
    }
}
