package com.clipsync.android.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.clipsync.android.store.SyncSettings
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import java.net.Inet4Address

/** Discovery hints are untrusted. The TLS client must authenticate every candidate. */
data class PcEndpoint(val address: String, val port: Int = TlsClipboardClient.PORT)
class PcDiscovery(context: Context, private val settings: SyncSettings) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val nsd = context.getSystemService(NsdManager::class.java)

    @Suppress("DEPRECATION")
    suspend fun <T> race(pairing: Boolean, connect: suspend (PcEndpoint) -> T): T = coroutineScope {
        val candidates = Channel<PcEndpoint>(32)
        val results = Channel<Result<T>>(32)
        val seen = mutableSetOf<PcEndpoint>()
        val jobs = mutableListOf<Job>()
        val resolveQueue = Channel<NsdServiceInfo>(16)
        var discoveryStarted = false
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) { discoveryStarted = true }
            override fun onDiscoveryStopped(type: String) { discoveryStarted = false }
            override fun onStartDiscoveryFailed(type: String, code: Int) { discoveryStarted = false }
            override fun onStopDiscoveryFailed(type: String, code: Int) { }
            override fun onServiceLost(info: NsdServiceInfo) { }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.trimEnd('.') == "_clipsync._tcp") resolveQueue.trySend(info)
            }
        }
        val resolver = launch {
            for (info in resolveQueue) {
                withTimeoutOrNull(2000) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(service: NsdServiceInfo, code: Int) { if (continuation.isActive) continuation.resume(Unit) }
                            override fun onServiceResolved(service: NsdServiceInfo) {
                                val ip = service.host
                                if (continuation.isActive && ip is Inet4Address && !ip.isLoopbackAddress && !ip.isAnyLocalAddress && service.port in 1..65535)
                                    candidates.trySend(PcEndpoint(ip.hostAddress!!, service.port))
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        })
                    }
                }
            }
        }
        // The Windows hotspot candidate is used only when the actual Wi-Fi route has this gateway.
        manager.allNetworks.filter { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }.forEach { network ->
            if (manager.getLinkProperties(network)?.routes?.any { it.gateway?.hostAddress == "192.168.137.1" } == true)
                candidates.trySend(PcEndpoint("192.168.137.1"))
        }
        if (settings.address.isNotBlank()) candidates.trySend(PcEndpoint(settings.address))
        if (settings.lastAddress.isNotBlank()) candidates.trySend(PcEndpoint(settings.lastAddress, settings.lastPort))
        try { nsd.discoverServices("_clipsync._tcp.", NsdManager.PROTOCOL_DNS_SD, listener); discoveryStarted = true } catch (_: RuntimeException) { }
        var failure: Throwable? = null
        val producer = launch {
            for (endpoint in candidates) {
                if (seen.size >= 16 || !seen.add(endpoint)) continue
                jobs += launch(Dispatchers.IO) {
                    try { results.send(Result.success(connect(endpoint))) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { results.send(Result.failure(e)) }
                }
            }
        }
        try {
            withTimeout(14000) {
                while (true) {
                    val result = results.receive()
                    if (result.isSuccess) return@withTimeout result.getOrThrow()
                    failure = result.exceptionOrNull()
                }
                @Suppress("UNREACHABLE_CODE") error("No candidate")
            }
        } catch (e: TimeoutCancellationException) {
            throw java.io.IOException("No authenticated PC found on Wi-Fi", failure)
        } finally {
            producer.cancel(); jobs.forEach { it.cancel() }; resolver.cancel()
            candidates.close(); resolveQueue.close(); results.close()
            if (discoveryStarted) runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
