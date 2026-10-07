package com.kinogo.atv.data.update

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun `newer canonical release is available`() {
        val result = GitHubReleaseParser.parse(releaseJson(), currentVersionCode = 13, currentVersionName = "0.4.9")

        assertTrue(result is AppUpdateCheckResult.Available)
        val release = (result as AppUpdateCheckResult.Available).release
        assertEquals("0.5.0", release.versionName)
        assertEquals(14L, release.versionCode)
        assertEquals("a".repeat(64), release.sha256)
        assertTrue(release.toString().contains("downloadUrl=<redacted>"))
    }

    @Test
    fun `installed release is up to date`() {
        assertEquals(
            AppUpdateCheckResult.UpToDate("0.5.0", 14),
            GitHubReleaseParser.parse(releaseJson(), currentVersionCode = 14, currentVersionName = "0.5.0"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `release without GitHub digest is rejected`() {
        GitHubReleaseParser.parse(
            releaseJson().replace("\"digest\": \"sha256:${"a".repeat(64)}\",", ""),
            currentVersionCode = 13,
            currentVersionName = "0.4.9",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lookalike download host is rejected`() {
        GitHubReleaseParser.parse(
            releaseJson().replace("https://github.com/", "https://github.com.evil.test/"),
            currentVersionCode = 13,
            currentVersionName = "0.4.9",
        )
    }

    @Test
    fun `only exact release origin and admitted GitHub CDN redirect are allowed`() {
        val release = (GitHubReleaseParser.parse(
            releaseJson(),
            currentVersionCode = 13,
            currentVersionName = "0.4.9",
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

    @Test
    fun `versions are compared numerically by major minor and patch`() {
        listOf(
            "0.6.9" to "0.6.10",
            "0.9.99" to "0.10.0",
            "0.999.999" to "1.0.0",
        ).forEach { (installed, latest) ->
            assertTrue(
                GitHubReleaseParser.parse(releaseJson(latest), 13, installed) is AppUpdateCheckResult.Available,
            )
            assertEquals(
                AppUpdateCheckResult.UpToDate(installed, 14),
                GitHubReleaseParser.parse(releaseJson(installed), 15, latest),
            )
        }
    }

    @Test
    fun `equal version name does not update even with higher Android code`() {
        assertEquals(AppUpdateCheckResult.UpToDate("0.5.0", 14), GitHubReleaseParser.parse(releaseJson(), 13, "0.5.0"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `newer version with non increasing Android code is rejected`() {
        GitHubReleaseParser.parse(releaseJson(), 14, "0.4.9")
    }

    @Test
    fun `stable release has no clock or expiry dependency`() {
        val body = releaseJson().replace("\"draft\": false,", "\"published_at\": \"2000-01-01T00:00:00Z\", \"draft\": false,")
        assertTrue(GitHubReleaseParser.parse(body, 13, "0.4.9") is AppUpdateCheckResult.Available)
    }

    @Test
    fun `draft prerelease invalid version duplicate assets and tag mismatch are rejected`() {
        listOf(
            releaseJson().replace("\"draft\": false", "\"draft\": true"),
            releaseJson().replace("\"prerelease\": false", "\"prerelease\": true"),
            releaseJson("0.5.0-beta"),
            releaseJson("00.5.0"),
            releaseJson("9223372036854775808.5.0"),
            releaseJson().replace("\"tag_name\": \"v0.5.0\"", "\"tag_name\": \"v0.5.1\""),
            releaseJson().replace("\"assets\": [", "\"assets\": [{\"name\": \"KinogoATV-0.5.0-code14.apk\", \"size\":123, \"digest\": \"sha256:${"a".repeat(64)}\", \"browser_download_url\": \"https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.0/KinogoATV-0.5.0-code14.apk\"},"),
        ).forEach { body ->
            assertTrue(runCatching { GitHubReleaseParser.parse(body, 13, "0.4.9") }.isFailure)
        }
        listOf("0.5", "0.5.0-beta", "-1.5.0", "00.5.0", "9223372036854775808.5.0").forEach { installed ->
            assertTrue(runCatching { GitHubReleaseParser.parse(releaseJson(), 13, installed) }.isFailure)
        }
    }

    private fun releaseJson(version: String = "0.5.0"): String =
        """
        {
          "draft": false,
          "prerelease": false,
          "tag_name": "v$version",
          "assets": [
            {
              "name": "KinogoATV-$version-code14.apk",
              "size": 123,
              "digest": "sha256:${"a".repeat(64)}",
              "browser_download_url": "https://github.com/reziarlleh/KinogoATV/releases/download/v$version/KinogoATV-$version-code14.apk"
            }
          ]
        }
        """.trimIndent()
}
