package com.clipsync.android.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyInfo
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import com.clipsync.android.logging.FileLogger
import com.clipsync.core.hash
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

/** Private signing key never leaves Android Keystore; not gated on biometric unlock. */
class KeyStoreIdentity : X509ExtendedKeyManager() {
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    init {
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(tlsIdentityKeySpec(ALIAS))
                generateKeyPair()
                FileLogger.info("Keystore TLS identity created: policy=v2, prehashed ECDSA authorized")
            }
        }
        // Catch unsupported authorizations locally before attempting the TLS handshake.
        val privateKey = store.getKey(ALIAS, null) as PrivateKey
        val keyInfo = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
            .getKeySpec(privateKey, KeyInfo::class.java)
        check(TlsIdentityPolicy.supportsTls(keyInfo.digests.toList())) { "TLS signing authorization missing" }
        val digest = MessageDigest.getInstance("SHA-256").digest("ClipSync TLS key self-test".toByteArray(Charsets.US_ASCII))
        val signer = Signature.getInstance("NONEwithECDSA")
        signer.initSign(privateKey)
        signer.update(digest)
        val signature = signer.sign()
        val verifier = Signature.getInstance("NONEwithECDSA")
        verifier.initVerify(store.getCertificate(ALIAS).publicKey)
        verifier.update(digest)
        check(verifier.verify(signature)) { "TLS identity self-test failed" }
        FileLogger.info("Keystore TLS signing self-test passed: policy=v2")
    }
    val fingerprint: String get() = hash(store.getCertificate(ALIAS).encoded)
    override fun getPrivateKey(alias: String?): PrivateKey? =
        if (alias == ALIAS) store.getKey(ALIAS, null) as PrivateKey else null
    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        if (alias == ALIAS) store.getCertificateChain(ALIAS).map { it as X509Certificate }.toTypedArray() else null
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        if (keyType == "EC") arrayOf(ALIAS) else null
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? =
        if (keyType?.contains("EC") == true) ALIAS else null
    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        if (keyType?.contains("EC") == true) ALIAS else null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    companion object { private const val ALIAS = TlsIdentityPolicy.ALIAS }
}

/** Shared by production generation and the device-only Keystore regression tests. */
internal fun tlsIdentityKeySpec(alias: String): KeyGenParameterSpec =
    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
        .setCertificateSubject(X500Principal("CN=ClipSync Android"))
        .setCertificateSerialNumber(BigInteger.ONE)
        .setCertificateNotBefore(Date(0))
        .setCertificateNotAfter(Date(4102444800000L))
        .build().also { check(it.digests.toSet() == TlsIdentityPolicy.digests.toSet()) }
