package com.clipsync.android.transport

import android.content.Context
import com.clipsync.android.logging.FileLogger
import android.net.ConnectivityManager
import android.net.Network
import com.clipsync.core.FrameCodec
import com.clipsync.core.ClipMessage
import android.net.NetworkCapabilities
import com.clipsync.android.security.KeyStoreIdentity
import com.clipsync.android.security.ManualAddress
import com.clipsync.android.security.PairingProtocol
import com.clipsync.android.security.PinnedTrustManager
import com.clipsync.android.store.SyncSettings
import com.clipsync.core.FrameReader
import com.clipsync.core.MessageType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class ConnectionProblem(message: String) : Exception(message)

/** One Wi-Fi-bound connection. Its service owns lifecycle recovery; no discovery here. */
class TlsClipboardClient(private val context: Context, private val settings: SyncSettings) {
    @Volatile var stage = ConnectionStage.IDLE
        private set
    private fun stage(next: ConnectionStage) {
        stage = next
        FileLogger.info("Connection stage: "+next.name)
    }
    @Volatile var network: Network? = null
        private set
    @Volatile private var readySocket: SSLSocket? = null
    private val writeLock = Any()
    private var socket: Socket? = null
    /** Serialized writes; caller owns a deadline that closes this connection on a stalled write. */
    fun sendText(text: String) {
        val frame = FrameCodec.encode(ClipMessage(MessageType.TEXT, text))
        synchronized(writeLock) {
            val active = readySocket ?: throw EOFException("Connection not ready")
            active.outputStream.write(frame)
            active.outputStream.flush()
        }
    }
    private var closed = false
    @Synchronized private fun own(next: Socket) {
        if (closed) { next.close(); throw EOFException("Connection cancelled") }
        socket = next
    }
    @Synchronized fun close() { closed = true; readySocket = null; runCatching { socket?.close() }; socket = null }

    suspend fun run(address: String, pairing: Boolean, confirm: suspend (String) -> Boolean,
                    connected: suspend () -> Unit, receive: suspend (String) -> Unit) {
        val ip = ManualAddress.parse(address)
        stage(ConnectionStage.WIFI_ROUTE)
        val manager = context.getSystemService(ConnectivityManager::class.java)
        // One-shot Wi-Fi route selection only. The service handles saved-IP lifecycle recovery.
        @Suppress("DEPRECATION")
        val wifi = manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: throw ConnectionProblem("Join the same Wi-Fi or PC hotspot, then tap Reconnect.")
        network = wifi
        stage(ConnectionStage.PIN_STORAGE)
        val pin = settings.readPin()
        if (!pairing && pin == null) throw ConnectionProblem("Pair this phone with your PC first.")
        stage(ConnectionStage.KEYSTORE_IDENTITY)
        val identity = KeyStoreIdentity()
        val trust = PinnedTrustManager(pin, pairing)
        val tls = SSLContext.getInstance("TLS").apply { init(arrayOf(identity), arrayOf(trust), null) }
        val tcp = wifi.socketFactory.createSocket()
        own(tcp)
        try {
            stage(ConnectionStage.TCP_CONNECT)
            tcp.tcpNoDelay = true
            tcp.connect(InetSocketAddress(ip, PORT), 8000)
            tcp.soTimeout = 10000
            val secure = tls.socketFactory.createSocket(tcp, ip, PORT, true) as SSLSocket
            own(secure)
            if (!secure.supportedProtocols.contains("TLSv1.3")) {
                throw ConnectionProblem("Secure sync requires Android 10 or newer with TLS 1.3. No insecure fallback is used.")
            }
            secure.enabledProtocols = arrayOf("TLSv1.3")
            secure.useClientMode = true
            stage(ConnectionStage.TLS_HANDSHAKE)
            secure.startHandshake()
            currentCoroutineContext().ensureActive()
            val remote = trust.peerFingerprint ?: throw ConnectionProblem("PC did not provide an identity.")
            val input = secure.inputStream
            stage(ConnectionStage.PAIRING_OFFER)
            val response = PairingProtocol.readLine(input)
            if (response.startsWith("PAIR|")) {
                if (!pairing) throw ConnectionProblem("On the PC choose Pair new device, then pair again here.")
                val code = PairingProtocol.validateOffer(response, identity.fingerprint, remote)
                stage(ConnectionStage.PAIRING_CONFIRM)
                if (!confirm(code)) throw ConnectionProblem("Pairing cancelled. Neither clipboard was shared.")
                currentCoroutineContext().ensureActive()
                secure.outputStream.write("CONFIRM|$code\n".toByteArray(Charsets.US_ASCII))
                secure.outputStream.flush()
                if (PairingProtocol.readLine(input) != "READY") throw ConnectionProblem("Pairing was rejected or expired. Start pairing again on both devices.")
            } else if (response == "READY") {
                if (pairing && remote != pin) {
                    // The PC may already pin this phone after an interrupted previous pairing.
                    stage(ConnectionStage.PAIRING_CONFIRM)
                    if (!confirm(PairingProtocol.code(identity.fingerprint, remote))) throw ConnectionProblem("Pairing cancelled.")
                }
            } else {
                throw ConnectionProblem("On the PC choose Pair new device, then try pairing again.")
            }
            currentCoroutineContext().ensureActive()
            if (pairing) { stage(ConnectionStage.PIN_SAVE); settings.pin(remote) }
            secure.soTimeout = 0
            readySocket = secure
            stage(ConnectionStage.RECEIVING)
            connected()
            val frames = FrameReader()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) throw EOFException("PC disconnected")
                for (frame in frames.push(buffer.copyOf(count))) {
                    if (frame.type == MessageType.TEXT) receive(frame.text ?: "")
                    // Manual sends use the same authenticated socket; images/discovery remain out of scope.
                }
            }
        } finally { close() }
    }
    companion object { const val PORT = 48653 }
}
