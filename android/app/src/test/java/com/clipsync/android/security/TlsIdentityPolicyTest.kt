package com.clipsync.android.security

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class TlsIdentityPolicyTest {
    @Test fun authorizePrehashedSigningRequiredByConscrypt() {
        assertTrue(TlsIdentityPolicy.digests.contains("NONE"))
        assertTrue(TlsIdentityPolicy.supportsTls(TlsIdentityPolicy.digests))
    }
    @Test fun oldKeyAuthorizationsAreInsufficient() {
        assertFalse(TlsIdentityPolicy.supportsTls(listOf("SHA-256", "SHA-384", "SHA-512")))
        assertFalse(TlsIdentityPolicy.supportsTls(listOf("NONE")))
        assertFalse(TlsIdentityPolicy.supportsTls(emptyList()))
    }
    @Test fun newAliasDoesNotReuseBrokenInstalledKey() {
        assertEquals("clipsync_tls_identity_v2", TlsIdentityPolicy.ALIAS)
        assertNotEquals("clipsync_tls_identity_v1", TlsIdentityPolicy.ALIAS)
    }
    @Test fun changingReturnedDigestListCannotMutatePolicy() {
        val changed = TlsIdentityPolicy.digests.toMutableList().apply { clear() }
        assertFalse(TlsIdentityPolicy.supportsTls(changed))
        assertTrue(TlsIdentityPolicy.supportsTls(TlsIdentityPolicy.digests))
    }
    @Test fun rawEcdsaSignsAlreadyHashedInputNotAnUnhashedTlsTranscript() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val transcript = "synthetic TLS transcript".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(transcript)
        val signature = Signature.getInstance("NONEwithECDSA").run {
            initSign(pair.private); update(digest); sign()
        }
        val verified = Signature.getInstance("SHA256withECDSA").run {
            initVerify(pair.public); update(transcript); verify(signature)
        }
        assertTrue(verified)
    }
}
