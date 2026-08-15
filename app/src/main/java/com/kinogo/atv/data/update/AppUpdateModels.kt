package com.kinogo.atv.data.update

import java.io.File

internal sealed interface AppUpdateCheckResult {
    data class UpToDate(val latestVersionName: String?) : AppUpdateCheckResult
    data class Available(val release: AppUpdateRelease) : AppUpdateCheckResult
}

/**
 * Validated metadata from the operator-controlled public GitHub Release.
 *
 * The download address stays memory-only. It is deliberately redacted from [toString] so a future
 * GitHub redirect containing opaque query parameters cannot leak through diagnostics.
 */
internal data class AppUpdateRelease(
    val versionName: String,
    val versionCode: Long,
    val assetName: String,
    val assetSizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
) {
    init {
        require(versionName.isNotBlank())
        require(versionCode > 0L)
        require(assetName.isNotBlank())
        require(assetSizeBytes in 1..MAX_APK_SIZE_BYTES)
        require(SHA256.matches(sha256))
    }

    override fun toString(): String =
        "AppUpdateRelease(versionName=$versionName, versionCode=$versionCode, " +
            "assetName=$assetName, assetSizeBytes=$assetSizeBytes, sha256=<redacted>, " +
            "downloadUrl=<redacted>)"

    companion object {
        const val MAX_APK_SIZE_BYTES = 200L * 1_024L * 1_024L
        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}

internal data class VerifiedAppUpdate(
    val release: AppUpdateRelease,
    val apkFile: File,
) {
    override fun toString(): String =
        "VerifiedAppUpdate(release=$release, apkFile=<redacted>)"
}

internal sealed interface AppUpdateInstallResult {
    data object InstallerOpened : AppUpdateInstallResult
    data object UnknownSourcesPermissionOpened : AppUpdateInstallResult
}

