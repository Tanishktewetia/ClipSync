package com.clipsync.android.service

import android.content.Context
import com.clipsync.android.store.*
import com.clipsync.android.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Service-owned queue; each offered certificate retains its own socket, deadline and code attempts. */
class PairingController(private val context: Context, private val settings: SyncSettings, private val scope: CoroutineScope,
    private val save: (List<PairedDevice>) -> Unit) {
    private data class Waiting(val id: Long, val offer: ControlClient.Offer, val turn: CompletableDeferred<Unit>, val answers: Channel<String?>)
    private val queue = PairingQueue<Waiting> { it.offer.arrived }
    private var sequence = 0L
    private var search: Job? = null
    private val attempts = mutableSetOf<PcEndpoint>()
    private val pendingIdentities = mutableSetOf<String>()
    private val jobs = mutableSetOf<Job>()
    init { SyncRuntime.onPairCode = { id, value ->
        if (queue.firstOrNull()?.id == id && SyncRuntime.state.value.pairing?.busy == false) {
            if (queue.first().answers.trySend(value).isSuccess) SyncRuntime.update { it.copy(pairing = it.pairing?.copy(busy = true)) }
        }
    } }
    fun start(address: String, replacementId: String? = null) {
        if (search?.isActive == true || jobs.any { it.isActive }) return
        SyncRuntime.clearPairingFeedback()
        val revision = SyncRuntime.feedbackRevision
        attempts.clear(); pendingIdentities.clear()
        search = scope.launch {
            SyncRuntime.update { it.copy(searching = true, pairingMessage = null, pairingFailed = false) }
            if (address.isNotBlank()) {
                val endpoint = endpoint(address)
                launchPair(endpoint, replacementId, revision)
            } else {
                var found = false
                var failure: String? = null
                try {
                    PcDiscovery(context, settings).scan { endpoint ->
                        val info = try { withContext(Dispatchers.IO) { ControlClient(context).probe(endpoint) } }
                        catch (e: ConnectionProblem) { withContext(Dispatchers.Main) { failure = e.message }; return@scan }
                        if (info.pairingOpen) withContext(Dispatchers.Main) { found = true; if (pendingIdentities.add(info.fingerprint)) launchPair(endpoint, replacementId, revision) }
                    }
                    if (!found) SyncRuntime.updatePairingFeedback(revision) { it.copy(pairingMessage = failure ?: "No devices found. Make sure 'Pair new device' is open on your PC.", pairingFailed = true) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { SyncRuntime.updatePairingFeedback(revision) { it.copy(pairingMessage = "Search failed. Join the same Wi-Fi and retry.", pairingFailed = true) } }
            }
            if (jobs.none { it.isActive }) SyncRuntime.update { it.copy(searching = false) }
        }
    }
    private fun launchPair(endpoint: PcEndpoint, replacementId: String?, revision: Long) {
        if (!attempts.add(endpoint)) return
        val job = scope.launch {
            try {
                val identity = withContext(Dispatchers.IO) {
                    ControlClient(context).pair(endpoint) { offer, submit -> withContext(Dispatchers.Main) {
                        val pending = Waiting(++sequence, offer, CompletableDeferred(), Channel(1))
                        queue.add(pending); showHead()
                        try {
                            withTimeout((offer.remaining - (android.os.SystemClock.elapsedRealtime() - offer.arrived)).coerceAtLeast(1)) {
                                pending.turn.await()
                                var accepted = false
                                while (!accepted) {
                                    val code = pending.answers.receive() ?: break
                                    SyncRuntime.update { it.copy(pairing = it.pairing?.copy(busy = true)) }
                                    val response = submit(code)
                                    when {
                                        response.startsWith("PAIRED|") -> accepted = true
                                        response.startsWith("WRONG|") -> SyncRuntime.update { it.copy(pairing = it.pairing?.copy(busy = false,
                                            error = "Code doesn't match. ${response.substringAfter('|')} attempts remaining.")) }
                                        response == "PAIR_TIMEOUT" -> throw TimeoutCancellationExceptionCompat()
                                        else -> throw ConnectionProblem("Three incorrect codes. Open Pair new device on the PC to restart.")
                                    }
                                }
                                accepted
                            }
                        } finally { queue.remove(pending); pending.answers.close(); showHead() }
                    } }
                }
                val before = SyncRuntime.state.value.devices
                val existing = before.firstOrNull { it.certFingerprint.equals(identity.fingerprint, true) }
                val merged = DevicePolicy.merge(before, PairedDevice(java.util.UUID.randomUUID().toString(), identity.host, identity.host,
                    identity.fingerprint, "${endpoint.address}:${endpoint.port}"), replacementId)
                save(merged)
                val message = if (existing != null) "Already paired as ${existing.displayName}" else "Paired. Expand the tile and tap Connect when you're ready."
                SyncRuntime.updatePairingFeedback(revision) { it.copy(pairingMessage = message, pairingFailed = false) }
                if (existing != null) android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            } catch (_: TimeoutCancellationException) { SyncRuntime.updatePairingFeedback(revision) { it.copy(pairingMessage = "Pairing timed out. Open Pair new device on the PC and retry.", pairingFailed = true) } }
            catch (_: TimeoutCancellationExceptionCompat) { SyncRuntime.updatePairingFeedback(revision) { it.copy(pairingMessage = "Pairing timed out. Open Pair new device on the PC and retry.") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { SyncRuntime.updatePairingFeedback(revision) { it.copy(pairingMessage = (e as? ConnectionProblem)?.message ?: ControlFailure.message("pairing", e), pairingFailed = true) } }
        }
        jobs.add(job); job.invokeOnCompletion { jobs.remove(job); if (jobs.none { it.isActive } && search?.isActive != true) SyncRuntime.update { it.copy(searching = false) } }
    }
    private fun showHead() {
        val head = queue.firstOrNull()
        SyncRuntime.update { it.copy(pairing = head?.let { p -> if (it.pairing?.id == p.id) it.pairing else PairingPrompt(p.id, p.offer.code, p.offer.host) }, waitingPairs = (queue.size - 1).coerceAtLeast(0)) }
        head?.turn?.complete(Unit)
    }
    fun close() { search?.cancel(); jobs.toList().forEach { it.cancel() }; SyncRuntime.onPairCode = null }
    companion object {
        fun endpoint(address: String): PcEndpoint {
            val parts = address.trim().split(':')
            require(parts.size in 1..2) { "Enter an IPv4 address, optionally followed by :port." }
            val port = if (parts.size == 1) 48653 else parts[1].toIntOrNull()
            require(port != null && port in 1..65535) { "Enter a valid port (1–65535)." }
            return PcEndpoint(com.clipsync.android.security.ManualAddress.parse(parts[0]), port)
        }
    }
    private class TimeoutCancellationExceptionCompat : Exception()
}
