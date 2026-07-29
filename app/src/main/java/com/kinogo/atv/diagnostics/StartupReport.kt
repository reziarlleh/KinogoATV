package com.kinogo.atv.diagnostics

internal enum class StartupStage {
    ACTIVITY_CREATED,
    NATIVE_VIEW_ATTACHED,
    NATIVE_FIRST_DRAW,
    COMPOSE_ATTACHING,
    COMPOSE_COMMITTED,
    COMPOSE_FIRST_DRAW,
    UI_READY,
}

internal enum class StartupReportKind {
    JAVA_FATAL,
    CAUGHT_BOOTSTRAP,
    INCOMPLETE_PREVIOUS_LAUNCH,
    STARTUP_STALL,
}

internal data class StartupMetadata(
    val appVersion: String,
    val device: String,
    val androidVersion: String,
    val pid: Int,
)

internal data class StartupReport(
    val code: String,
    val kind: StartupReportKind,
    val createdAtEpochMs: Long,
    val stage: StartupStage?,
    val summary: String,
    val details: String,
    val metadata: StartupMetadata,
)

internal data class LaunchAttempt(
    val id: String,
    val pid: Int,
    val startedAtEpochMs: Long,
    val stage: StartupStage,
)

internal data class ProcessExitEvidence(
    val reason: String,
    val description: String?,
    val status: Int,
    val timestampEpochMs: Long,
    val processStateSummary: String?,
)

internal object LaunchAttemptClassifier {
    fun needsRecovery(previous: LaunchAttempt?, currentPid: Int): Boolean =
        previous != null &&
            previous.pid != currentPid &&
            previous.stage != StartupStage.UI_READY
}
