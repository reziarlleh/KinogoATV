package com.kinogo.atv.data.update

import com.google.gson.JsonParser
import java.net.URI

internal object GitHubReleaseParser {
    private const val EXPECTED_OWNER = "reziarlleh"
    private const val EXPECTED_REPOSITORY = "KinogoATV"
    private val assetNamePattern = Regex(
        "^KinogoATV-(\\d+\\.\\d+\\.\\d+)-code(\\d+)\\.apk$",
    )
    private val versionPattern = Regex("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)")

    fun parse(
        body: String,
        currentVersionCode: Long,
        currentVersionName: String,
    ): AppUpdateCheckResult {
        val installedVersion = versionParts(currentVersionName)
        require(body.length <= MAX_RELEASE_DOCUMENT_CHARS) { "Release response is too large" }
        val release = JsonParser.parseString(body).asJsonObject
        require(release["draft"]?.asBoolean == false) { "Draft release is not installable" }
        require(release["prerelease"]?.asBoolean == false) { "Prerelease is not installable" }

        val tag = release["tag_name"]?.asString.orEmpty()
        val assets = release["assets"]?.asJsonArray
            ?: throw IllegalArgumentException("Release has no assets")
        val candidates = assets.mapNotNull { element ->
            val asset = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = asset["name"]?.asString ?: return@mapNotNull null
            val match = assetNamePattern.matchEntire(name) ?: return@mapNotNull null
            val versionName = match.groupValues[1]
            val versionCode = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (tag != "v$versionName") return@mapNotNull null
            val size = asset["size"]?.asLong ?: return@mapNotNull null
            val digest = asset["digest"]?.asString
                ?.removePrefix("sha256:")
                ?.lowercase()
                ?: return@mapNotNull null
            val downloadUrl = asset["browser_download_url"]?.asString ?: return@mapNotNull null
            if (!isExpectedReleaseDownloadUrl(downloadUrl, tag, name)) return@mapNotNull null
            runCatching {
                AppUpdateRelease(
                    versionName = versionName,
                    versionCode = versionCode,
                    assetName = name,
                    assetSizeBytes = size,
                    sha256 = digest,
                    downloadUrl = downloadUrl,
                )
            }.getOrNull()
        }

        require(candidates.size == 1) { "Release must contain exactly one signed APK asset" }
        val candidate = candidates.single()
        val latestVersion = versionParts(candidate.versionName)
        val comparison = latestVersion.zip(installedVersion)
            .firstOrNull { (latest, installed) -> latest != installed }
            ?.let { (latest, installed) -> latest.compareTo(installed) }
            ?: 0
        return if (comparison > 0) {
            // Android also requires an increasing versionCode for an in-place update.
            require(candidate.versionCode > currentVersionCode) { "Release version code cannot update this installation" }
            AppUpdateCheckResult.Available(candidate)
        } else {
            AppUpdateCheckResult.UpToDate(candidate.versionName, candidate.versionCode)
        }
    }

    private fun versionParts(version: String): List<Long> {
        require(version.length <= 64 && versionPattern.matches(version)) { "Version must be x.y.z" }
        return version.split('.').map { part ->
            requireNotNull(part.toLongOrNull()) { "Version component is too large" }
        }
    }

    internal fun isExpectedReleaseDownloadUrl(
        rawUrl: String,
        tag: String,
        assetName: String,
    ): Boolean = runCatching {
        val uri = URI.create(rawUrl)
        uri.scheme == "https" &&
            uri.rawUserInfo == null &&
            uri.port == -1 &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawPath == "/$EXPECTED_OWNER/$EXPECTED_REPOSITORY/releases/download/$tag/$assetName"
    }.getOrDefault(false)

    private const val MAX_RELEASE_DOCUMENT_CHARS = 512 * 1_024
}
