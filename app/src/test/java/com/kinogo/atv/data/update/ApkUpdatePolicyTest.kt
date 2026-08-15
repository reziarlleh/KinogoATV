package com.kinogo.atv.data.update

import org.junit.Test

class ApkUpdatePolicyTest {
    @Test
    fun `newer same-package same-signer archive is accepted`() {
        ApkUpdatePolicy.validate(
            installed = metadata(versionName = "0.4.3-dev", versionCode = 13),
            candidate = metadata(versionName = "0.5.0", versionCode = 14),
            release = release(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `different signer is rejected before installer`() {
        ApkUpdatePolicy.validate(
            installed = metadata(versionName = "0.4.3-dev", versionCode = 13),
            candidate = metadata(
                versionName = "0.5.0",
                versionCode = 14,
                signers = setOf("other"),
            ),
            release = release(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `different package is rejected before installer`() {
        ApkUpdatePolicy.validate(
            installed = metadata(versionName = "0.4.3-dev", versionCode = 13),
            candidate = metadata(
                packageName = "lookalike.package",
                versionName = "0.5.0",
                versionCode = 14,
            ),
            release = release(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-incrementing archive is rejected`() {
        ApkUpdatePolicy.validate(
            installed = metadata(versionName = "0.5.0", versionCode = 14),
            candidate = metadata(versionName = "0.5.0", versionCode = 14),
            release = release(),
        )
    }

    private fun metadata(
        packageName: String = "com.kinogo.atv",
        versionName: String,
        versionCode: Long,
        signers: Set<String> = setOf("certificate"),
    ) = ApkArchiveMetadata(packageName, versionName, versionCode, signers)

    private fun release() = AppUpdateRelease(
        versionName = "0.5.0",
        versionCode = 14,
        assetName = "KinogoATV-0.5.0-code14.apk",
        assetSizeBytes = 123,
        sha256 = "a".repeat(64),
        downloadUrl =
            "https://github.com/reziarlleh/KinogoATV/releases/download/v0.5.0/" +
                "KinogoATV-0.5.0-code14.apk",
    )
}
