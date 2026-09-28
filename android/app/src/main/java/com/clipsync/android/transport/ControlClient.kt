package com.clipsync.android.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import com.clipsync.android.security.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

data class PcIdentity(val fingerprint: String, val host: String, val pairingOpen: Boolean)

/** No clipboard frames on these ALPN-isolated sockets. Untrusted metadata is only a discovery hint. */
class ControlClient(private val context: Context, private val expectedPin: String? = null) {
    private var stage = "initializing"
    @Volatile private var deadlineExpired = false
    @Volatile var observedFingerprint: String? = null
        private set
    @Volatile private var socket: Socket? = null
    private var closed = false
    @Synchronized private fun own(value: Socket) { if (closed) { value.close(); error("Cancelled") }; socket = value }
    @Synchronized fun close() { closed = true; runCatching { socket?.close() }; socket = null }
    private suspend fun <T> use(endpoint: PcEndpoint, pairing: Boolean, block: suspend (SSLSocket, String, KeyStoreIdentity) -> T): T = coroutineScope {
        val cleanup = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { close() } }
        val deadline = launch(Dispatchers.Default) { delay(5000); deadlineExpired = true; close() }
        try {
            if (Build.VERSION.SDK_INT < 29) throw ConnectionProblem("Secure sync requires Android 10 or newer with TLS 1.3.")
            val manager = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            val wifi = manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                ?: throw ConnectionProblem("Join Wi-Fi before searching for a PC.")
            val identity = KeyStoreIdentity()
            val trust = PinnedTrustManager(expectedPin, pairing || expectedPin == null) { observedFingerprint = it }
            val tls = SSLContext.getInstance("TLS").apply { init(arrayOf(identity), arrayOf(trust), null) }
            val tcp = wifi.socketFactory.createSocket(); own(tcp)
            stage = "connect"
            tcp.connect(InetSocketAddress(endpoint.address, endpoint.port), 4000); tcp.soTimeout = 4000
            val ssl = tls.socketFactory.createSocket(tcp, endpoint.address, endpoint.port, true) as SSLSocket; own(ssl)
            val protocol = if (pairing) "clipsync-pair/1" else "clipsync-probe/1"
            ssl.enabledProtocols = arrayOf("TLSv1.3")
            ssl.sslParameters = ssl.sslParameters.apply { applicationProtocols = arrayOf(protocol) }
            stage = "tls"
            ssl.startHandshake()
            stage = "protocol"
            check(ssl.applicationProtocol == protocol) { "Update the Windows app to Phase 8.5 before pairing." }
            val remote = trust.peerFingerprint ?: error("Missing PC identity")
            // Initial handshake/offer deadline remains active until the first server line arrives.
            stage = "offer"
            val first = PairingProtocol.readLine(ssl.inputStream)
            val arrived = android.os.SystemClock.elapsedRealtime()
            if (pairing) {
                when (first) {
                    "PAIR_CLOSED" -> throw ConnectionProblem("Open Pair new device on the PC to restart pairing.")
                    "PAIR_BUSY" -> throw ConnectionProblem("This PC is already confirming another request. Try again after it finishes.")
                }
                val code = PairingProtocol.validateOffer(first, identity.fingerprint, remote)
                val meta = PairingProtocol.readLine(ssl.inputStream).split('|')
                require(meta.size == 3 && meta[0] == "META")
                offer = Offer(code, decodeHost(meta[1]), meta[2].toLong().coerceIn(1, 120000), arrived)
            } else {
                val fields = first.split('|'); require(fields.size == 3 && fields[0] == "INFO")
                info = PcIdentity(remote, decodeHost(fields[1]), fields[2] == "1")
            }
            deadline.cancel()
            stage = "confirmation"
            block(ssl, remote, identity)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // Never log exception messages from remote peers or clipboard content.
            com.clipsync.android.logging.FileLogger.warn("Control connection failed: stage=$stage type=${e.javaClass.simpleName} deadline=$deadlineExpired")
            val reason = if (deadlineExpired) java.net.SocketTimeoutException() else e
            throw ConnectionProblem(ControlFailure.message(stage, reason), e)
        } finally { deadline.cancel(); cleanup.cancel(); close() }
    }
    data class Offer(val code: String, val host: String, val remaining: Long, val arrived: Long)
    private var offer: Offer? = null
    private var info: PcIdentity? = null
    suspend fun probe(endpoint: PcEndpoint): PcIdentity = use(endpoint, false) { _, _, _ -> checkNotNull(info) }
    suspend fun pair(endpoint: PcEndpoint, confirm: suspend (Offer, suspend (String) -> String) -> Boolean): PcIdentity = use(endpoint, true) { ssl, remote, _ ->
        val prompt = checkNotNull(offer)
        val expires = launchDeadline(prompt.remaining)
        try {
            ssl.soTimeout = 5000
            val accepted = confirm(prompt) { code ->
                require(code.matches(Regex("[0-9]{6}")))
                withContext(Dispatchers.IO) {
                    ssl.outputStream.write("CONFIRM|$code\n".toByteArray(Charsets.US_ASCII)); ssl.outputStream.flush()
                    PairingProtocol.readLine(ssl.inputStream)
                }
            }
            if (!accepted) throw ConnectionProblem("Pairing cancelled. Open Pair new device on the PC to restart.")
            PcIdentity(remote, prompt.host, false)
        } finally { expires.cancel() }
    }
    private fun launchDeadline(duration: Long) = CoroutineScope(Dispatchers.IO).launch { delay(duration); close() }
    private fun decodeHost(value: String) = Base64.decode(value, Base64.NO_WRAP).toString(Charsets.UTF_8).take(80)
}
