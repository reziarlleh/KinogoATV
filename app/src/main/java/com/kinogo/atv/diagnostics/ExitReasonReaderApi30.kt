package com.kinogo.atv.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.annotation.RequiresApi
import kotlin.math.abs

@RequiresApi(30)
internal object ExitReasonReaderApi30 {
    fun read(context: Context, attempt: LaunchAttempt): ProcessExitEvidence? = runCatching {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val candidates = activityManager
            .getHistoricalProcessExitReasons(context.packageName, 0, 16)
            .filter { info -> info.timestamp >= attempt.startedAtEpochMs - TIMESTAMP_TOLERANCE_MS }
        val match = candidates.firstOrNull { info -> info.pid == attempt.pid }
            ?: candidates.minByOrNull { info -> abs(info.timestamp - attempt.startedAtEpochMs) }
            ?: return@runCatching null
        ProcessExitEvidence(
            reason = reasonLabel(match.reason),
            description = match.description?.take(MAX_DESCRIPTION_CHARS),
            status = match.status,
            timestampEpochMs = match.timestamp,
            processStateSummary = match.processStateSummary
                ?.toString(Charsets.UTF_8)
                ?.take(MAX_STATE_CHARS),
        )
    }.getOrNull()

    fun setProcessStateSummary(context: Context, summary: String) {
        runCatching {
            context.getSystemService(ActivityManager::class.java)
                .setProcessStateSummary(summary.toByteArray(Charsets.UTF_8).take(MAX_STATE_BYTES).toByteArray())
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "приложение завершило процесс"
        ApplicationExitInfo.REASON_SIGNALED -> "процесс завершён сигналом"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "нехватка памяти"
        ApplicationExitInfo.REASON_CRASH -> "Java/Kotlin crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "приложение не отвечало (ANR)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "ошибка инициализации"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "изменение разрешений"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "чрезмерное использование ресурсов"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "остановлено пользователем"
        ApplicationExitInfo.REASON_USER_STOPPED -> "пользователь остановил пакет"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "завершилась системная зависимость"
        ApplicationExitInfo.REASON_OTHER -> "другая системная причина"
        ApplicationExitInfo.REASON_UNKNOWN -> "неизвестная системная причина"
        else -> "системная причина $reason"
    }

    private const val TIMESTAMP_TOLERANCE_MS = 2_000L
    private const val MAX_DESCRIPTION_CHARS = 2_048
    private const val MAX_STATE_CHARS = 128
    private const val MAX_STATE_BYTES = 128
}
