package com.kinogo.atv.diagnostics

import java.util.concurrent.atomic.AtomicBoolean

internal class FatalCrashHandler(
    private val recorder: (Thread, Throwable) -> Unit,
    private val delegate: Thread.UncaughtExceptionHandler?,
    private val terminateFallback: () -> Unit,
) : Thread.UncaughtExceptionHandler {
    private val recorded = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            if (recorded.compareAndSet(false, true)) recorder(thread, error)
        } catch (_: Throwable) {
            // A failing crash recorder must never prevent Android from terminating normally.
        } finally {
            if (delegate != null && delegate !== this) {
                delegate.uncaughtException(thread, error)
            } else {
                terminateFallback()
            }
        }
    }
}
