package com.clipsync.android.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.clipsync.android.clipboard.ClipboardReadStore

class ClipboardReadActivity : Activity() {
    private var hasReadClipboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        com.clipsync.android.logging.FileLogger.info("Clipboard read activity launched: action=${intent?.action}")
    }

    override fun onPostResume() {
        super.onPostResume()
        if (intent?.action != ACTION_READ_CLIPBOARD || hasReadClipboard) return

        window.decorView.post {
            if (hasReadClipboard || isFinishing) return@post
            hasReadClipboard = true
            readClipboardOnce()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
            }
            overridePendingTransition(0, 0)
        }
    }

    private fun readClipboardOnce() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                com.clipsync.android.logging.FileLogger.warn("Manual clipboard read found no primary clip")
                return
            }
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (text.isNullOrEmpty()) {
                com.clipsync.android.logging.FileLogger.warn("Manual clipboard read found empty text")
            } else {
                com.clipsync.android.logging.FileLogger.info("Manual clipboard read obtained text length=${text.length}")
                ClipboardReadStore.recordRead(this, text)
            }
        } catch (e: Exception) {
            com.clipsync.android.logging.FileLogger.error("Manual clipboard read failed", e)
        }
    }

    companion object {
        const val ACTION_READ_CLIPBOARD = "com.clipsync.android.action.READ_CLIPBOARD"

        fun createIntent(context: Context, source: String): Intent = Intent(context, ClipboardReadActivity::class.java)
            .setAction(ACTION_READ_CLIPBOARD)
            .setData(android.net.Uri.parse("clipsync://clipboard/$source/${System.nanoTime()}"))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
    }
}
