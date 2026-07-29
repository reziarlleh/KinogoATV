package com.kinogo.atv.player

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SafePlaybackDataSourcesTest {
    @Test
    fun `public-only DNS returns exclusively public answers`() {
        val expected = listOf(InetAddress.getByName("8.8.8.8"))
        val dns = PublicOnlyDns(FakeDns(expected))

        assertEquals(expected, dns.lookup("media.test"))
    }

    @Test
    fun `public-only DNS rejects private and mixed answers`() {
        val privateAddress = InetAddress.getByName("192.168.1.10")
        val publicAddress = InetAddress.getByName("8.8.8.8")

        listOf(
            listOf(privateAddress),
            listOf(publicAddress, privateAddress),
            emptyList(),
        ).forEach { answers ->
            try {
                PublicOnlyDns(FakeDns(answers)).lookup("media.test")
                fail("Expected unsafe DNS answer to be rejected")
            } catch (_: UnknownHostException) {
                // Expected.
            }
        }
    }

    @Test
    fun `playback client keeps built-in redirects disabled and installs validator`() {
        val client = SafePlaybackDataSources.buildClient(
            FakeDns(listOf(InetAddress.getByName("8.8.8.8"))),
        )

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertTrue(client.interceptors.any { it is ValidatedPlaybackRedirectInterceptor })
    }

    @Test
    fun `validated CDN redirect preserves playback headers and strips credentials`() {
        val dns = FakeDns(listOf(InetAddress.getByName("8.8.8.8")))
        val current = Request.Builder()
            .url("https://media.test/start.m3u8?grant=fixture")
            .header("Range", "bytes=10-")
            .header("User-Agent", "fixture-player")
            .header("Authorization", "Bearer fixture-secret")
            .header("Cookie", "fixture-cookie")
            .header("Referer", "https://media.test/embed")
            .build()

        val redirected = validatedPlaybackRedirectRequest(
            current = current,
            location = "https://cdn.test/final.m3u8?grant=fixture-next",
            dns = dns,
        )

        assertEquals("cdn.test", redirected.url.host)
        assertEquals("bytes=10-", redirected.header("Range"))
        assertEquals("fixture-player", redirected.header("User-Agent"))
        assertNull(redirected.header("Authorization"))
        assertNull(redirected.header("Cookie"))
        assertNull(redirected.header("Referer"))
    }

    @Test
    fun `same-origin redirect keeps request context`() {
        val dns = FakeDns(listOf(InetAddress.getByName("8.8.8.8")))
        val current = Request.Builder()
            .url("https://media.test/start.m3u8")
            .header("Referer", "https://media.test/embed")
            .build()

        val redirected = validatedPlaybackRedirectRequest(current, "/final.m3u8", dns)

        assertEquals("https://media.test/final.m3u8", redirected.url.toString())
        assertEquals("https://media.test/embed", redirected.header("Referer"))
    }

    @Test
    fun `unsafe playback destinations are rejected without echoing tokens`() {
        val publicDns = FakeDns(listOf(InetAddress.getByName("8.8.8.8")))
        val privateDns = FakeDns(listOf(InetAddress.getByName("192.168.1.10")))
        val current = Request.Builder().url("https://media.test/start.m3u8").build()
        val cases = listOf(
            "http://cdn.test/final.m3u8?token=do-not-echo" to publicDns,
            "https://user:password@cdn.test/final.m3u8?token=do-not-echo" to publicDns,
            "https://cdn.test/final.m3u8?token=do-not-echo#fragment" to publicDns,
            "https://cdn.test:444/final.m3u8?token=do-not-echo" to publicDns,
            "https://127.0.0.1/final.m3u8?token=do-not-echo" to publicDns,
            "https://private.test/final.m3u8?token=do-not-echo" to privateDns,
        )

        cases.forEach { (location, dns) ->
            try {
                validatedPlaybackRedirectRequest(current, location, dns)
                fail("Expected unsafe playback redirect to be rejected")
            } catch (error: Exception) {
                assertFalse(error.message.orEmpty().contains("do-not-echo"))
                assertFalse(error.message.orEmpty().contains(location))
            }
        }
    }

    @Test
    fun `interceptor follows a bounded validated redirect without built-in redirects`() {
        val initial = Request.Builder().url("https://media.test/start.m3u8").build()
        val chain = ScriptedChain(initial) { request, callIndex ->
            if (callIndex == 0) {
                response(request, 302, "https://cdn.test/final.m3u8?grant=fixture")
            } else {
                response(request, 200)
            }
        }

        val response = ValidatedPlaybackRedirectInterceptor(
            dns = FakeDns(listOf(InetAddress.getByName("8.8.8.8"))),
            maxRedirects = 2,
        ).intercept(chain)

        response.close()
        assertEquals(200, response.code)
        assertEquals(2, chain.requests.size)
        assertEquals("cdn.test", chain.requests.last().url.host)
    }

    @Test
    fun `interceptor stops redirect loops with a redacted error`() {
        val initial = Request.Builder()
            .url("https://media.test/start.m3u8?token=fixture-secret")
            .build()
        val chain = ScriptedChain(initial) { request, callIndex ->
            response(request, 302, "/hop-$callIndex?token=fixture-secret")
        }

        try {
            ValidatedPlaybackRedirectInterceptor(
                dns = FakeDns(listOf(InetAddress.getByName("8.8.8.8"))),
                maxRedirects = 2,
            ).intercept(chain)
            fail("Expected redirect loop to be rejected")
        } catch (error: Exception) {
            assertEquals("Playback redirect limit exceeded", error.message)
            assertFalse(error.message.orEmpty().contains("fixture-secret"))
            assertEquals(3, chain.requests.size)
        }
    }

    private fun response(
        request: Request,
        code: Int,
        location: String? = null,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("fixture")
        .apply { location?.let { header("Location", it) } }
        .body(ByteArray(0).toResponseBody())
        .build()

    private class FakeDns(private val addresses: List<InetAddress>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = addresses
    }

    private class ScriptedChain(
        private val initialRequest: Request,
        private val responder: (Request, Int) -> Response,
    ) : Interceptor.Chain {
        val requests = mutableListOf<Request>()
        private val call: Call = OkHttpClient().newCall(initialRequest)

        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            val callIndex = requests.size
            requests += request
            return responder(request, callIndex)
        }

        override fun connection(): Connection? = null

        override fun call(): Call = call

        override fun connectTimeoutMillis(): Int = 10_000

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 10_000

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 10_000

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
