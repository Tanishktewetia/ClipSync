package com.clipsync.android.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A distinct authenticated format; legacy fixed-length pins cannot be mistaken for a device list. */
object DeviceListCipher {
    private val aad = "clipsync_devices_v2".toByteArray(Charsets.UTF_8)
    fun encrypt(value: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key); cipher.updateAAD(aad)
        return cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }
    fun decrypt(value: ByteArray, key: SecretKey): String {
        require(value.size >= 28) { "Invalid protected device list" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, value.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return cipher.doFinal(value.copyOfRange(12, value.size)).toString(Charsets.UTF_8)
    }
}
