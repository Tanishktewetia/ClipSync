package com.clipsync.android.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Device-only regression: compile locally, run only on a disposable emulator/test device.
 * Uses unique test aliases, never selects/deletes either production identity or the peer pin.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreTlsSigningTest {
    @Test fun productionSpecSupportsConscryptPrehashedSignatureWithoutExportingPrivateKey() {
        val alias = "clipsync_test_tls_" + UUID.randomUUID()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val pair = KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply { initialize(tlsIdentityKeySpec(alias)) }.generateKeyPair()
            val info = KeyFactory.getInstance("EC", "AndroidKeyStore").getKeySpec(pair.private, KeyInfo::class.java)
            assertTrue(TlsIdentityPolicy.supportsTls(info.digests.toList()))
            assertNull(pair.private.encoded)
            val digest = MessageDigest.getInstance("SHA-256").digest("test-only TLS input".toByteArray())
            val signature = Signature.getInstance("NONEwithECDSA").run { initSign(pair.private); update(digest); sign() }
            val verified = Signature.getInstance("NONEwithECDSA").run { initVerify(pair.public); update(digest); verify(signature) }
            assertTrue(verified)
        } finally { store.deleteEntry(alias) }
    }
    @Test fun legacyDigestPolicyCannotPerformConscryptSigningOperation() {
        val alias = "clipsync_test_legacy_" + UUID.randomUUID()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val pair = KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512).build())
            }.generateKeyPair()
            val digest = MessageDigest.getInstance("SHA-256").digest("test-only TLS input".toByteArray())
            assertThrows(Exception::class.java) {
                Signature.getInstance("NONEwithECDSA").run { initSign(pair.private); update(digest); sign() }
            }
        } finally { store.deleteEntry(alias) }
    }
}
