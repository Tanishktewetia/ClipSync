package com.clipsync.android.security

/** Android TLS computes the transcript digest, then invokes NONEwithECDSA on Keystore.
 * "NONE" here authorizes signing that digest; it does NOT disable TLS hashing/verification.
 * A Keystore key's authorizations cannot be amended after generation, so 0.5.0's
 * incompatible v1 key is retained but no longer selected. Never overwrite a paired key in place.
 * Source: Android KeyGenParameterSpec.Builder.setDigests documentation.
 */
internal object TlsIdentityPolicy {
    const val ALIAS = "clipsync_tls_identity_v2"
    val digests: List<String> get() = listOf("NONE", "SHA-256", "SHA-384", "SHA-512")
    fun supportsTls(authorized: Collection<String>): Boolean = authorized.containsAll(listOf("NONE", "SHA-256"))
}
