package com.kinogo.atv.data.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider

internal class AppUpdateManager(
    context: Context,
    private val client: GitHubReleaseUpdateClient = GitHubReleaseUpdateClient(),
) {
    private val appContext = context.applicationContext
    private val verifier = ApkUpdateVerifier(appContext)

    suspend fun check(currentVersionCode: Long): AppUpdateCheckResult =
        client.check(currentVersionCode)

    suspend fun downloadAndVerify(release: AppUpdateRelease): VerifiedAppUpdate {
        val directory = appContext.cacheDir.resolve(UPDATE_CACHE_DIRECTORY)
        val file = client.download(directory, release)
        try {
            verifier.verify(file, release)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        return VerifiedAppUpdate(release, file)
    }

    fun requestInstall(
        activity: Activity,
        update: VerifiedAppUpdate,
    ): AppUpdateInstallResult {
        require(update.apkFile.isFile) { "Verified update is no longer available" }
        if (!activity.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            )
            activity.startActivity(permissionIntent)
            return AppUpdateInstallResult.UnknownSourcesPermissionOpened
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.updates",
            update.apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(installIntent)
        return AppUpdateInstallResult.InstallerOpened
    }

    private companion object {
        const val UPDATE_CACHE_DIRECTORY = "updates"
    }
}
