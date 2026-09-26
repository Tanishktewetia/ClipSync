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
import kotlinx.coroutines.*
import com.clipsync.core.SessionProtocol
import com.clipsync.core.ReconnectJournal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class ConnectionProblem(message: String) : Exception(message)

/** One Wi-Fi-bound authenticated connection. Discovery never changes trust. */
class TlsClipboardClient(private val context: Context, private val settings: SyncSettings, private val journal: ReconnectJournal = ReconnectJournal(settings.deviceId)) {
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
        val message = journal.local(text)
        val frame = FrameCodec.encode(if (modern) message else ClipMessage(MessageType.TEXT, text))
        synchronized(writeLock) {
            val active = readySocket ?: throw EOFException("Connection not ready")
            active.outputStream.write(frame)
            active.outputStream.flush()
        }
    }
    @Volatile private var modern = false
    @Volatile private var lastPongAt = 0L
    private var heartbeatWatchdog: Job? = null
    private fun write(message: ClipMessage) = synchronized(writeLock) {
        val active = readySocket ?: throw EOFException("Connection not ready")
        active.outputStream.write(FrameCodec.encode(message)); active.outputStream.flush()
    }
    suspend fun probe(endpoint: PcEndpoint, pairing: Boolean): PcEndpoint = coroutineScope {
        val cleanup = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { close() } }
        try {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            val wifi = manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                ?: throw java.io.IOException("Wi-Fi unavailable")
            val identity = KeyStoreIdentity()
            val tls = SSLContext.getInstance("TLS").apply { init(arrayOf(identity), arrayOf(PinnedTrustManager(settings.readPin(), pairing)), null) }
            val tcp = wifi.socketFactory.createSocket(); own(tcp)
            tcp.connect(InetSocketAddress(endpoint.address, endpoint.port), 2500); tcp.soTimeout = 2500
            val ssl = tls.socketFactory.createSocket(tcp, endpoint.address, endpoint.port, true) as SSLSocket; own(ssl)
            ssl.enabledProtocols = arrayOf("TLSv1.3"); ssl.startHandshake()
            val offer = PairingProtocol.readLine(ssl.inputStream)
            if (offer != "READY" && !offer.startsWith("PAIR|")) throw java.io.IOException("Not ClipSync")
            endpoint
        } finally { cleanup.cancel(); close() }
    }
    private var closed = false
    @Synchronized private fun own(next: Socket) {
        if (closed) { next.close(); throw EOFException("Connection cancelled") }
        socket = next
    }
    @Synchronized fun close() { heartbeatWatchdog?.cancel(); closed = true; readySocket = null; runCatching { socket?.close() }; socket = null }

    suspend fun run(address: String, pairing: Boolean, port: Int = PORT, confirm: suspend (String) -> Boolean,
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
            tcp.connect(InetSocketAddress(ip, port), 8000)
            tcp.soTimeout = 10000
            val secure = tls.socketFactory.createSocket(tcp, ip, port, true) as SSLSocket
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
            secure.soTimeout = 1000
            readySocket = secure
            stage(ConnectionStage.RECEIVING)
            connected()
            write(SessionProtocol.hello(journal.latest, settings.replayOnConnect && !settings.paused))
            lastPongAt = android.os.SystemClock.elapsedRealtime()
            var lastPing = lastPongAt
            heartbeatWatchdog = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.Default) {
                while (isActive) {
                    delay(1000)
                    if (modern && android.os.SystemClock.elapsedRealtime() - lastPongAt >= 30000) {
                        FileLogger.warn("Two heartbeat replies missed; closing stale connection")
                        close(); return@launch
                    }
                }
            }
            val frames = FrameReader()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val now = android.os.SystemClock.elapsedRealtime()
                if (modern && now - lastPongAt >= 30000) throw java.net.SocketTimeoutException("Two heartbeats missed")
                if (modern && now - lastPing >= 15000) { write(ClipMessage(MessageType.PING)); lastPing = now }
                val count = try { input.read(buffer) } catch (_: java.net.SocketTimeoutException) { continue }
                if (count < 0) throw EOFException("PC disconnected")
                for (frame in frames.push(buffer.copyOf(count))) {
                    when (frame.type) {
                        MessageType.HELLO -> {
                            val origin = SessionProtocol.origin(frame)
                            if (origin != null && !modern) {
                                journal.observe(frame.lamport)
                                modern = true; lastPongAt = android.os.SystemClock.elapsedRealtime()
                                journal.latest?.let { if (!settings.paused && SessionProtocol.replay(frame) && SessionProtocol.newer(it, frame.lamport, origin)) write(it) }
                            }
                        }
                        MessageType.PING -> if (modern) write(ClipMessage(MessageType.PONG))
                        MessageType.PONG -> lastPongAt = android.os.SystemClock.elapsedRealtime()
                        MessageType.STATE -> if (modern && !settings.paused && journal.accept(frame)) {
                            try { receive(frame.text ?: "") }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                journal.forgetIf(frame)
                                throw java.io.IOException("Clipboard apply failed; retry newest on reconnect", e)
                            }
                        }
                        MessageType.TEXT -> if (!settings.paused) { journal.clear(); receive(frame.text ?: "") }
                        else -> Unit
                    }
                }
            }
        } finally { close() }
    }
    companion object { const val PORT = 48653 }
}
