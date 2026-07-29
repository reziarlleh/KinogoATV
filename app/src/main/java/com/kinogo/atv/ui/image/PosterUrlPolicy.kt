package com.kinogo.atv.ui.image

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Accepts only credential-free HTTPS poster URLs and removes cache-irrelevant fragments. */
internal object PosterUrlPolicy {
    fun normalizeOrNull(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val url = raw.toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return url.newBuilder()
            .fragment(null)
            .build()
            .toString()
    }
}
