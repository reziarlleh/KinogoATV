package com.kinogo.atv

import android.app.Application
import android.content.Context
import android.webkit.WebView
import com.kinogo.atv.diagnostics.StartupDiagnostics

class KinogoApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupDiagnostics.install(base)
    }

    override fun onCreate() {
        super.onCreate()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }
}
