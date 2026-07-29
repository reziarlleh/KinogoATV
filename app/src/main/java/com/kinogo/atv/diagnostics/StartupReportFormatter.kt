package com.kinogo.atv.diagnostics

import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest

internal object StartupReportFormatter {
    const val MAX_REPORT_CHARS = 24 * 1024
    private const val FORMAT_HEADER = "KINOGO_TV_STARTUP_REPORT_V1"
    private const val DETAILS_MARKER = "---DETAILS---"
    private const val TRUNCATED_MARKER = "\n...[отчёт сокращён]"

    private val authorizationPattern =
        Regex("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)[^\\s,;]+")
    private val secretFieldPattern = Regex(
        "(?i)\\b(password|passwd|access_token|refresh_token|session|cookie|set-cookie)" +
            "(\\s*[:=]\\s*)[^\\s,;&]+",
    )
    private val secretQueryPattern = Regex(
        "(?i)([?&](?:password|access_token|refresh_token|token|session)=)[^&\\s]+",
    )

    fun fromThrowable(
        kind: StartupReportKind,
        stage: StartupStage?,
        error: Throwable,
        metadata: StartupMetadata,
        createdAtEpochMs: Long = System.currentTimeMillis(),
        threadName: String? = null,
    ): StartupReport {
        val stackTrace = try {
            StringWriter().also { writer ->
                PrintWriter(writer).use { printer -> error.printStackTrace(printer) }
            }.toString()
        } catch (_: Throwable) {
            "${error.javaClass.name}: ${error.message.orEmpty()}"
        }
        val summary = sanitize(
            buildString {
                append(error.javaClass.name)
                error.message?.lineSequence()?.firstOrNull()?.takeIf(String::isNotBlank)?.let {
                    append(": ")
                    append(it)
                }
            },
        ).singleLine()
        val details = sanitize(
            buildString {
                threadName?.let {
                    append("Thread: ")
                    append(it)
                    append('\n')
                }
                append(stackTrace)
            },
        )
        return create(kind, stage, summary, details, metadata, createdAtEpochMs)
    }

    fun fromIncompleteLaunch(
        attempt: LaunchAttempt,
        evidence: ProcessExitEvidence?,
        metadata: StartupMetadata,
        createdAtEpochMs: Long = System.currentTimeMillis(),
    ): StartupReport {
        val reason = evidence?.reason ?: "причина не предоставлена системой"
        val summary = "Предыдущий запуск завершился до первого кадра: $reason"
        val details = buildString {
            append("Attempt: ${attempt.id}\n")
            append("Previous PID: ${attempt.pid}\n")
            append("Started: ${attempt.startedAtEpochMs}\n")
            append("Last stage: ${attempt.stage}\n")
            if (evidence != null) {
                append("Exit reason: ${evidence.reason}\n")
                append("Exit status: ${evidence.status}\n")
                append("Exit timestamp: ${evidence.timestampEpochMs}\n")
                evidence.description?.let { append("Description: $it\n") }
                evidence.processStateSummary?.let { append("Process state: $it\n") }
            } else {
                append("Android 9/10 не предоставляет ApplicationExitInfo; ")
                append("возможны native crash, остановка системой или принудительное завершение.\n")
            }
        }
        return create(
            StartupReportKind.INCOMPLETE_PREVIOUS_LAUNCH,
            attempt.stage,
            sanitize(summary).singleLine(),
            sanitize(details),
            metadata,
            createdAtEpochMs,
        )
    }

    fun fromStall(
        stage: StartupStage?,
        metadata: StartupMetadata,
        elapsedMs: Long,
        createdAtEpochMs: Long = System.currentTimeMillis(),
    ): StartupReport = create(
        kind = StartupReportKind.STARTUP_STALL,
        stage = stage,
        summary = "Интерфейс не появился за ${elapsedMs / 1_000} секунд",
        details = "Запуск не завершился, но необработанное исключение не было зафиксировано.",
        metadata = metadata,
        createdAtEpochMs = createdAtEpochMs,
    )

