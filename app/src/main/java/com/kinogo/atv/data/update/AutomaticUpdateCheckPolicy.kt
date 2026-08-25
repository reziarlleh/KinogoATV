package com.kinogo.atv.data.update

internal enum class AppUpdateCheckOrigin {
    AUTOMATIC,
    MANUAL,
}

/** Pure startup orchestration rules kept outside the application root. */
internal object AutomaticUpdateCheckPolicy {
    const val MAX_ATTEMPTS = 2
    const val RETRY_DELAY_MS = 10_000L

    fun shouldStart(
        autoCheckPreference: Boolean?,
        alreadyStarted: Boolean,
    ): Boolean = autoCheckPreference == true && !alreadyStarted

    fun shouldRetry(
        origin: AppUpdateCheckOrigin,
        failedAttempt: Int,
    ): Boolean = origin == AppUpdateCheckOrigin.AUTOMATIC && failedAttempt < MAX_ATTEMPTS

    fun shouldPrompt(
        origin: AppUpdateCheckOrigin,
        result: AppUpdateCheckResult,
    ): Boolean = origin == AppUpdateCheckOrigin.AUTOMATIC &&
        result is AppUpdateCheckResult.Available
}
