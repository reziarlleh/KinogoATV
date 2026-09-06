package com.kinogo.atv.data.network

import java.net.URI

/** Parses an HTTPS target's syntax; callers still apply their own DNS and SSRF policy. */
internal fun strictHttpsUriOrNull(rawValue: String): URI? {
    if (!rawValue.hasSafeUriTextShape()) return null
    val uri = runCatching { URI(rawValue) }.getOrNull() ?: return null
    return uri.takeIf(URI::hasStrictHttpsShape)
}

private fun String.hasSafeUriTextShape(): Boolean =
    isNotBlank() &&
        this == trim() &&
        none(Char::isISOControl) &&
        '\\' !in this

private fun URI.hasStrictHttpsShape(): Boolean =
    scheme.equals("https", ignoreCase = true) &&
        !isOpaque &&
        !host.isNullOrBlank() &&
        rawUserInfo == null &&
        rawFragment == null &&
        (port == -1 || port == 443)
