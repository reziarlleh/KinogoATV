package com.kinogo.atv.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupReportFormatterTest {
    private val metadata = StartupMetadata(
        appVersion = "test (1)",
        device = "TV",
        androidVersion = "14 / API 34",
        pid = 42,
    )

    @Test
    fun throwableReportRedactsSecretsAndRoundTrips() {
        val error = IllegalStateException(
            "password=hunter2 Authorization: Bearer top-secret " +
                "https://example.test/a?access_token=abc&safe=yes",
        )

        val report = StartupReportFormatter.fromThrowable(
            kind = StartupReportKind.JAVA_FATAL,
            stage = StartupStage.COMPOSE_ATTACHING,
            error = error,
            metadata = metadata,
            createdAtEpochMs = 123L,
            threadName = "main",
        )
        val encoded = StartupReportFormatter.serialize(report)
        val decoded = StartupReportFormatter.parse(encoded)

        assertFalse(encoded.contains("hunter2"))
        assertFalse(encoded.contains("top-secret"))
        assertFalse(encoded.contains("access_token=abc"))
        assertTrue(encoded.contains("<redacted>"))
        assertNotNull(decoded)
        assertEquals(report.code, decoded?.code)
        assertEquals(report.stage, decoded?.stage)
        assertEquals(report.summary, decoded?.summary)
    }

    @Test
    fun serializedReportIsCapped() {
        val report = StartupReportFormatter.fromThrowable(
            kind = StartupReportKind.CAUGHT_BOOTSTRAP,
            stage = StartupStage.NATIVE_FIRST_DRAW,
            error = IllegalArgumentException("x".repeat(StartupReportFormatter.MAX_REPORT_CHARS * 2)),
            metadata = metadata,
            createdAtEpochMs = 456L,
        )

        val encoded = StartupReportFormatter.serialize(report)

        assertTrue(encoded.length <= StartupReportFormatter.MAX_REPORT_CHARS)
        assertTrue(encoded.endsWith("...[отчёт сокращён]"))
    }
}
