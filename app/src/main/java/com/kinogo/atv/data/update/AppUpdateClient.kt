package com.kinogo.atv.data.update

import java.io.File
import kotlinx.coroutines.CancellationException

/** A bounded metadata/download channel used by [AppUpdateManager]. */
internal interface AppUpdateClient {
    val channel: AppUpdateReleaseChannel

    suspend fun check(currentVersionCode: Long): AppUpdateCheckResult

    suspend fun download(
        destinationDirectory: File,
        release: AppUpdateRelease,
    ): File
}

/**
 * Tries operator-signed independent manifests first and GitHub only as a compatibility fallback.
 * A successfully verified source is authoritative, including an up-to-date result. This avoids a
 * blocked secondary source delaying every app start.
 */
internal class FallbackAppUpdateClient(
    private val clients: List<AppUpdateClient>,
) : AppUpdateClient {
    init {
        require(clients.isNotEmpty())
        require(clients.map(AppUpdateClient::channel).distinct().size == clients.size)
    }

    override val channel: AppUpdateReleaseChannel
        get() = clients.first().channel

    override suspend fun check(currentVersionCode: Long): AppUpdateCheckResult {
        var failure: Throwable? = null
        clients.forEach { client ->
            try {
                val result = client.check(currentVersionCode)
                if (
                    result is AppUpdateCheckResult.UpToDate &&
                    result.latestVersionCode != null &&
                    result.latestVersionCode < currentVersionCode
                ) {
                    failure = IllegalStateException("Update source metadata is stale")
                } else {
                    return result
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = error
            }
        }
        throw IllegalStateException("No trusted update source is available", failure)
    }

    override suspend fun download(
        destinationDirectory: File,
        release: AppUpdateRelease,
    ): File {
        val client = clients.singleOrNull { it.channel == release.channel }
            ?: throw IllegalArgumentException("Update channel is unavailable")
        return client.download(destinationDirectory, release)
    }
}
