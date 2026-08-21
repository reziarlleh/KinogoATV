package com.kinogo.atv.data.update

import android.content.Context
import android.content.pm.PackageManager
import com.kinogo.atv.data.network.ResilientPublicDns
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** GitHub-independent update channel backed by one or more APK-signer-authenticated manifests. */
internal class SignedManifestUpdateClient internal constructor(
    private val client: OkHttpClient,
    manifestUrls: List<String>,
    private val trustedPublicKeys: Collection<PublicKey>,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : AppUpdateClient {
    constructor(
        context: Context,
        manifestUrls: List<String>,
    ) : this(
        client = defaultClient(),
        manifestUrls = manifestUrls,
        trustedPublicKeys = installedSigningPublicKeys(context),
    )

    private val manifestUrls = manifestUrls.map(String::trim).distinct()

    init {
        require(this.manifestUrls.isNotEmpty()) { "No independent update manifests configured" }
        require(this.manifestUrls.size <= MAX_MANIFEST_URLS) {
            "Too many independent update manifests configured"
        }
        this.manifestUrls.forEach(SignedUpdateUrlPolicy::requireSafeManifestUrl)
        require(trustedPublicKeys.isNotEmpty()) { "Installed update identity is unavailable" }
    }

    override val channel: AppUpdateReleaseChannel = AppUpdateReleaseChannel.SIGNED_MANIFEST

    override suspend fun check(currentVersionCode: Long): AppUpdateCheckResult =
        withContext(Dispatchers.IO) {
            val releases = coroutineScope {
                manifestUrls.map { manifestUrl ->
                    async {
                        try {
                            fetchVerifiedRelease(manifestUrl)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // Another independently signed endpoint can still provide the release.
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            require(releases.isNotEmpty()) { "Independent update manifests are unavailable" }
            val highestCode = releases.maxOf(AppUpdateRelease::versionCode)
            val highest = releases.filter { it.versionCode == highestCode }
            val identity = highest.first().identity()
            require(highest.all { it.identity() == identity }) {
                "Independent update manifests disagree"
            }
            val urls = highest.flatMap(AppUpdateRelease::allDownloadUrls).distinct()
            require(urls.size <= MAX_DOWNLOAD_URLS) {
                "Independent update manifests provide too many download locations"
            }
            val release = highest.first().copy(
                downloadUrl = urls.first(),
                fallbackDownloadUrls = urls.drop(1),
                validUntilEpochSeconds = highest
                    .mapNotNull(AppUpdateRelease::validUntilEpochSeconds)
                    .minOrNull(),
            )
            if (release.versionCode > currentVersionCode) {
                AppUpdateCheckResult.Available(release)
            } else {
                AppUpdateCheckResult.UpToDate(release.versionName, release.versionCode)
            }
        }

    override suspend fun download(
        destinationDirectory: File,
        release: AppUpdateRelease,
    ): File = withContext(Dispatchers.IO) {
        require(release.channel == channel) { "Wrong update channel" }
        require(release.validUntilEpochSeconds?.let { nowEpochSeconds() < it } == true) {
            "Independent update metadata has expired"
        }
        require(destinationDirectory.mkdirs() || destinationDirectory.isDirectory) {
            "Update cache is unavailable"
        }
        val destination = File(destinationDirectory, PENDING_APK_NAME)
        release.allDownloadUrls.forEach { rawUrl ->
            destination.delete()
            val completed = try {
                downloadValidated(rawUrl, release, destination)
                true
            } catch (error: CancellationException) {
                destination.delete()
                throw error
            } catch (_: Exception) {
                false
            }
            if (completed) return@withContext destination
        }
        destination.delete()
        throw IllegalStateException("Independent update download is unavailable")
    }

    private fun fetchVerifiedRelease(manifestUrl: String): AppUpdateRelease {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(MANIFEST_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        call.execute().use { response ->
            require(response.isSuccessful) { "Independent update manifest request failed" }
            val body = response.body ?: throw IllegalStateException("Update manifest is empty")
            val bytes = body.byteStream().use { it.readSignedManifestLimited(MAX_MANIFEST_BYTES) }
            val result = SignedUpdateManifestParser.parse(
                body = bytes.toString(Charsets.UTF_8),
                currentVersionCode = 0L,
                trustedPublicKeys = trustedPublicKeys,
                nowEpochSeconds = nowEpochSeconds(),
            )
            return (result as AppUpdateCheckResult.Available).release
        }
    }

    private fun downloadValidated(
        rawUrl: String,
        release: AppUpdateRelease,
        destination: File,
    ) {
        require(rawUrl in release.allDownloadUrls) { "Update download URL is not signed" }
        var uri = URI.create(rawUrl)
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            SignedUpdateUrlPolicy.requireSafeDownloadUrl(uri.toString())
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
                        ?: throw IllegalStateException("Update redirect is invalid")
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
        require(response.isSuccessful) { "Update download request failed" }
        val body = response.body ?: throw IllegalStateException("Update download is empty")
        val declaredLength = body.contentLength()
        require(declaredLength < 0L || declaredLength == release.assetSizeBytes) {
            "Update size does not match signed metadata"
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
                        "Update exceeds signed metadata"
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
        require(actualSha256 == release.sha256) { "Update checksum does not match signed metadata" }
    }

    private data class ReleaseIdentity(
        val versionName: String,
        val versionCode: Long,
        val assetName: String,
        val assetSizeBytes: Long,
        val sha256: String,
    )

    private fun AppUpdateRelease.identity() = ReleaseIdentity(
        versionName,
        versionCode,
        assetName,
        assetSizeBytes,
        sha256,
    )

    companion object {
        private const val USER_AGENT = "KinogoATV/0.5 (Android TV; signed update client)"
        private const val PENDING_APK_NAME = "KinogoATV-pending-update.apk"
        private const val MAX_MANIFEST_BYTES = 256 * 1_024
        private const val MAX_MANIFEST_URLS = 4
        private const val MANIFEST_CALL_TIMEOUT_SECONDS = 20L
        private const val MAX_DOWNLOAD_URLS = 1 + AppUpdateRelease.MAX_FALLBACK_DOWNLOAD_URLS
        private const val MAX_REDIRECTS = 4
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .dns(ResilientPublicDns())
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()

        private fun installedSigningPublicKeys(context: Context): List<PublicKey> {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val certificateFactory = CertificateFactory.getInstance("X.509")
            return info.signingInfo
                ?.apkContentsSigners
                ?.mapNotNull { signer ->
                    runCatching {
                        certificateFactory.generateCertificate(
                            signer.toByteArray().inputStream(),
                        ) as X509Certificate
                    }.getOrNull()?.publicKey
                }
                .orEmpty()
        }
    }
}

private fun java.io.InputStream.readSignedManifestLimited(maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 32 * 1_024))
    val buffer = ByteArray(8 * 1_024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "Update manifest is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
