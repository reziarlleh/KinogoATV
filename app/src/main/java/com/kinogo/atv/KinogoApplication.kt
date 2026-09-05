package com.kinogo.atv

import android.app.Application
import android.content.Context
import android.webkit.WebView
import com.kinogo.atv.diagnostics.StartupDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KinogoApplication : Application() {
    /**
     * Playback checkpoints must outlive a Compose host/Activity recreation. The process-owned
     * queue keeps their order while DataStore performs the actual atomic disk writes.
     */
    internal val playbackCheckpointWriteQueue = PlaybackCheckpointWriteQueue()
    internal val playbackCheckpointWriteScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupDiagnostics.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }
}
