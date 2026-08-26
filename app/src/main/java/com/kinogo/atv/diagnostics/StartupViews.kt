package com.kinogo.atv.diagnostics

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

internal data class NativeStartupHost(
    val root: FrameLayout,
    val overlay: View,
    val message: TextView,
    val progress: ProgressBar,
    val diagnosticsButton: Button,
)

internal const val STARTUP_APP_TITLE = "KinogoATV"

internal object StartupViews {
    fun loading(context: Context): NativeStartupHost {
        val root = FrameLayout(context).apply {
            setBackgroundColor(BACKGROUND)
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(64), context.dp(48), context.dp(64), context.dp(48))
        }
        val title = text(context, STARTUP_APP_TITLE, 42f, Color.WHITE).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val message = text(context, "Запуск приложения…", 24f, TEXT_SECONDARY).apply {
            gravity = Gravity.CENTER
            setPadding(0, context.dp(20), 0, context.dp(22))
        }
        val progress = ProgressBar(context).apply { isIndeterminate = true }
        val diagnosticsButton = tvButton(context, "Диагностика").apply {
            visibility = View.GONE
        }
        panel.addView(title, linearMatchWrap())
        panel.addView(message, linearMatchWrap())
        panel.addView(progress, LinearLayout.LayoutParams(context.dp(52), context.dp(52)))
        panel.addView(
            diagnosticsButton,
            LinearLayout.LayoutParams(context.dp(280), context.dp(58)).apply {
                topMargin = context.dp(24)
            },
        )
        root.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        return NativeStartupHost(root, panel, message, progress, diagnosticsButton)
    }

    fun showDelayed(host: NativeStartupHost, onDiagnostics: () -> Unit) {
        host.message.text = "Запуск занимает слишком много времени"
        host.progress.visibility = View.GONE
        host.diagnosticsButton.apply {
            visibility = View.VISIBLE
            setOnClickListener { onDiagnostics() }
            post { requestFocus() }
        }
    }

    fun report(
        context: Context,
        report: StartupReport,
        onRetry: () -> Unit,
        onExport: () -> Unit,
        onClose: () -> Unit,
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(context.dp(54), context.dp(32), context.dp(54), context.dp(30))
        }
        root.addView(
            text(context, "KinogoATV не смог запуститься", 34f, Color.WHITE).apply {
                typeface = Typeface.DEFAULT_BOLD
            },
            linearMatchWrap(),
        )
        val summary = buildString {
            appendLine("Код: ${report.code}")
            appendLine("Этап: ${report.stage ?: "неизвестен"}")
            appendLine("Android: ${report.metadata.androidVersion}")
            append(report.summary)
        }
        root.addView(
            text(context, summary, 20f, TEXT_SECONDARY).apply {
                setPadding(0, context.dp(12), 0, context.dp(16))
            },
            linearMatchWrap(),
        )

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        val retry = tvButton(context, "Повторить запуск").apply {
            setOnClickListener { onRetry() }
        }
        val detailsButton = tvButton(context, "Подробнее")
        val export = tvButton(context, "Сохранить отчёт").apply {
            setOnClickListener { onExport() }
        }
        val close = tvButton(context, "Закрыть").apply {
            setOnClickListener { onClose() }
        }
        listOf(retry, detailsButton, export, close).forEach { button ->
            actions.addView(
                button,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(56)).apply {
                    marginEnd = context.dp(14)
                },
            )
        }
        root.addView(actions, linearMatchWrap())

        val detailsText = text(
            context,
            StartupReportFormatter.renderForScreen(report),
            16f,
            Color.WHITE,
        ).apply {
            typeface = Typeface.MONOSPACE
            setPadding(context.dp(18), context.dp(16), context.dp(18), context.dp(16))
            isFocusable = false
        }
        val details = ScrollView(context).apply {
            visibility = View.GONE
            isFocusable = true
            isFocusableInTouchMode = false
            background = roundedDrawable(PANEL, BORDER, context.dp(1), context.dp(10).toFloat())
            addView(
                detailsText,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        smoothScrollBy(0, context.dp(180))
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        smoothScrollBy(0, -context.dp(180))
                        true
                    }
                    else -> false
                }
            }
        }
        detailsButton.setOnClickListener {
            val show = details.visibility != View.VISIBLE
            details.visibility = if (show) View.VISIBLE else View.GONE
            detailsButton.text = if (show) "Скрыть" else "Подробнее"
            if (show) details.post { details.requestFocus() }
        }
        root.addView(
            details,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = context.dp(18)
            },
        )
        root.addView(
            text(
                context,
                "Сфотографируйте экран вместе с кодом ошибки или сохраните текстовый отчёт.",
                16f,
                TEXT_SECONDARY,
            ).apply { setPadding(0, context.dp(10), 0, 0) },
            linearMatchWrap(),
        )
        retry.post { retry.requestFocus() }
        return root
    }

    private fun text(context: Context, value: String, sizeSp: Float, color: Int) =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
        }

    private fun tvButton(context: Context, label: String) = Button(context).apply {
        text = label
        textSize = 17f
        isAllCaps = false
        isFocusable = true
        isFocusableInTouchMode = false
        minWidth = context.dp(180)
        setPadding(context.dp(18), 0, context.dp(18), 0)
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                roundedDrawable(FOCUSED, FOCUSED, context.dp(2), context.dp(9).toFloat()),
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                roundedDrawable(FOCUSED, FOCUSED, context.dp(2), context.dp(9).toFloat()),
            )
            addState(
                intArrayOf(),
                roundedDrawable(PANEL, BORDER, context.dp(1), context.dp(9).toFloat()),
            )
        }
        setTextColor(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf(),
                ),
                intArrayOf(Color.rgb(18, 25, 38), Color.rgb(18, 25, 38), Color.WHITE),
            ),
        )
    }

    private fun roundedDrawable(fill: Int, stroke: Int, strokeWidth: Int, radius: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(strokeWidth, stroke)
            cornerRadius = radius
        }

    private fun linearMatchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private const val BACKGROUND = 0xFF071426.toInt()
    private const val PANEL = 0xFF102844.toInt()
    private const val BORDER = 0xFF4D759F.toInt()
    private const val FOCUSED = 0xFFF8D45A.toInt()
    private const val TEXT_SECONDARY = 0xFFD2E2F3.toInt()
}
