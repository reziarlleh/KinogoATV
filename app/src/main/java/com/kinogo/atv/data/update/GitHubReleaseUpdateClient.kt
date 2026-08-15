package com.kinogo.atv.data.update

import com.kinogo.atv.data.network.ResilientPublicDns
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class GitHubReleaseUpdateClient(
    private val client: OkHttpClient = defaultClient(),
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {
    suspend fun check(currentVersionCode: Long): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext AppUpdateCheckResult.UpToDate(null)
            require(response.isSuccessful) { "Update server returned HTTP ${response.code}" }
            val body = response.body ?: throw IllegalStateException("Update response is empty")
            val bytes = body.byteStream().use { it.readLimited(MAX_RELEASE_DOCUMENT_BYTES) }
            GitHubReleaseParser.parse(bytes.toString(Charsets.UTF_8), currentVersionCode)
        }
    }

    suspend fun download(
        destinationDirectory: File,
        release: AppUpdateRelease,
    ): File = withContext(Dispatchers.IO) {
        require(destinationDirectory.mkdirs() || destinationDirectory.isDirectory) {
            "Update cache is unavailable"
        }
        val destination = File(destinationDirectory, PENDING_APK_NAME)
        destination.delete()
        try {
            downloadValidated(release, destination)
            destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    private fun downloadValidated(release: AppUpdateRelease, destination: File) {
        var uri = URI.create(release.downloadUrl)
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            require(isAllowedUpdateDownloadUri(uri, release, initial = redirectIndex == 0)) {
                "Update download destination is not allowed"
            }
            val request = Request.Builder()
                .url(uri.toString())
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code in REDIRECT_CODES) {
                    require(redirectIndex < MAX_REDIRECTS) { "Too many update redirects" }
                    val location = response.header("Location")
                        ?: throw IllegalStateException("Update redirect has no destination")
                    uri = uri.resolve(location)
                    return@repeat
                }
                writeVerifiedResponse(response, destination, release)
                return
            }
        }
        error("Update download did not reach an APK")
    }

    private fun writeVerifiedResponse(
        response: Response,
        destination: File,
        release: AppUpdateRelease,
    ) {
        require(response.isSuccessful) { "Update download returned HTTP ${response.code}" }
        val body = response.body ?: throw IllegalStateException("Update download is empty")
        val declaredLength = body.contentLength()
        require(declaredLength < 0L || declaredLength == release.assetSizeBytes) {
            "Update size does not match release metadata"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        body.byteStream().use { input ->
            FileOutputStream(destination).buffered().use { output ->
                val buffer = ByteArray(32 * 1_024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= release.assetSizeBytes) {
                        "Update download exceeds release metadata"
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        require(total == release.assetSizeBytes) { "Update download is incomplete" }
        val actualSha256 = digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        require(actualSha256 == release.sha256) { "Update checksum does not match GitHub Release" }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/reziarlleh/KinogoATV/releases/latest"
        private const val USER_AGENT = "KinogoATV/0.5 (Android TV; update client)"
        private const val PENDING_APK_NAME = "KinogoATV-pending-update.apk"
        private const val MAX_RELEASE_DOCUMENT_BYTES = 512 * 1_024
        private const val MAX_REDIRECTS = 4
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val DOWNLOAD_HOSTS = setOf(
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com",
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .dns(ResilientPublicDns())
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()

        internal fun isAllowedUpdateDownloadUri(
            uri: URI,
            release: AppUpdateRelease,
            initial: Boolean,
        ): Boolean {
            val host = uri.host?.lowercase() ?: return false
            if (
                uri.scheme != "https" ||
                uri.rawUserInfo != null ||
                uri.port != -1 ||
                uri.rawFragment != null ||
                host !in DOWNLOAD_HOSTS
            ) {
                return false
            }
            if (initial) {
                return GitHubReleaseParser.isExpectedReleaseDownloadUrl(
                    rawUrl = uri.toString(),
                    tag = "v${release.versionName}",
                    assetName = release.assetName,
                )
            }
            // GitHub's signed CDN destination can carry opaque query parameters. It is admitted
            // only after a redirect from the already validated release URL and is never logged.
            return host != "github.com" || GitHubReleaseParser.isExpectedReleaseDownloadUrl(
                rawUrl = uri.toString(),
                tag = "v${release.versionName}",
                assetName = release.assetName,
            )
        }
    }
}

private fun java.io.InputStream.readLimited(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 32 * 1_024))
    val buffer = ByteArray(8 * 1_024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "Update response is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
