package com.kinogo.atv.data.update

import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdateClientTest {
    @Test
    fun `check uses latest stable release without cookies cache or manifest requests`() = runBlocking {
        var requests = 0
        val client = GitHubReleaseUpdateClient(OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            assertEquals(GitHubReleaseUpdateClient.LATEST_RELEASE_URL, chain.request().url.toString())
            assertEquals("no-cache", chain.request().header("Cache-Control"))
            assertEquals(null, chain.request().header("Cookie"))
            response(chain.request(), 200, releaseJson())
        }.build())
        assertTrue(client.check(21, "0.6.1") is AppUpdateCheckResult.Available)
        assertEquals(AppUpdateCheckResult.UpToDate("0.6.2", 22), client.check(22, "0.6.2"))
        assertEquals(2, requests)
    }

    @Test
    fun `network error is not reported as up to date`() = runBlocking {
        val client = GitHubReleaseUpdateClient(OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), 503, "Unavailable")
        }.build())
        assertTrue(runCatching { client.check(21, "0.6.1") }.isFailure)
    }

    @Test
    fun `download checks exact bytes and deletes invalid partial file`() = runBlocking {
        val bytes = "test APK bytes".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val release = (GitHubReleaseParser.parse(releaseJson(sha, bytes.size), 21, "0.6.1")
            as AppUpdateCheckResult.Available).release
        val client = GitHubReleaseUpdateClient(OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), 200, bytes.toString(Charsets.UTF_8))
        }.build())
        val directory = Files.createTempDirectory("kinogo-update-test").toFile()
        try {
            assertTrue(client.download(directory, release).readBytes().contentEquals(bytes))
            assertTrue(runCatching { client.download(directory, release.copy(sha256 = "a".repeat(64))) }.isFailure)
            assertFalse(directory.resolve("KinogoATV-pending-update.apk").exists())
            assertTrue(runCatching { client.download(directory, release.copy(assetSizeBytes = bytes.size + 1L)) }.isFailure)
            assertFalse(directory.resolve("KinogoATV-pending-update.apk").exists())
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    private fun response(request: okhttp3.Request, code: Int, body: String): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
        .body(body.toResponseBody()).build()

    private fun releaseJson(sha: String = "a".repeat(64), size: Int = 123): String = """
        {"draft":false,"prerelease":false,"tag_name":"v0.6.2","assets":[{
        "name":"KinogoATV-0.6.2-code22.apk","size":$size,"digest":"sha256:$sha",
        "browser_download_url":"https://github.com/reziarlleh/KinogoATV/releases/download/v0.6.2/KinogoATV-0.6.2-code22.apk"}]}
    """.trimIndent()
}
