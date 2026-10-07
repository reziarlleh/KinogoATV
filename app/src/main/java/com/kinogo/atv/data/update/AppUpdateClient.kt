package com.kinogo.atv.data.update

import java.io.File

/** A bounded metadata/download channel used by [AppUpdateManager]. */
internal interface AppUpdateClient {
    suspend fun check(currentVersionCode: Long, currentVersionName: String): AppUpdateCheckResult

    suspend fun download(
        destinationDirectory: File,
        release: AppUpdateRelease,
    ): File
}
