package com.kinogo.atv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kinogo.atv.diagnostics.NativeStartupHost
import com.kinogo.atv.diagnostics.StartupDiagnostics
import com.kinogo.atv.diagnostics.StartupReport
import com.kinogo.atv.diagnostics.StartupStage
import com.kinogo.atv.diagnostics.StartupViews

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var diagnostics: StartupDiagnostics
    private var startupHost: NativeStartupHost? = null
    private var composeDisposer: (() -> Unit)? = null
    private var startupStartedAtElapsedMs = 0L
    private var composeReady = false
    private val delayedStartupNotice = Runnable {
        val host = startupHost ?: return@Runnable
        if (!composeReady) {
            StartupViews.showDelayed(host) {
                val elapsed = SystemClock.elapsedRealtime() - startupStartedAtElapsedMs
                showDiagnosticReport(diagnostics.recordStall(elapsed))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This view is deliberately plain Android UI: it is visible before Compose, storage,
        // networking and the rest of the application graph are touched.
        val initialHost = StartupViews.loading(this)
        startupHost = initialHost
        setContentView(initialHost.root)
        enterImmersiveMode()

        diagnostics = StartupDiagnostics.get(applicationContext)
        val pendingReport = runCatching {
            diagnostics.pendingReportOrRecoverPreviousAttempt()
        }.getOrElse { diagnostics.recordCaught(it) }
        if (pendingReport != null) {
            showDiagnosticReport(pendingReport)
        } else {
            beginStartup(initialHost)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayedStartupNotice)
        disposeCompose()
        super.onDestroy()
    }

    private fun beginStartup(existingHost: NativeStartupHost? = null) {
        mainHandler.removeCallbacks(delayedStartupNotice)
        disposeCompose()
        composeReady = false
        startupStartedAtElapsedMs = SystemClock.elapsedRealtime()
        diagnostics.beginAttempt()

        val host = existingHost ?: StartupViews.loading(this).also {
            setContentView(it.root)
        }
        startupHost = host
        diagnostics.updateStage(StartupStage.NATIVE_VIEW_ATTACHED)
        enterImmersiveMode()
        mainHandler.postDelayed(delayedStartupNotice, STARTUP_STALL_TIMEOUT_MS)

        val observer = host.root.viewTreeObserver
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (host.root.viewTreeObserver.isAlive) {
                    host.root.viewTreeObserver.removeOnPreDrawListener(this)
                }
                diagnostics.updateStage(StartupStage.NATIVE_FIRST_DRAW)
                host.root.post { attachCompose(host) }
                return true
            }
        })
    }

    private fun attachCompose(host: NativeStartupHost) {
        if (isFinishing || isDestroyed || startupHost !== host || composeReady) return
        diagnostics.updateStage(StartupStage.COMPOSE_ATTACHING)
        try {
            composeDisposer = ComposeHost.attach(
                activity = this,
                host = host.root,
                onCompositionCommitted = {
                    diagnostics.updateStage(StartupStage.COMPOSE_COMMITTED)
                },
                onFirstSuccessfulDraw = firstDraw@{
                    if (startupHost !== host || isFinishing || isDestroyed) return@firstDraw
                    diagnostics.updateStage(StartupStage.COMPOSE_FIRST_DRAW)
                    diagnostics.markUiReady()
                    composeReady = true
                    mainHandler.removeCallbacks(delayedStartupNotice)
                    host.root.removeView(host.overlay)
                    startupHost = null
                },
            )
        } catch (error: Throwable) {
            Log.e(TAG, "Compose bootstrap failed", error)
            showDiagnosticReport(diagnostics.recordCaught(error))
        }
    }

    private fun showDiagnosticReport(report: StartupReport) {
        mainHandler.removeCallbacks(delayedStartupNotice)
        disposeCompose()
        composeReady = false
        startupHost = null
        setContentView(
            StartupViews.report(
                context = this,
                report = report,
                onRetry = {
                    diagnostics.acknowledgePendingReport()
                    beginStartup()
                },
                onExport = {
                    runCatching { diagnostics.exportReport(report) }
                        .onSuccess { file ->
                            Toast.makeText(
                                this,
                                "Отчёт сохранён: ${file.absolutePath}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        .onFailure {
                            Toast.makeText(this, "Не удалось сохранить отчёт", Toast.LENGTH_LONG).show()
                        }
                },
                onClose = { finish() },
            ),
        )
        enterImmersiveMode()
    }

    private fun disposeCompose() {
        val disposer = composeDisposer
        composeDisposer = null
        runCatching { disposer?.invoke() }
    }

    private fun enterImmersiveMode() {
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to enter immersive mode", error)
        }
    }

    private companion object {
        const val TAG = "KinogoMainActivity"
        const val STARTUP_STALL_TIMEOUT_MS = 10_000L
    }
}