    fun serialize(report: StartupReport): String {
        val header = buildString {
            appendLine(FORMAT_HEADER)
            appendLine("CODE=${report.code.singleLine()}")
            appendLine("KIND=${report.kind.name}")
            appendLine("CREATED_AT=${report.createdAtEpochMs}")
            appendLine("STAGE=${report.stage?.name.orEmpty()}")
            appendLine("APP=${report.metadata.appVersion.singleLine()}")
            appendLine("DEVICE=${report.metadata.device.singleLine()}")
            appendLine("ANDROID=${report.metadata.androidVersion.singleLine()}")
            appendLine("PID=${report.metadata.pid}")
            appendLine("SUMMARY=${sanitize(report.summary).singleLine()}")
            appendLine(DETAILS_MARKER)
        }
        val safeDetails = sanitize(report.details)
        val available = (MAX_REPORT_CHARS - header.length - TRUNCATED_MARKER.length).coerceAtLeast(0)
        return if (header.length + safeDetails.length <= MAX_REPORT_CHARS) {
            header + safeDetails
        } else {
            header + safeDetails.take(available) + TRUNCATED_MARKER
        }
    }

    fun parse(raw: String): StartupReport? {
        if (!raw.startsWith(FORMAT_HEADER)) return null
        val markerIndex = raw.indexOf(DETAILS_MARKER)
        if (markerIndex < 0) return null
        val header = raw.substring(0, markerIndex).lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
        return runCatching {
            StartupReport(
                code = header.getValue("CODE"),
                kind = StartupReportKind.valueOf(header.getValue("KIND")),
                createdAtEpochMs = header.getValue("CREATED_AT").toLong(),
                stage = header["STAGE"]?.takeIf(String::isNotBlank)?.let(StartupStage::valueOf),
                summary = header.getValue("SUMMARY"),
                details = raw.substring(markerIndex + DETAILS_MARKER.length).trimStart('\r', '\n'),
                metadata = StartupMetadata(
                    appVersion = header.getValue("APP"),
                    device = header.getValue("DEVICE"),
                    androidVersion = header.getValue("ANDROID"),
                    pid = header.getValue("PID").toInt(),
                ),
            )
        }.getOrNull()
    }

    fun renderForScreen(report: StartupReport): String = buildString {
        appendLine("Код: ${report.code}")
        appendLine("Тип: ${report.kind}")
        appendLine("Версия: ${report.metadata.appVersion}")
        appendLine("Устройство: ${report.metadata.device}")
        appendLine("Android: ${report.metadata.androidVersion}")
        appendLine("PID: ${report.metadata.pid}")
        appendLine("Этап: ${report.stage ?: "неизвестен"}")
        appendLine("Время (epoch ms): ${report.createdAtEpochMs}")
        appendLine("Причина: ${report.summary}")
        appendLine()
        append(report.details)
    }

    fun sanitize(value: String): String = secretQueryPattern.replace(
        secretFieldPattern.replace(
            authorizationPattern.replace(value) { match ->
                match.groupValues[1] + "<redacted>"
            },
        ) { match ->
            match.groupValues[1] + match.groupValues[2] + "<redacted>"
        },
    ) { match ->
        match.groupValues[1] + "<redacted>"
    }

    private fun create(
        kind: StartupReportKind,
        stage: StartupStage?,
        summary: String,
        details: String,
        metadata: StartupMetadata,
        createdAtEpochMs: Long,
    ): StartupReport {
        val safeSummary = sanitize(summary).singleLine()
        val safeDetails = sanitize(details)
        val signature = buildString {
            append(createdAtEpochMs)
            append('|')
            append(kind)
            append('|')
            append(stage)
            append('|')
            append(safeSummary)
            append('|')
            append(safeDetails.take(512))
        }
        return StartupReport(
            code = "KTV-${shortHash(signature)}",
            kind = kind,
            createdAtEpochMs = createdAtEpochMs,
            stage = stage,
            summary = safeSummary,
            details = safeDetails,
            metadata = metadata,
        )
    }

    private fun shortHash(value: String): String = runCatching {
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(4)
            .joinToString(separator = "") { byte -> "%02X".format(byte.toInt() and 0xff) }
    }.getOrElse {
        value.hashCode().toUInt().toString(16).uppercase().padStart(8, '0')
    }

    private fun String.singleLine(): String =
        replace('\r', ' ').replace('\n', ' ').trim().take(1_024)
}
