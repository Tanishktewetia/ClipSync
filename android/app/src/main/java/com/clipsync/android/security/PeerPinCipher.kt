package com.clipsync.android.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated format: 12-byte random IV + encrypted pin + 16-byte tag. */
object PeerPinCipher {
    private val aad = "clipsync_peer_protection_v1".toByteArray(Charsets.UTF_8)
    fun encrypt(fingerprint: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        return cipher.iv + cipher.doFinal(PairingProtocol.fingerprint(fingerprint).toByteArray(Charsets.US_ASCII))
    }
    fun decrypt(encrypted: ByteArray, key: SecretKey): String {
        require(encrypted.size == 12 + 64 + 16) { "Invalid protected peer identity" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return PairingProtocol.fingerprint(cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)).toString(Charsets.US_ASCII))
    }
}
