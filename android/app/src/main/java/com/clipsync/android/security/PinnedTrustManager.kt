package com.clipsync.android.security

import com.clipsync.core.hash
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class PeerPinMismatchException : CertificateException("PC certificate is not pinned")

/** A provisional TLS connection is allowed ONLY for an explicit pairing attempt.
 * Clipboard frames remain gated until a human confirms the fingerprint-derived code.
 * Certificate expiry/hostname/CA are not trust anchors: the exact certificate pin is.
 */
class PinnedTrustManager(private val pinned: String?, private val pairing: Boolean) : X509TrustManager {
    var peerFingerprint: String? = null
        private set
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificate = chain?.firstOrNull() ?: throw CertificateException("Missing PC certificate")
        val actual = hash(certificate.encoded)
        if (!pairing && (pinned == null || actual != pinned)) throw PeerPinMismatchException()
        peerFingerprint = actual
    }
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Not a TLS server")
    }
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
