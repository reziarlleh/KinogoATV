package com.kinogo.atv.data.mirror

import com.google.gson.JsonParser
import com.kinogo.atv.data.network.ResilientPublicDns
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

data class MirrorBootstrapManifest(
    val generatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val origins: List<String>,
)

sealed interface MirrorBootstrapResult {
    data class Applied(
        val manifest: MirrorBootstrapManifest,
        val candidates: List<MirrorEntry>,
    ) : MirrorBootstrapResult

    data class Unavailable(val message: String) : MirrorBootstrapResult
}

internal data class MirrorBootstrapHttpResponse(
    val statusCode: Int,
    val contentType: String?,
    val body: String,
)

internal fun interface MirrorBootstrapTransport {
    suspend fun get(): MirrorBootstrapHttpResponse
}

/**
 * Loads operator-controlled bootstrap candidates without granting them trust.
 *
 * The manifest merely widens the bounded probe queue when old built-in redirectors disappear.
 * Every returned origin remains QUARANTINED until the existing health checker confirms public DNS,
 * HTTPS and the Kinogo HTML fingerprint.
 */
class MirrorBootstrapClient internal constructor(
    private val transport: MirrorBootstrapTransport,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    constructor(
        dns: Dns = ResilientPublicDns(),
        nowEpochMs: () -> Long = System::currentTimeMillis,
    ) : this(
        transport = GitHubRawMirrorBootstrapTransport(dns),
        nowEpochMs = nowEpochMs,
    )

    suspend fun refresh(registry: MirrorRegistry): MirrorBootstrapResult {
        return try {
            val response = transport.get()
            if (response.statusCode != 200) {
                return MirrorBootstrapResult.Unavailable(
                    if (response.statusCode == 404) {
                        "Удалённый список зеркал ещё не опубликован"
                    } else {
                        "Удалённый список зеркал вернул HTTP ${response.statusCode}"
                    },
                )
            }
            val mediaType = response.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (mediaType != null && mediaType !in ALLOWED_CONTENT_TYPES) {
                return MirrorBootstrapResult.Unavailable("Удалённый список имеет неверный тип данных")
            }
            val manifest = MirrorBootstrapManifestParser.parse(response.body, nowEpochMs())
            val update = registry.updateDiscoveryCandidates(manifest.origins)
            if (update.rejected.isNotEmpty()) {
                return MirrorBootstrapResult.Unavailable("Удалённый список содержит недопустимый адрес")
            }
            MirrorBootstrapResult.Applied(manifest, update.accepted)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MirrorBootstrapResult.Unavailable("Не удалось обновить список зеркал")
        }
    }

    private companion object {
        val ALLOWED_CONTENT_TYPES = setOf("application/json", "text/plain")
    }
}

internal object MirrorBootstrapManifestParser {
    fun parse(rawJson: String, nowEpochMs: Long): MirrorBootstrapManifest {
        require(rawJson.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST_BYTES) {
            "Mirror manifest is too large"
        }
        val root = JsonParser.parseString(rawJson).asJsonObject
        require(root.keySet() == REQUIRED_FIELDS) { "Unexpected mirror manifest schema" }
        require(root.get("schemaVersion").asInt == SCHEMA_VERSION) {
            "Unsupported mirror manifest schema"
        }
        val generatedAt = root.get("generatedAt").asString.parseInstant()
        val expiresAt = root.get("expiresAt").asString.parseInstant()
        require(generatedAt >= 0L && expiresAt > generatedAt) { "Invalid mirror manifest lifetime" }
        require(generatedAt <= nowEpochMs + MAX_CLOCK_SKEW_MS) { "Mirror manifest is from the future" }
        require(expiresAt > nowEpochMs) { "Mirror manifest has expired" }
        require(expiresAt - generatedAt <= MAX_VALIDITY_MS) { "Mirror manifest validity is too long" }

        val rawOrigins = root.getAsJsonArray("origins")
        require(rawOrigins.size() in 1..MAX_ORIGINS) { "Invalid mirror candidate count" }
        val origins = rawOrigins.map { element ->
            require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                "Mirror candidate must be a string"
            }
            MirrorUrlNormalizer.normalize(element.asString)
        }.distinct()
        require(origins.size == rawOrigins.size()) { "Duplicate mirror candidate" }

        return MirrorBootstrapManifest(
            generatedAtEpochMs = generatedAt,
            expiresAtEpochMs = expiresAt,
            origins = origins,
        )
    }

    private fun String.parseInstant(): Long = try {
        Instant.parse(this).toEpochMilli()
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("Invalid mirror manifest timestamp", error)
    }

    private val REQUIRED_FIELDS = setOf("schemaVersion", "generatedAt", "expiresAt", "origins")
    private const val SCHEMA_VERSION = 1
    private const val MAX_ORIGINS = 24
    private const val MAX_MANIFEST_BYTES = 32 * 1_024
    private const val MAX_CLOCK_SKEW_MS = 24 * 60 * 60 * 1_000L
    private const val MAX_VALIDITY_MS = 120L * 24 * 60 * 60 * 1_000L
}

private class GitHubRawMirrorBootstrapTransport(
    dns: Dns,
) : MirrorBootstrapTransport {
    private val client = OkHttpClient.Builder()
        .dns(dns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()
    private val manifestUri = URI.create(MANIFEST_URL)
    private val validatedDns = dns

    override suspend fun get(): MirrorBootstrapHttpResponse = withContext(Dispatchers.IO) {
        check(manifestUri.host == MANIFEST_HOST)
        NetworkDestinationValidator.validateHttpsPublic(manifestUri, validatedDns)
        val request = Request.Builder()
            .url(MANIFEST_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "KinogoATV/0.5 (Android TV; mirror bootstrap)")
            .build()
        client.newCall(request).execute().use { response ->
            require(response.code !in 300..399) { "Mirror manifest redirect is not allowed" }
            val responseBody = response.body
            val declaredLength = responseBody?.contentLength() ?: -1L
            require(declaredLength < 0L || declaredLength <= MAX_RESPONSE_BYTES) {
                "Mirror manifest is too large"
            }
            val bytes = responseBody?.byteStream()?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4 * 1_024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    require(total <= MAX_RESPONSE_BYTES) { "Mirror manifest is too large" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: ByteArray(0)
            MirrorBootstrapHttpResponse(
                statusCode = response.code,
                contentType = responseBody?.contentType()?.toString(),
                body = bytes.toString(Charsets.UTF_8),
            )
        }
    }

    private companion object {
        const val MANIFEST_HOST = "raw.githubusercontent.com"
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/reziarlleh/KinogoATV/main/config/mirrors.json"
        const val MAX_RESPONSE_BYTES = 32 * 1_024
    }
}
