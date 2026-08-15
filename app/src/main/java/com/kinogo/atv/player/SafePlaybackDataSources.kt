package com.kinogo.atv.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.kinogo.atv.data.mirror.NetworkAddressPolicy
import com.kinogo.atv.data.network.ResilientPublicDns
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Network stack shared by manifests, media segments and progressive files. */
object SafePlaybackDataSources {
    private val client: OkHttpClient by lazy {
        buildClient(PublicOnlyDns())
    }

    fun createFactory(): DataSource.Factory =
        OkHttpDataSource.Factory(client)
            .setUserAgent("KinogoATV/0.5 (Android TV; native player)")

    internal fun buildClient(dns: Dns): OkHttpClient = OkHttpClient.Builder()
        .dns(dns)
        // Built-in redirects stay disabled: every manifest/segment redirect is followed manually
        // only after the target has passed the same HTTPS and public-DNS boundary.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(ValidatedPlaybackRedirectInterceptor(dns))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

internal class ValidatedPlaybackRedirectInterceptor(
    private val dns: Dns,
    private val maxRedirects: Int = MAX_PLAYBACK_REDIRECTS,
) : Interceptor {
    init {
        require(maxRedirects >= 0)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        validatePlaybackDestination(request.url, dns)
        var redirectCount = 0

        while (true) {
            val response = chain.proceed(request)
            if (response.code !in PLAYBACK_REDIRECT_CODES) return response

            val location = response.header("Location") ?: return response
            if (redirectCount >= maxRedirects) {
                response.close()
                throw IOException("Playback redirect limit exceeded")
            }

            val redirected = try {
                validatedPlaybackRedirectRequest(request, location, dns)
            } catch (_: Exception) {
                response.close()
                throw IOException("Playback redirect rejected")
            }
            response.close()
            request = redirected
            redirectCount++
        }
    }
}

/**
 * Produces the next GET/HEAD without ever putting the rejected location into an exception.
 * Authentication-like headers are stripped whenever a CDN redirect changes origin.
 */
internal fun validatedPlaybackRedirectRequest(
    current: Request,
    location: String,
    dns: Dns,
): Request {
    require(current.method == "GET" || current.method == "HEAD") {
        "Playback redirects are only supported for GET and HEAD"
    }
    val target = current.url.resolve(location)
        ?: throw IOException("Playback redirect rejected")
    validatePlaybackDestination(target, dns)

    val builder = current.newBuilder().url(target)
    if (!current.url.sameOrigin(target)) {
        SENSITIVE_REDIRECT_HEADERS.forEach(builder::removeHeader)
    }
    return builder.build()
}

internal fun validatePlaybackDestination(url: HttpUrl, dns: Dns) {
    try {
        require(url.isHttps) { "Playback destination must use HTTPS" }
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "Playback destination must not contain user info"
        }
        require(url.fragment == null) { "Playback destination must not contain a fragment" }
        require(url.port == 443) { "Playback destination must use the standard HTTPS port" }
        require(!url.host.isIpLiteral()) { "Playback destination must use a DNS name" }
        val addresses = dns.lookup(url.host)
        require(addresses.isNotEmpty() && addresses.all(NetworkAddressPolicy::isPublic)) {
            "Playback destination did not resolve exclusively to public addresses"
        }
    } catch (_: Exception) {
        throw IOException("Playback destination rejected")
    }
}

internal class PublicOnlyDns(
    private val delegate: Dns = ResilientPublicDns(),
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !NetworkAddressPolicy.isPublic(it) }) {
            throw UnknownHostException("Playback host did not resolve exclusively to public addresses")
        }
        return addresses
    }
}

private fun HttpUrl.sameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private fun String.isIpLiteral(): Boolean =
    ':' in this || IPV4_LITERAL.matches(this)

private const val MAX_PLAYBACK_REDIRECTS = 5
private val PLAYBACK_REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
private val SENSITIVE_REDIRECT_HEADERS = listOf(
    "Authorization",
    "Cookie",
    "Origin",
    "Proxy-Authorization",
    "Referer",
)
private val IPV4_LITERAL = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
