package com.kinogo.atv

import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.ComposeView
import java.util.concurrent.atomic.AtomicBoolean

internal object ComposeHost {
    fun attach(
        activity: ComponentActivity,
        host: FrameLayout,
        onCompositionCommitted: () -> Unit,
        onFirstSuccessfulDraw: () -> Unit,
    ): () -> Unit {
        val committed = AtomicBoolean(false)
        val firstDrawDelivered = AtomicBoolean(false)
        val composeView = ComposeView(activity).apply {
            setContent {
                KinogoAppRoot()
                SideEffect {
                    if (committed.compareAndSet(false, true)) {
                        onCompositionCommitted()
                        invalidate()
                    }
                }
            }
        }
        val drawListener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (committed.get() && firstDrawDelivered.compareAndSet(false, true)) {
                    if (composeView.viewTreeObserver.isAlive) {
                        composeView.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    composeView.post(onFirstSuccessfulDraw)
                }
                return true
            }
        }
        composeView.viewTreeObserver.addOnPreDrawListener(drawListener)
        host.addView(
            composeView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return {
            if (composeView.viewTreeObserver.isAlive) {
                composeView.viewTreeObserver.removeOnPreDrawListener(drawListener)
            }
            composeView.disposeComposition()
            (composeView.parent as? ViewGroup)?.removeView(composeView)
        }
    }
}
