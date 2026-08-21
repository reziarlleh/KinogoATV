package com.kinogo.atv.data.update

import android.content.Context
import com.kinogo.atv.BuildConfig

internal object DefaultAppUpdateClientFactory {
    fun create(
        context: Context,
        additionalManifestUrls: List<String> = emptyList(),
    ): AppUpdateClient {
        val packagedUrls = BuildConfig.UPDATE_MANIFEST_URLS
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val manifestUrls = buildList {
            addAll(DEFAULT_SIGNED_MANIFEST_URLS)
            addAll(packagedUrls)
            addAll(additionalManifestUrls)
        }.distinct()
        val clients = buildList {
            if (manifestUrls.isNotEmpty()) {
                add(SignedManifestUpdateClient(context, manifestUrls))
            }
            add(GitHubReleaseUpdateClient())
        }
        return FallbackAppUpdateClient(clients)
    }

    val DEFAULT_SIGNED_MANIFEST_URLS = listOf(
        // github.io is a static Pages origin and does not depend on the blocked GitHub API/UI.
        "https://reziarlleh.github.io/KinogoATV/update/manifest.json",
        // A separately operated CDN can still deliver the tiny signed manifest when both
        // github.com and github.io are unavailable. APK bytes remain hash/signature verified.
        "https://cdn.jsdelivr.net/gh/reziarlleh/KinogoATV@main/update/manifest.json",
    )
}
