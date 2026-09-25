package com.clipsync.android.security

import com.clipsync.core.hash
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.crypto.KeyGenerator

class PairingProtocolTest {
    private val a = "A".repeat(64)
    private val b = "B".repeat(64)
    @Test fun codeMatchesWindowsLittleEndianVectorAndIsOrderIndependent() {
        assertEquals("830564", PairingProtocol.code(a, b))
        assertEquals(PairingProtocol.code(a, b), PairingProtocol.code(b.lowercase(), a))
    }
    @Test fun serverOfferMustMatchTlsIdentityAndLocallyDerivedCode() {
        val code = PairingProtocol.code(a, b)
        assertEquals(code, PairingProtocol.validateOffer("PAIR|$b|$code", a, b))
        assertThrows(IllegalArgumentException::class.java) { PairingProtocol.validateOffer("PAIR|$a|$code", a, b) }
        assertThrows(IllegalArgumentException::class.java) { PairingProtocol.validateOffer("PAIR|$b|000000", a, b) }
    }
    @Test fun malformedCodesFingerprintsAndOffersFailClosed() {
        for (bad in listOf("", "A".repeat(63), "Z".repeat(64)))
            assertThrows(IllegalArgumentException::class.java) { PairingProtocol.fingerprint(bad) }
        for (bad in listOf("PAIR", "PAIR|$b", "PAIR|$b|123456|extra"))
            assertThrows(IllegalArgumentException::class.java) { PairingProtocol.validateOffer(bad, a, b) }
    }
    @Test fun bootstrapReaderDoesNotConsumeFollowingFrames() {
        val input = ByteArrayInputStream("READY\r\nCSP1".toByteArray())
        assertEquals("READY", PairingProtocol.readLine(input)); assertEquals('C'.code, input.read())
    }
    @Test fun bootstrapRejectsOversizeNonAsciiAndTruncatedResponses() {
        assertThrows(IllegalArgumentException::class.java) { PairingProtocol.readLine(ByteArrayInputStream(("x".repeat(257) + "\n").toByteArray())) }
        assertThrows(IllegalArgumentException::class.java) { PairingProtocol.readLine(ByteArrayInputStream(byteArrayOf(0))) }
        assertThrows(EOFException::class.java) { PairingProtocol.readLine(ByteArrayInputStream("READY".toByteArray())) }
    }
    @Test fun manualIpRejectsUrlsDnsLoopbackMulticastAndBadOctets() {
        assertEquals("192.168.137.1", ManualAddress.parse(" 192.168.137.1 "))
        for (bad in listOf("pc.local", "https://192.168.1.2", "127.0.0.1", "224.0.0.1", "0.0.0.0", "192.168.1.255", "256.2.3.4", "192.168.01.1", "1.2.3"))
            assertThrows(IllegalArgumentException::class.java) { ManualAddress.parse(bad) }
    }
    @Test fun pinnedPeerAcceptedAndUnknownPeerRejectedOutsidePairing() {
        val certificate = TestCertificate(byteArrayOf(1, 2, 3))
        val pin = hash(certificate.encoded)
        val trust = PinnedTrustManager(pin, false)
        trust.checkServerTrusted(arrayOf(certificate), "ECDHE_RSA"); assertEquals(pin, trust.peerFingerprint)
        assertThrows(CertificateException::class.java) { PinnedTrustManager(a, false).checkServerTrusted(arrayOf(certificate), "RSA") }
        assertThrows(CertificateException::class.java) { PinnedTrustManager(null, false).checkServerTrusted(arrayOf(certificate), "RSA") }
    }
    @Test fun onlyExplicitPairingAdmitsProvisionalIdentity() {
        val trust = PinnedTrustManager(a, true)
        trust.checkServerTrusted(arrayOf(TestCertificate(byteArrayOf(1))), "RSA")
        assertNotNull(trust.peerFingerprint)
        assertThrows(CertificateException::class.java) { trust.checkServerTrusted(emptyArray(), "RSA") }
        assertThrows(CertificateException::class.java) { trust.checkClientTrusted(emptyArray(), "RSA") }
    }
    @Test fun encryptedPinRoundTripHasRandomIvAndNoPlaintext() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val one = PeerPinCipher.encrypt(a, key); val two = PeerPinCipher.encrypt(a, key)
        assertEquals(a, PeerPinCipher.decrypt(one, key)); assertFalse(one.contentEquals(two))
        assertFalse(one.toString(Charsets.US_ASCII).contains(a))
    }
    @Test fun encryptedPinRejectsTamperingWrongKeyAndTruncation() {
        val generator = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = generator.generateKey(); val encrypted = PeerPinCipher.encrypt(b, key)
        assertThrows(Exception::class.java) { PeerPinCipher.decrypt(encrypted.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }, key) }
        assertThrows(Exception::class.java) { PeerPinCipher.decrypt(encrypted, generator.generateKey()) }
        assertThrows(IllegalArgumentException::class.java) { PeerPinCipher.decrypt(encrypted.copyOf(20), key) }
    }
}

/** The TLS provider validates certificate structure/signatures; this stub exercises pin policy only. */
private class TestCertificate(private val data: ByteArray) : X509Certificate() {
    override fun getEncoded() = data
    override fun checkValidity() {}
    override fun checkValidity(date: Date?) {}
    override fun getVersion() = 3
    override fun getSerialNumber() = BigInteger.ONE
    override fun getIssuerDN(): Principal = Principal { "CN=test" }
    override fun getSubjectDN(): Principal = issuerDN
    override fun getNotBefore() = Date(0)
    override fun getNotAfter() = Date(0)
    override fun getTBSCertificate() = data
    override fun getSignature() = byteArrayOf()
    override fun getSigAlgName() = "SHA256withECDSA"
    override fun getSigAlgOID() = "1.2.840.10045.4.3.2"
    override fun getSigAlgParams(): ByteArray? = null
    override fun getIssuerUniqueID(): BooleanArray? = null
    override fun getSubjectUniqueID(): BooleanArray? = null
    override fun getKeyUsage(): BooleanArray? = null
    override fun getBasicConstraints() = -1
    override fun verify(key: PublicKey?) {}
    override fun verify(key: PublicKey?, provider: String?) {}
    override fun getPublicKey(): PublicKey? = null
    override fun toString() = "Test certificate"
    override fun getCriticalExtensionOIDs(): Set<String>? = null
    override fun getNonCriticalExtensionOIDs(): Set<String>? = null
    override fun getExtensionValue(oid: String?): ByteArray? = null
    override fun hasUnsupportedCriticalExtension() = false
}
