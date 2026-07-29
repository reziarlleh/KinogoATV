package com.kinogo.atv.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import com.kinogo.atv.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

internal class StartupDiagnostics private constructor(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val diagnosticsDirectory = runCatching {
        File(appContext.noBackupFilesDir, DIRECTORY_NAME)
    }.getOrElse {
        File(appContext.filesDir, DIRECTORY_NAME)
    }.apply { mkdirs() }
    private val launchStateFile = AtomicFile(File(diagnosticsDirectory, LAUNCH_STATE_FILE))
    private val reportFile = AtomicFile(File(diagnosticsDirectory, REPORT_FILE))
    private val pendingMarker = File(diagnosticsDirectory, PENDING_FILE)
    private val handlerInstalled = AtomicBoolean(false)
    private val metadata = readMetadata(appContext)

    @Volatile
    private var currentAttempt: LaunchAttempt? = null

    init {
        installFatalHandler()
    }

    fun pendingReportOrRecoverPreviousAttempt(): StartupReport? {
        readPendingReport()?.let { return it }
        val previous = readLaunchAttempt() ?: return null
        if (!LaunchAttemptClassifier.needsRecovery(previous, Process.myPid())) return null
        val evidence = if (Build.VERSION.SDK_INT >= 30) {
            ExitReasonReaderApi30.read(appContext, previous)
        } else {
            null
        }
        val report = StartupReportFormatter.fromIncompleteLaunch(previous, evidence, metadata)
        persistReport(report)
        return report
    }

    fun beginAttempt(): LaunchAttempt {
        val now = System.currentTimeMillis()
        val attempt = LaunchAttempt(
            id = "${now.toString(36)}-${Process.myPid().toString(36)}",
            pid = Process.myPid(),
            startedAtEpochMs = now,
            stage = StartupStage.ACTIVITY_CREATED,
        )
        currentAttempt = attempt
        writeLaunchAttempt(attempt)
        publishProcessState(attempt)
        return attempt
    }

    fun updateStage(stage: StartupStage) {
        val updated = currentAttempt?.copy(stage = stage) ?: return
        currentAttempt = updated
        writeLaunchAttempt(updated)
        publishProcessState(updated)
    }

    fun markUiReady() = updateStage(StartupStage.UI_READY)

    fun recordCaught(error: Throwable): StartupReport {
        val report = StartupReportFormatter.fromThrowable(
            kind = StartupReportKind.CAUGHT_BOOTSTRAP,
            stage = currentAttempt?.stage,
            error = error,
            metadata = metadata,
            threadName = Thread.currentThread().name,
        )
        persistReport(report)
        return report
    }

    fun recordStall(elapsedMs: Long): StartupReport {
        val report = StartupReportFormatter.fromStall(
            stage = currentAttempt?.stage,
            metadata = metadata,
            elapsedMs = elapsedMs,
        )
        persistReport(report)
        return report
    }

    fun acknowledgePendingReport() {
        runCatching { pendingMarker.delete() }
    }

    fun exportReport(report: StartupReport): File {
        val destinationDirectory = appContext.getExternalFilesDir("diagnostics")
            ?: File(appContext.filesDir, "diagnostics-export")
        check(destinationDirectory.exists() || destinationDirectory.mkdirs()) {
            "Не удалось создать каталог диагностики"
        }
        val destination = File(destinationDirectory, "KinogoTV-${report.code}.txt")
        FileOutputStream(destination).use { stream ->
            stream.write(StartupReportFormatter.serialize(report).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        return destination
    }

    private fun installFatalHandler() {
        if (!handlerInstalled.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val handler = FatalCrashHandler(
            recorder = ::recordFatal,
            delegate = previous,
            terminateFallback = {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            },
        )
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    private fun recordFatal(thread: Thread, error: Throwable) {
        val report = StartupReportFormatter.fromThrowable(
            kind = StartupReportKind.JAVA_FATAL,
            stage = currentAttempt?.stage,
            error = error,
            metadata = metadata,
            threadName = thread.name,
        )
        persistReport(report)
    }

    private fun persistReport(report: StartupReport) {
        runCatching {
            writeAtomic(reportFile, StartupReportFormatter.serialize(report))
            FileOutputStream(pendingMarker).use { stream ->
                stream.write(byteArrayOf(1))
                stream.fd.sync()
            }
        }.onFailure { error ->
            Log.e(LOG_TAG, "Unable to persist startup report", error)
        }
    }

    private fun readPendingReport(): StartupReport? {
        if (!pendingMarker.isFile) return null
        val report = readAtomic(reportFile)?.let(StartupReportFormatter::parse)
        if (report == null) runCatching { pendingMarker.delete() }
        return report
    }

    private fun readLaunchAttempt(): LaunchAttempt? =
        readAtomic(launchStateFile)?.let(LaunchAttemptCodec::decode)

    private fun writeLaunchAttempt(attempt: LaunchAttempt) {
        runCatching {
            writeAtomic(launchStateFile, LaunchAttemptCodec.encode(attempt))
        }.onFailure { error ->
            Log.e(LOG_TAG, "Unable to persist launch stage", error)
        }
    }

    private fun publishProcessState(attempt: LaunchAttempt) {
        if (Build.VERSION.SDK_INT >= 30) {
            ExitReasonReaderApi30.setProcessStateSummary(
                appContext,
                "attempt=${attempt.id};stage=${attempt.stage.name}",
            )
        }
    }

    private fun readAtomic(file: AtomicFile): String? = runCatching {
        if (!file.baseFile.isFile) return@runCatching null
        file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { error ->
        Log.e(LOG_TAG, "Unable to read ${file.baseFile.name}", error)
    }.getOrNull()

    private fun writeAtomic(file: AtomicFile, value: String) {
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun readMetadata(context: Context): StartupMetadata {
        val packageVersion = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName ?: BuildConfig.VERSION_NAME} (${info.longVersionCode})"
        }.getOrElse {
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return StartupMetadata(
            appVersion = packageVersion,
            device = listOf(manufacturer, model).filter(String::isNotBlank).distinct().joinToString(" ")
                .ifBlank { "неизвестно" },
            androidVersion = "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
            pid = Process.myPid(),
        )
    }

    companion object {
        private const val DIRECTORY_NAME = "startup-diagnostics"
        private const val LAUNCH_STATE_FILE = "launch-state.txt"
        private const val REPORT_FILE = "last-crash.txt"
        private const val PENDING_FILE = "last-crash.pending"
        private const val LOG_TAG = "KinogoStartupDiagnostics"

        @Volatile
        private var instance: StartupDiagnostics? = null

        fun install(context: Context): StartupDiagnostics = instance ?: synchronized(this) {
            instance ?: StartupDiagnostics(context).also { instance = it }
        }

        fun get(context: Context): StartupDiagnostics = install(context)
    }
}
