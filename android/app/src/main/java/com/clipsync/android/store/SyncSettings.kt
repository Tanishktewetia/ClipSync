package com.clipsync.android.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SyncSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sync_settings_v1", Context.MODE_PRIVATE)
    var address: String
        get() = prefs.getString("address", "") ?: ""
        set(value) { prefs.edit().putString("address", value).apply() }
    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) { prefs.edit().putBoolean("paused", value).apply() }
    var batteryRequested: Boolean
        get() = prefs.getBoolean("battery_requested", false)
        set(value) { prefs.edit().putBoolean("battery_requested", value).apply() }
    val hasPin: Boolean get() = prefs.contains("peer_pin")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(KEY)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
                generateKey()
            }
        }
        return store.getKey(KEY, null) as SecretKey
    }
    // Authentication/tag errors propagate: corrupted trust must never mean "accept anyone".
    @Synchronized fun readPin(): String? {
        val encrypted = prefs.getString("peer_pin", null) ?: return null
        return com.clipsync.android.security.PeerPinCipher.decrypt(Base64.decode(encrypted, Base64.NO_WRAP), key())
    }
    @Synchronized fun pin(fingerprint: String) {
        val encrypted = com.clipsync.android.security.PeerPinCipher.encrypt(fingerprint, key())
        check(prefs.edit().putString("peer_pin", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Could not save protected peer identity"
        }
    }
    companion object { private const val KEY = "clipsync_peer_protection_v1" }
}
