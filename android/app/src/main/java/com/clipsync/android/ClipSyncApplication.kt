package com.clipsync.android

import android.app.Application
import com.clipsync.android.logging.CrashHandler
import com.clipsync.android.logging.FileLogger

class ClipSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        CrashHandler.init(this)
        FileLogger.info("ClipSync v${BuildConfig.VERSION_NAME} starting")
    }
}
