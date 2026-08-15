package com.kinogo.atv.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

internal data class ApkArchiveMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

internal object ApkUpdatePolicy {
    fun validate(
        installed: ApkArchiveMetadata,
        candidate: ApkArchiveMetadata,
        release: AppUpdateRelease,
    ) {
        require(candidate.packageName == installed.packageName) {
            "Update package name does not match KinogoATV"
        }
        require(candidate.versionCode == release.versionCode) {
            "Update version code does not match release metadata"
        }
        require(candidate.versionName == release.versionName) {
            "Update version name does not match release metadata"
        }
        require(candidate.versionCode > installed.versionCode) {
            "Update version is not newer than the installed application"
        }
        require(installed.signerSha256.isNotEmpty()) { "Installed signing identity is unavailable" }
        require(candidate.signerSha256 == installed.signerSha256) {
            "Update signing certificate does not match the installed application"
        }
    }
}

internal class ApkUpdateVerifier(private val context: Context) {
    fun verify(file: File, release: AppUpdateRelease) {
        require(file.isFile && file.length() == release.assetSizeBytes) {
            "Downloaded update file is incomplete"
        }
        val packageManager = context.packageManager
        val installedInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val candidateInfo = packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        ) ?: throw IllegalArgumentException("Downloaded file is not an Android APK")
        ApkUpdatePolicy.validate(
            installed = installedInfo.toArchiveMetadata(),
            candidate = candidateInfo.toArchiveMetadata(),
            release = release,
        )
    }
}

private fun PackageInfo.toArchiveMetadata(): ApkArchiveMetadata = ApkArchiveMetadata(
    packageName = packageName,
    versionName = versionName.orEmpty(),
    versionCode = longVersionCode,
    signerSha256 = signingInfo
        ?.apkContentsSigners
        ?.map { signature -> signature.toByteArray().sha256() }
        ?.toSet()
        .orEmpty(),
)

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

