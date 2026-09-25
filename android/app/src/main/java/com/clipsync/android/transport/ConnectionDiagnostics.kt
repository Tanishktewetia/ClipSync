package com.clipsync.android.transport

import com.clipsync.android.security.PeerPinMismatchException
import java.io.EOFException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Local enum + bounded exception class chain only. Never emit arbitrary exception
 * messages, certificate dumps, IPs, pairing codes or clipboard payloads into logs.
 */
enum class ConnectionStage { IDLE, WIFI_ROUTE, PIN_STORAGE, KEYSTORE_IDENTITY, TCP_CONNECT, TLS_HANDSHAKE, PAIRING_OFFER, PAIRING_CONFIRM, PIN_SAVE, RECEIVING }
object ConnectionDiagnostics {
    fun causes(error: Throwable): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = error
        while (current != null && chain.size < 6 && chain.none { it === current }) {
            chain += current
            current = current.cause
        }
        return chain
    }
    fun summary(stage: ConnectionStage, error: Throwable): String =
        "stage=" + stage.name + " causes=" + causes(error).joinToString(" > ") {
            it.javaClass.simpleName.replace(Regex("[^A-Za-z0-9_]"), "").take(64)
        }
    fun userMessage(stage: ConnectionStage, error: Throwable): String = when {
        causes(error).any { it is PeerPinMismatchException } ->
            "The PC certificate does not match the saved pairing. Check the PC, then use Pair again and compare all six digits."
        stage == ConnectionStage.KEYSTORE_IDENTITY ->
            "Android could not use the phone's secure signing key. Share Diagnostics; no clipboard text was exchanged."
        error is SSLException && stage == ConnectionStage.RECEIVING ->
            "The secure connection was interrupted. Tap Reconnect when the PC is available; if it repeats, share Diagnostics."
        error is SSLException ->
            "Secure connection setup failed before pairing. Check that the updated Windows app is running, open Pair new device, and retry. If it repeats, share Diagnostics."
        error is SocketTimeoutException ->
            "PC did not respond. Check its Wi-Fi IP and allow ClipSync on Private networks."
        error is EOFException -> "PC disconnected. Tap Reconnect when it is available."
        else -> "Could not connect or apply clipboard text. Check Wi-Fi, the PC app, and Diagnostics, then reconnect."
    }
}
