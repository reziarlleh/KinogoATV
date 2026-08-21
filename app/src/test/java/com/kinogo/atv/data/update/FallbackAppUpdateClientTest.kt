package com.kinogo.atv.data.update

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FallbackAppUpdateClientTest {
    @Test
    fun `blocked independent channel falls back to GitHub`() = runTest {
        val independent = FakeClient(
            AppUpdateReleaseChannel.SIGNED_MANIFEST,
            failure = IllegalStateException("blocked"),
        )
        val github = FakeClient(
            AppUpdateReleaseChannel.GITHUB_RELEASE,
            result = AppUpdateCheckResult.UpToDate("0.5.0"),
        )

        assertEquals(
            AppUpdateCheckResult.UpToDate("0.5.0"),
            FallbackAppUpdateClient(listOf(independent, github)).check(14),
        )
        assertEquals(1, independent.checkCount)
        assertEquals(1, github.checkCount)
    }

    @Test
    fun `verified independent result does not wait for blocked GitHub`() = runTest {
        val independent = FakeClient(
            AppUpdateReleaseChannel.SIGNED_MANIFEST,
            result = AppUpdateCheckResult.UpToDate("0.5.0"),
        )
        val github = FakeClient(
            AppUpdateReleaseChannel.GITHUB_RELEASE,
            failure = IllegalStateException("blocked"),
        )

        assertEquals(
            AppUpdateCheckResult.UpToDate("0.5.0"),
            FallbackAppUpdateClient(listOf(independent, github)).check(14),
        )
        assertEquals(1, independent.checkCount)
        assertEquals(0, github.checkCount)
    }

    @Test
    fun `signed manifest older than installed app cannot block fallback`() = runTest {
        val independent = FakeClient(
            AppUpdateReleaseChannel.SIGNED_MANIFEST,
            result = AppUpdateCheckResult.UpToDate("0.4.3", 13),
        )
        val github = FakeClient(
            AppUpdateReleaseChannel.GITHUB_RELEASE,
            result = AppUpdateCheckResult.UpToDate("0.5.0", 14),
        )

        assertEquals(
            AppUpdateCheckResult.UpToDate("0.5.0", 14),
            FallbackAppUpdateClient(listOf(independent, github)).check(14),
        )
        assertEquals(1, github.checkCount)
    }

    @Test
    fun `download is routed back to metadata channel`() = runTest {
        val expected = File("independent.apk")
        val independent = FakeClient(
            AppUpdateReleaseChannel.SIGNED_MANIFEST,
            downloadResult = expected,
        )
        val github = FakeClient(AppUpdateReleaseChannel.GITHUB_RELEASE)
        val release = release(AppUpdateReleaseChannel.SIGNED_MANIFEST)

        assertSame(
            expected,
            FallbackAppUpdateClient(listOf(independent, github)).download(File("."), release),
        )
        assertEquals(1, independent.downloadCount)
        assertEquals(0, github.downloadCount)
    }

    private class FakeClient(
        override val channel: AppUpdateReleaseChannel,
        private val result: AppUpdateCheckResult? = null,
        private val failure: Throwable? = null,
        private val downloadResult: File = File("update.apk"),
    ) : AppUpdateClient {
        var checkCount = 0
        var downloadCount = 0

        override suspend fun check(currentVersionCode: Long): AppUpdateCheckResult {
            checkCount += 1
            failure?.let { throw it }
            return result ?: AppUpdateCheckResult.UpToDate(null)
        }

        override suspend fun download(
            destinationDirectory: File,
            release: AppUpdateRelease,
        ): File {
            downloadCount += 1
            return downloadResult
        }
    }

    private fun release(channel: AppUpdateReleaseChannel) = AppUpdateRelease(
        versionName = "0.5.1",
        versionCode = 15,
        assetName = "KinogoATV-0.5.1-code15.apk",
        assetSizeBytes = 123,
        sha256 = "a".repeat(64),
        downloadUrl = "https://updates.example.org/KinogoATV-0.5.1-code15.apk",
        channel = channel,
    )
}
