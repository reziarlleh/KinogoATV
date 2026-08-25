package com.kinogo.atv.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticUpdateCheckPolicyTest {
    @Test
    fun `startup waits for loaded enabled preference and runs once`() {
        assertFalse(AutomaticUpdateCheckPolicy.shouldStart(null, alreadyStarted = false))
        assertFalse(AutomaticUpdateCheckPolicy.shouldStart(false, alreadyStarted = false))
        assertTrue(AutomaticUpdateCheckPolicy.shouldStart(true, alreadyStarted = false))
        assertFalse(AutomaticUpdateCheckPolicy.shouldStart(true, alreadyStarted = true))
    }

    @Test
    fun `automatic failure retries once while manual check stays immediate`() {
        assertTrue(
            AutomaticUpdateCheckPolicy.shouldRetry(
                AppUpdateCheckOrigin.AUTOMATIC,
                failedAttempt = 1,
            ),
        )
        assertFalse(
            AutomaticUpdateCheckPolicy.shouldRetry(
                AppUpdateCheckOrigin.AUTOMATIC,
                failedAttempt = 2,
            ),
        )
        assertFalse(
            AutomaticUpdateCheckPolicy.shouldRetry(
                AppUpdateCheckOrigin.MANUAL,
                failedAttempt = 1,
            ),
        )
    }

    @Test
    fun `only automatic available result requests a global prompt`() {
        val available = AppUpdateCheckResult.Available(
            AppUpdateRelease(
                versionName = "0.5.3",
                versionCode = 17L,
                assetName = "KinogoATV-v0.5.3.apk",
                assetSizeBytes = 1_024L,
                downloadUrl = "https://example.org/KinogoATV.apk",
                sha256 = "a".repeat(64),
                channel = AppUpdateReleaseChannel.SIGNED_MANIFEST,
                validUntilEpochSeconds = Long.MAX_VALUE,
            ),
        )

        assertTrue(
            AutomaticUpdateCheckPolicy.shouldPrompt(AppUpdateCheckOrigin.AUTOMATIC, available),
        )
        assertFalse(
            AutomaticUpdateCheckPolicy.shouldPrompt(AppUpdateCheckOrigin.MANUAL, available),
        )
        assertFalse(
            AutomaticUpdateCheckPolicy.shouldPrompt(
                AppUpdateCheckOrigin.AUTOMATIC,
                AppUpdateCheckResult.UpToDate("0.5.2", 16L),
            ),
        )
    }
}
