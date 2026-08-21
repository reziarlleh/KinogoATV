package com.kinogo.atv.data.update

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun `newer canonical release is available`() {
        val result = GitHubReleaseParser.parse(releaseJson(), currentVersionCode = 13)

        assertTrue(result is AppUpdateCheckResult.Available)
        val release = (result as AppUpdateCheckResult.Available).release
        assertEquals("0.5.0", release.versionName)
        assertEquals(14L, release.versionCode)
        assertEquals("a".repeat(64), release.sha256)
        assertTrue(release.toString().contains("downloadUrls=<redacted:1>"))
    }

    @Test
    fun `installed release is up to date`() {
        assertEquals(
            AppUpdateCheckResult.UpToDate("0.5.0", 14),
            GitHubReleaseParser.parse(releaseJson(), currentVersionCode = 14),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `release without GitHub digest is rejected`() {
        GitHubReleaseParser.parse(
            releaseJson().replace("\"digest\": \"sha256:${"a".repeat(64)}\",", ""),
            currentVersionCode = 13,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lookalike download host is rejected`() {
        GitHubReleaseParser.parse(
            releaseJson().replace("https://github.com/", "https://github.com.evil.test/"),
            currentVersionCode = 13,
        )
    }

    @Test
    fun `only exact release origin and admitted GitHub CDN redirect are allowed`() {
        val release = (GitHubReleaseParser.parse(
            releaseJson(),
            currentVersionCode = 13,
        ) as AppUpdateCheckResult.Available).release

        assertTrue(
            GitHubReleaseUpdateClient.isAllowedUpdateDownloadUri(
                URI.create(release.downloadUrl),
                release,
                initial = true,
            ),
        )
        assertTrue(
            GitHubReleaseUpdateClient.isAllowedUpdateDownloadUri(
                URI.create("https://release-assets.githubusercontent.com/github-production-release-asset/1/file?token=opaque"),
                release,
                initial = false,
            ),
        )
        listOf(
            "http://github.com/reziarlleh/KinogoATV/releases/download/v0.5.0/${release.assetName}",
            "https://127.0.0.1/update.apk",
            "https://github.com.evil.test/update.apk",
            "https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.0/${release.assetName}?token=unexpected",
        ).forEach { url ->
            assertTrue(
                !GitHubReleaseUpdateClient.isAllowedUpdateDownloadUri(
                    URI.create(url),
                    release,
                    initial = false,
                ),
            )
        }
    }

    private fun releaseJson(): String =
        """
        {
          "draft": false,
          "prerelease": false,
          "tag_name": "v0.5.0",
          "assets": [
            {
              "name": "KinogoATV-0.5.0-code14.apk",
              "size": 123,
              "digest": "sha256:${"a".repeat(64)}",
              "browser_download_url": "https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.0/KinogoATV-0.5.0-code14.apk"
            }
          ]
        }
        """.trimIndent()
}
