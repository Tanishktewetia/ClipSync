package com.clipsync.android.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.*
import android.os.*
import android.view.WindowManager
import com.clipsync.android.clipboard.ClipboardReadStore
import com.clipsync.android.logging.FileLogger
import com.clipsync.android.service.SyncRuntime
import com.clipsync.core.ManualSendResult

/** One user-requested read after actual focus, then return to the source app immediately.
 * No clipboard polling/listener, no network wait, no main-task navigation.
 */
class ClipboardReadActivity : Activity() {
    private var consumed = false
    private var resumed = false
    private val handler = Handler(Looper.getMainLooper())
    private val focusTimeout = Runnable {
        if (!consumed) { feedback("Clipboard not sent: no focused window. Unlock and tap Send again."); finishRead() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumed = savedInstanceState?.getBoolean("consumed") ?: false
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        SyncRuntime.initialize(this)
        if (intent.action != ACTION_READ_CLIPBOARD || consumed) { finishRead(); return }
        handler.postDelayed(focusTimeout, 1500)
    }
    override fun onPostResume() { super.onPostResume(); resumed = true; readIfFocused() }
    override fun onPause() { resumed = false; super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) readIfFocused() }
    private fun readIfFocused() {
        if (!resumed || !hasWindowFocus() || consumed || isFinishing) return
        consumed = true
        handler.removeCallbacks(focusTimeout)
        try {
            if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                feedback("Unlock the phone before sending clipboard text."); return
            }
            val state = SyncRuntime.state.value
            if (state.paused) { feedback("Not sent: syncing is paused."); return }
            if (!state.connected || SyncRuntime.onManualSend == null) {
                feedback("Not sent: wait for Connected, then tap Send again."); return
            }
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            // Text only. Do not dereference a content URI, Intent, HTML conversion or another app's provider.
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
            if (text.isNullOrEmpty()) { feedback("No plain text on the clipboard."); return }
            val sensitiveKey = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
            val sensitive = clip?.description?.extras?.getBoolean(sensitiveKey, false) == true
            val result = SyncRuntime.onManualSend?.invoke(text, sensitive)
            if (result == ManualSendResult.QUEUED) ClipboardReadStore.recordRead(this, text)
        } catch (e: Exception) {
            FileLogger.warn("Manual clipboard read failed: "+e.javaClass.simpleName)
            feedback("Clipboard read failed. Unlock and tap Send again.")
        } finally { finishRead() }
    }
    private fun feedback(message: String) {
        SyncRuntime.update { it.copy(sendFeedback = message) }
        // Fixed local messages only; never include clipboard content or exception messages.
        FileLogger.info(message)
    }
    private fun finishRead() { consumed = true; handler.removeCallbacks(focusTimeout); finishAndRemoveTask(); @Suppress("DEPRECATION") overridePendingTransition(0, 0) }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("consumed", consumed); super.onSaveInstanceState(outState) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
    companion object {
        const val ACTION_READ_CLIPBOARD = "com.clipsync.android.action.READ_CLIPBOARD"
        fun createIntent(context: Context, source: String): Intent = Intent(context, ClipboardReadActivity::class.java)
            .setAction(ACTION_READ_CLIPBOARD)
            .setData(android.net.Uri.parse("clipsync://clipboard/$source"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}
