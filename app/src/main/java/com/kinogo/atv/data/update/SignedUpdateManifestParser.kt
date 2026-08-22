package com.kinogo.atv.data.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.Locale

/**
 * Parser for a detached envelope signed by the same identity that signs the installed APK.
 *
 * Envelope v1 contains an exact base64 payload and a signature over the decoded UTF-8 payload.
 * Signing the bytes instead of a reconstructed JSON object removes canonicalisation ambiguity.
 */
internal object SignedUpdateManifestParser {
    fun parse(
        body: String,
        currentVersionCode: Long,
        trustedPublicKeys: Collection<PublicKey>,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L,
    ): AppUpdateCheckResult {
        require(body.length <= MAX_ENVELOPE_CHARS) { "Update manifest is too large" }
        require(trustedPublicKeys.isNotEmpty()) { "Installed update identity is unavailable" }
        require(nowEpochSeconds > 0L) { "Current time is invalid" }
        val envelope = JsonParser.parseString(body).asJsonObject
        require(envelope.keySet() == ENVELOPE_FIELDS) { "Update manifest envelope is invalid" }
        require(envelope.requiredLong("schema") == SCHEMA_VERSION) {
            "Update manifest schema is unsupported"
        }
        val payloadBytes = envelope.requiredBase64("payload", MAX_PAYLOAD_BYTES)
        val signatureBytes = envelope.requiredBase64("signature", MAX_SIGNATURE_BYTES)
        require(payloadBytes.isNotEmpty()) { "Update manifest payload is empty" }
        require(
            trustedPublicKeys.any { key -> verify(key, payloadBytes, signatureBytes) },
        ) { "Update manifest signature is invalid" }

        val payload = payloadBytes.toString(Charsets.UTF_8)
        require(payload.toByteArray(Charsets.UTF_8).contentEquals(payloadBytes)) {
            "Update manifest payload is not UTF-8"
        }
        val releaseObject = JsonParser.parseString(payload).asJsonObject
        require(releaseObject.keySet() == PAYLOAD_FIELDS) { "Update manifest payload is invalid" }
        val versionName = releaseObject.requiredString("versionName")
        require(VERSION_NAME.matches(versionName)) { "Update version name is invalid" }
        val versionCode = releaseObject.requiredLong("versionCode")
        require(versionCode > 0L) { "Update version code is invalid" }
        val assetName = releaseObject.requiredString("assetName")
        require(assetName == "KinogoATV-$versionName-code$versionCode.apk") {
            "Update asset name is invalid"
        }
        val assetSizeBytes = releaseObject.requiredLong("assetSizeBytes")
        val sha256 = releaseObject.requiredString("sha256").lowercase(Locale.ROOT)
        val issuedAtEpochSeconds = releaseObject.requiredLong("issuedAtEpochSeconds")
        val expiresAtEpochSeconds = releaseObject.requiredLong("expiresAtEpochSeconds")
        require(issuedAtEpochSeconds <= nowEpochSeconds + MAX_CLOCK_SKEW_SECONDS) {
            "Update manifest is not valid yet"
        }
        require(expiresAtEpochSeconds > nowEpochSeconds) { "Update manifest has expired" }
        require(expiresAtEpochSeconds > issuedAtEpochSeconds) { "Update manifest lifetime is invalid" }
        require(expiresAtEpochSeconds - issuedAtEpochSeconds <= MAX_MANIFEST_LIFETIME_SECONDS) {
            "Update manifest lifetime is too long"
        }
        val urls = releaseObject["downloadUrls"]
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?: throw IllegalArgumentException("Update download list is missing")
        require(urls.size() in 1..MAX_DOWNLOAD_URLS) { "Update download list is invalid" }
        val downloadUrls = urls.map { item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                "Update download URL is invalid"
            }
            item.asString.also(SignedUpdateUrlPolicy::requireSafeDownloadUrl)
        }
        require(downloadUrls.distinct().size == downloadUrls.size) {
            "Update download list contains duplicates"
        }
        val release = AppUpdateRelease(
            versionName = versionName,
            versionCode = versionCode,
            assetName = assetName,
            assetSizeBytes = assetSizeBytes,
            sha256 = sha256,
            downloadUrl = downloadUrls.first(),
            fallbackDownloadUrls = downloadUrls.drop(1),
            channel = AppUpdateReleaseChannel.SIGNED_MANIFEST,
            validUntilEpochSeconds = expiresAtEpochSeconds,
        )
        return if (release.versionCode > currentVersionCode) {
            AppUpdateCheckResult.Available(release)
        } else {
            AppUpdateCheckResult.UpToDate(release.versionName, release.versionCode)
        }
    }

    private fun verify(
        key: PublicKey,
        payload: ByteArray,
        signatureBytes: ByteArray,
    ): Boolean = runCatching {
        val algorithm = when (key.algorithm.uppercase(Locale.ROOT)) {
            "RSA" -> "SHA256withRSA"
            "EC", "ECDSA" -> "SHA256withECDSA"
            else -> return false
        }
        Signature.getInstance(algorithm).run {
            initVerify(key)
            update(payload)
            verify(signatureBytes)
        }
    }.getOrDefault(false)

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        require(value?.isString == true) { "Update manifest field is invalid" }
        return value.asString.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Update manifest field is empty")
    }

    private fun JsonObject.requiredLong(name: String): Long {
        val value = get(name)?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        require(value?.isNumber == true) { "Update manifest field is invalid" }
        val raw = value.asString
        require(UNSIGNED_INTEGER.matches(raw)) { "Update manifest field is invalid" }
        return raw.toLongOrNull()
            ?: throw IllegalArgumentException("Update manifest field is invalid")
    }

    private fun JsonObject.requiredBase64(name: String, maxBytes: Int): ByteArray {
        val encoded = requiredString(name)
        require(encoded.length <= maxBytes * 2) { "Update manifest field is too large" }
        return runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Update manifest field is invalid") }
            .also { require(it.size <= maxBytes) { "Update manifest field is too large" } }
    }

    private const val SCHEMA_VERSION = 1L
    private const val MAX_ENVELOPE_CHARS = 256 * 1_024
    private const val MAX_PAYLOAD_BYTES = 64 * 1_024
    private const val MAX_SIGNATURE_BYTES = 2 * 1_024
    private const val MAX_CLOCK_SKEW_SECONDS = 24 * 60 * 60L
    private const val MAX_MANIFEST_LIFETIME_SECONDS = 90 * 24 * 60 * 60L
    private const val MAX_DOWNLOAD_URLS = 1 + AppUpdateRelease.MAX_FALLBACK_DOWNLOAD_URLS
    private val VERSION_NAME = Regex("\\d+\\.\\d+\\.\\d+(?:-[A-Za-z0-9.-]+)?")
    private val UNSIGNED_INTEGER = Regex("0|[1-9]\\d*")
    private val ENVELOPE_FIELDS = setOf("schema", "payload", "signature")
    private val PAYLOAD_FIELDS = setOf(
        "versionName",
        "versionCode",
        "assetName",
        "assetSizeBytes",
        "sha256",
        "issuedAtEpochSeconds",
        "expiresAtEpochSeconds",
        "downloadUrls",
    )
}

