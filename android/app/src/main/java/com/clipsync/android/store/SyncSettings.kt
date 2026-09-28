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
    var lastAddress: String
        get() = prefs.getString("last_address", "") ?: ""
        set(value) { prefs.edit().putString("last_address", value).apply() }
    var lastPort: Int
        get() = prefs.getInt("last_port", 48653)
        set(value) { prefs.edit().putInt("last_port", value).apply() }
    var replayOnConnect: Boolean
        get() = prefs.getBoolean("replay_on_connect", true)
        set(value) { prefs.edit().putBoolean("replay_on_connect", value).apply() }
    val deviceId: String get() = prefs.getString("device_id", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).commit() }
    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) { prefs.edit().putBoolean("paused", value).apply() }
    var notificationRequested: Boolean
        get() = prefs.getBoolean("notification_requested", false)
        set(value) { prefs.edit().putBoolean("notification_requested", value).apply() }
    var batteryRequested: Boolean
        get() = prefs.getBoolean("battery_requested", false)
        set(value) { prefs.edit().putBoolean("battery_requested", value).apply() }
    val hasPin: Boolean get() = devices().isNotEmpty()
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
    var onboardingStep: Int
        get() = prefs.getInt("onboarding_step", 0)
        set(value) { check(prefs.edit().putInt("onboarding_step", value).commit()) }
    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) { check(prefs.edit().putBoolean("onboarding_done", value).commit()) }
    var tileAdded: Boolean
        get() = prefs.getBoolean("tile_added", false)
        set(value) { prefs.edit().putBoolean("tile_added", value).putBoolean("tile_dismissed", false).apply() }
    var tileDismissed: Boolean
        get() = prefs.getBoolean("tile_dismissed", false)
        set(value) { prefs.edit().putBoolean("tile_dismissed", value).apply() }
    @Synchronized fun devices(): List<PairedDevice> {
        val encoded = prefs.getString("devices_v2", null)
        if (encoded == null) {
            val old = readPin() ?: return emptyList()
            val migrated = listOf(PairedDevice(java.util.UUID.randomUUID().toString(), "My PC", "My PC", old,
                "$lastAddress:$lastPort".takeIf { lastAddress.isNotBlank() } ?: "$address:48653"))
            saveDevices(migrated)
            return migrated
        }
        val json = org.json.JSONArray(com.clipsync.android.security.DeviceListCipher.decrypt(Base64.decode(encoded, Base64.NO_WRAP), key()))
        return (0 until json.length()).map { index ->
            val d = json.getJSONObject(index)
            PairedDevice(d.getString("id"), d.getString("name"), d.getString("host"), com.clipsync.android.security.PairingProtocol.fingerprint(d.getString("pin")),
                d.getString("address"), if (d.isNull("last")) null else d.getLong("last"),
                DeviceState.Available, d.optBoolean("active", false))
        }.also { require(it.count { d -> d.isActive } <= 1); require(it.map { d -> d.certFingerprint }.distinct().size == it.size) }
    }
    @Synchronized fun saveDevices(devices: List<PairedDevice>) {
        require(devices.count { it.isActive } <= 1)
        require(devices.map { com.clipsync.android.security.PairingProtocol.fingerprint(it.certFingerprint) }.distinct().size == devices.size)
        val json = org.json.JSONArray()
        devices.forEach { d -> json.put(org.json.JSONObject().put("id", d.id).put("name", d.displayName)
            .put("host", d.hostLabel).put("pin", d.certFingerprint).put("address", d.lastKnownAddress)
            .put("last", d.lastConnectedAt ?: org.json.JSONObject.NULL).put("active", d.isActive)) }
        val encrypted = com.clipsync.android.security.DeviceListCipher.encrypt(json.toString(), key())
        check(prefs.edit().putString("devices_v2", Base64.encodeToString(encrypted, Base64.NO_WRAP)).remove("peer_pin").commit())
    }
    companion object { private const val KEY = "clipsync_peer_protection_v1" }
}
