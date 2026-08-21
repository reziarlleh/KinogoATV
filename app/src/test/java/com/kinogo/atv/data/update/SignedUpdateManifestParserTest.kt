package com.kinogo.atv.data.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedUpdateManifestParserTest {
    private val signer = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()

    @Test
    fun `valid signer-authenticated manifest exposes independent mirrors`() {
        val result = SignedUpdateManifestParser.parse(
            body = envelope(payload(), signer),
            currentVersionCode = 14,
            trustedPublicKeys = listOf(signer.public),
            nowEpochSeconds = NOW,
        )

        assertTrue(result is AppUpdateCheckResult.Available)
        val release = (result as AppUpdateCheckResult.Available).release
        assertEquals(AppUpdateReleaseChannel.SIGNED_MANIFEST, release.channel)
        assertEquals(15L, release.versionCode)
        assertEquals(
            listOf(
                "https://updates.example.org/KinogoATV-0.5.1-code15.apk",
                "https://cdn.example.net/KinogoATV-0.5.1-code15.apk",
            ),
            release.allDownloadUrls,
        )
        assertTrue(release.toString().contains("downloadUrls=<redacted:2>"))
        assertTrue(!release.toString().contains("example.org"))
    }

    @Test
    fun `installed version is reported as up to date`() {
        assertEquals(
            AppUpdateCheckResult.UpToDate("0.5.1", 15),
            SignedUpdateManifestParser.parse(
                body = envelope(payload(), signer),
                currentVersionCode = 15,
                trustedPublicKeys = listOf(signer.public),
                nowEpochSeconds = NOW,
            ),
        )
    }

    @Test
    fun `ECDSA APK signing identity is supported`() {
        val ecSigner = KeyPairGenerator.getInstance("EC")
            .apply { initialize(256) }
            .generateKeyPair()

        assertTrue(
            SignedUpdateManifestParser.parse(
                body = envelope(payload(), ecSigner),
                currentVersionCode = 14,
                trustedPublicKeys = listOf(ecSigner.public),
                nowEpochSeconds = NOW,
            ) is AppUpdateCheckResult.Available,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `payload changed after signing is rejected`() {
        val signed = JsonParser.parseString(envelope(payload(), signer)).asJsonObject
        val changedPayload = Base64.getDecoder().decode(signed["payload"].asString).also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        signed.addProperty("payload", Base64.getEncoder().encodeToString(changedPayload))
        SignedUpdateManifestParser.parse(
            body = signed.toString(),
            currentVersionCode = 14,
            trustedPublicKeys = listOf(signer.public),
            nowEpochSeconds = NOW,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `different signing identity is rejected`() {
        val other = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
        SignedUpdateManifestParser.parse(
            body = envelope(payload(), signer),
            currentVersionCode = 14,
            trustedPublicKeys = listOf(other.public),
            nowEpochSeconds = NOW,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `signed private network URL is still rejected`() {
        val unsafe = payload().replace(
            "https://updates.example.org/KinogoATV-0.5.1-code15.apk",
            "https://127.0.0.1/KinogoATV-0.5.1-code15.apk",
        )
        SignedUpdateManifestParser.parse(
            body = envelope(unsafe, signer),
            currentVersionCode = 14,
            trustedPublicKeys = listOf(signer.public),
            nowEpochSeconds = NOW,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `asset identity must exactly match version`() {
        val mismatched = payload().replace("code15.apk", "code16.apk")
        SignedUpdateManifestParser.parse(
            body = envelope(mismatched, signer),
            currentVersionCode = 14,
            trustedPublicKeys = listOf(signer.public),
            nowEpochSeconds = NOW,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `expired signed manifest cannot be replayed`() {
        SignedUpdateManifestParser.parse(
            body = envelope(payload(), signer),
            currentVersionCode = 14,
            trustedPublicKeys = listOf(signer.public),
            nowEpochSeconds = NOW + 86_400,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unbounded manifest lifetime is rejected`() {
        val longLived = payload().replace(
            (NOW + 86_400).toString(),
            (NOW + 100L * 24L * 60L * 60L).toString(),
        )
        SignedUpdateManifestParser.parse(
            body = envelope(longLived, signer),
            currentVersionCode = 14,
            trustedPublicKeys = listOf(signer.public),
            nowEpochSeconds = NOW,
        )
    }

    @Test
    fun `manifest endpoint policy allows only clean public https domain URLs`() {
        assertEquals(
            listOf(
                "https://reziarlleh.github.io/KinogoATV/update/manifest.json",
                "https://cdn.jsdelivr.net/gh/reziarlleh/KinogoATV@main/update/manifest.json",
            ),
            DefaultAppUpdateClientFactory.DEFAULT_SIGNED_MANIFEST_URLS,
        )
        DefaultAppUpdateClientFactory.DEFAULT_SIGNED_MANIFEST_URLS.forEach(
            SignedUpdateUrlPolicy::requireSafeManifestUrl,
        )
        listOf(
            "http://updates.example.org/manifest.json",
            "https://localhost/manifest.json",
            "https://192.168.1.2/manifest.json",
            "https://updates.example.org/manifest.json?token=secret",
            "https://user@updates.example.org/manifest.json",
        ).forEach { url ->
            assertTrue(runCatching { SignedUpdateUrlPolicy.requireSafeManifestUrl(url) }.isFailure)
        }
    }

    private fun payload(): String =
        """
        {
          "versionName": "0.5.1",
          "versionCode": 15,
          "assetName": "KinogoATV-0.5.1-code15.apk",
          "assetSizeBytes": 123456,
          "sha256": "${"a".repeat(64)}",
          "issuedAtEpochSeconds": ${NOW - 100},
          "expiresAtEpochSeconds": ${NOW + 86_400},
          "downloadUrls": [
            "https://updates.example.org/KinogoATV-0.5.1-code15.apk",
            "https://cdn.example.net/KinogoATV-0.5.1-code15.apk"
          ]
        }
        """.trimIndent()

    private fun envelope(payload: String, keyPair: KeyPair): String {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val algorithm = when (keyPair.private.algorithm) {
            "EC" -> "SHA256withECDSA"
            else -> "SHA256withRSA"
        }
        val signature = Signature.getInstance(algorithm).run {
            initSign(keyPair.private)
            update(payloadBytes)
            sign()
        }
        return JsonObject().apply {
            addProperty("schema", 1)
            addProperty("payload", Base64.getEncoder().encodeToString(payloadBytes))
            addProperty("signature", Base64.getEncoder().encodeToString(signature))
        }.toString()
    }

    private companion object {
        const val NOW = 2_000_000_000L
    }
}
