package com.kinogo.atv.diagnostics

internal object LaunchAttemptCodec {
    private const val HEADER = "KINOGO_TV_LAUNCH_V1"

    fun encode(attempt: LaunchAttempt): String = buildString {
        appendLine(HEADER)
        appendLine("ID=${attempt.id}")
        appendLine("PID=${attempt.pid}")
        appendLine("STARTED_AT=${attempt.startedAtEpochMs}")
        appendLine("STAGE=${attempt.stage.name}")
    }

    fun decode(raw: String): LaunchAttempt? {
        if (!raw.startsWith(HEADER)) return null
        val values = raw.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
        return runCatching {
            LaunchAttempt(
                id = values.getValue("ID"),
                pid = values.getValue("PID").toInt(),
                startedAtEpochMs = values.getValue("STARTED_AT").toLong(),
                stage = StartupStage.valueOf(values.getValue("STAGE")),
            )
        }.getOrNull()
    }
}
