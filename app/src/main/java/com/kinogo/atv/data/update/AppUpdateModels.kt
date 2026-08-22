package com.kinogo.atv.data.update

import java.io.File

internal sealed interface AppUpdateCheckResult {
    data class UpToDate(
        val latestVersionName: String?,
        val latestVersionCode: Long? = null,
    ) : AppUpdateCheckResult
    data class Available(val release: AppUpdateRelease) : AppUpdateCheckResult
}

internal enum class AppUpdateReleaseChannel {
    GITHUB_RELEASE,
    SIGNED_MANIFEST,
}

/**
 * Validated metadata from either the exact public GitHub Release contract or an
 * APK-signer-authenticated independent manifest.
 *
 * The download address stays memory-only. It is deliberately redacted from [toString] so a future
 * redirect containing opaque query parameters cannot leak through diagnostics.
 */
internal data class AppUpdateRelease(
    val versionName: String,
    val versionCode: Long,
    val assetName: String,
    val assetSizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val fallbackDownloadUrls: List<String> = emptyList(),
    val channel: AppUpdateReleaseChannel = AppUpdateReleaseChannel.GITHUB_RELEASE,
    val validUntilEpochSeconds: Long? = null,
) {
    init {
        require(versionName.isNotBlank())
        require(versionCode > 0L)
        require(assetName.isNotBlank())
        require(assetSizeBytes in 1..MAX_APK_SIZE_BYTES)
        require(SHA256.matches(sha256))
        require(downloadUrl.isNotBlank())
        require(fallbackDownloadUrls.size <= MAX_FALLBACK_DOWNLOAD_URLS)
        require(fallbackDownloadUrls.all(String::isNotBlank))
        require(allDownloadUrls.distinct().size == allDownloadUrls.size)
        require(validUntilEpochSeconds == null || validUntilEpochSeconds > 0L)
    }

    val allDownloadUrls: List<String>
        get() = listOf(downloadUrl) + fallbackDownloadUrls

    override fun toString(): String =
        "AppUpdateRelease(versionName=$versionName, versionCode=$versionCode, " +
            "assetName=$assetName, assetSizeBytes=$assetSizeBytes, sha256=<redacted>, " +
            "downloadUrls=<redacted:${allDownloadUrls.size}>, channel=$channel, " +
            "validUntilEpochSeconds=$validUntilEpochSeconds)"

    companion object {
        const val MAX_APK_SIZE_BYTES = 200L * 1_024L * 1_024L
        const val MAX_FALLBACK_DOWNLOAD_URLS = 3
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
