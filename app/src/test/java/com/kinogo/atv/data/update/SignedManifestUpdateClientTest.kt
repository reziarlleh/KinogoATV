package com.kinogo.atv.data.update

import com.google.gson.JsonObject
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedManifestUpdateClientTest {
    private val signer = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()

    @Test
    fun `one unavailable endpoint does not hide another verified manifest`() = runTest {
        val apk = "signed apk bytes".toByteArray()
        val body = envelope(payload(apk))
        val client = client(
            interceptor = Interceptor { chain ->
                when (chain.request().url.host) {
                    "offline.example.org" -> response(chain, 503, "")
                    else -> response(chain, 200, body)
                }
            },
            manifestUrls = listOf(
                "https://offline.example.org/manifest-v1.json",
                "https://updates.example.org/manifest-v1.json",
            ),
        )

        val result = client.check(currentVersionCode = 14)

        assertTrue(result is AppUpdateCheckResult.Available)
        assertEquals(15L, (result as AppUpdateCheckResult.Available).release.versionCode)
    }

    @Test
    fun `download tries signed mirrors and verifies exact bytes`() = runTest {
        val apk = "signed apk bytes".toByteArray()
        val body = envelope(payload(apk))
        val client = client(
            interceptor = Interceptor { chain ->
                when (chain.request().url.host) {
                    "updates.example.org" -> response(chain, 503, "")
                    "cdn.example.net" -> response(chain, 200, apk)
                    else -> response(chain, 200, body)
                }
            },
            manifestUrls = listOf("https://manifest.example.com/manifest-v1.json"),
        )
        val release = (client.check(14) as AppUpdateCheckResult.Available).release
        val directory = Files.createTempDirectory("kinogo-update-test").toFile()
        try {
            val result = client.download(directory, release)
            assertArrayEquals(apk, result.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun client(
        interceptor: Interceptor,
        manifestUrls: List<String>,
    ) = SignedManifestUpdateClient(
        client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        manifestUrls = manifestUrls,
        trustedPublicKeys = listOf(signer.public),
        nowEpochSeconds = { NOW },
    )

    private fun response(
        chain: Interceptor.Chain,
        code: Int,
        body: String,
    ) = response(chain, code, body.toByteArray())

    private fun response(
        chain: Interceptor.Chain,
        code: Int,
        body: ByteArray,
    ): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code in 200..299) "OK" else "Unavailable")
        .body(body.toResponseBody("application/octet-stream".toMediaType()))
        .build()

    private fun payload(apk: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(apk)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        return """
            {
              "versionName": "0.5.1",
              "versionCode": 15,
              "assetName": "KinogoATV-0.5.1-code15.apk",
              "assetSizeBytes": ${apk.size},
              "sha256": "$digest",
              "issuedAtEpochSeconds": ${NOW - 100},
              "expiresAtEpochSeconds": ${NOW + 86_400},
              "downloadUrls": [
                "https://updates.example.org/KinogoATV-0.5.1-code15.apk",
                "https://cdn.example.net/KinogoATV-0.5.1-code15.apk"
              ]
            }
        """.trimIndent()
    }

    private fun envelope(payload: String): String {
        val bytes = payload.toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(signer.private)
            update(bytes)
            sign()
        }
        return JsonObject().apply {
            addProperty("schema", 1)
            addProperty("payload", Base64.getEncoder().encodeToString(bytes))
            addProperty("signature", Base64.getEncoder().encodeToString(signature))
        }.toString()
    }

    private companion object {
        const val NOW = 2_000_000_000L
    }
}