internal object SignedUpdateUrlPolicy {
    fun requireSafeManifestUrl(rawUrl: String) {
        val uri = requireSafeHttpsDomainUrl(rawUrl)
        require(uri.rawQuery == null) { "Update manifest query is not allowed" }
    }

    fun requireSafeDownloadUrl(rawUrl: String) {
        requireSafeHttpsDomainUrl(rawUrl)
    }

    fun isSafeDownloadUrl(rawUrl: String): Boolean = runCatching {
        requireSafeDownloadUrl(rawUrl)
        true
    }.getOrDefault(false)

    private fun requireSafeHttpsDomainUrl(rawUrl: String): URI {
        require(rawUrl.length in 1..MAX_URL_CHARS) { "Update URL is invalid" }
        require(rawUrl.none { it.isISOControl() || it == '\\' }) { "Update URL is invalid" }
        val uri = runCatching { URI.create(rawUrl) }
            .getOrElse { throw IllegalArgumentException("Update URL is invalid") }
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.rawUserInfo == null &&
                uri.port == -1 &&
                uri.rawFragment == null &&
                uri.rawPath?.startsWith('/') == true &&
                host.contains('.') &&
                host != "localhost" &&
                !IPV4_LITERAL.matches(host) &&
                ':' !in host,
        ) { "Update URL is not an allowed public HTTPS address" }
        return uri
    }

    private const val MAX_URL_CHARS = 8 * 1_024
    private val IPV4_LITERAL = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
}
