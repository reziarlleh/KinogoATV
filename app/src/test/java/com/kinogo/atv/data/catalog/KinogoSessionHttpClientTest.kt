package com.kinogo.atv.data.catalog

import java.net.URI
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class KinogoSessionHttpClientTest {
    @Test
    fun allPosterDocumentRequestsShareTemplateSelectionWithoutChangingTheirRoutes() = runTest {
        val origin = "https://kinogo.family"
        val transport = KinogoSessionHttpClient(
            dns = object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)))
            },
        )
        val requests = mutableListOf<Request>()
        val clientField = KinogoSessionHttpClient::class.java.declaredFields
            .single { OkHttpClient::class.java.isAssignableFrom(it.type) }
            .apply { isAccessible = true }
        val client = (clientField.get(transport) as OkHttpClient).newBuilder()
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("Fixture")
                    .body("<div id=dle-content><article class=shortStory>Kinogo fixture</article></div>"
                        .toResponseBody("text/html; charset=UTF-8".toMediaType()))
                    .build()
            }.build()
        clientField.set(transport, client)

        val routes = listOf(
            "/", "/page/2/", "/novinki/", "/search/Film/", "/search/Film/page/2/",
            "/favorites/", "/favorites/watch/", "/favorites/done/page/2/",
            "/serialy/99-film.html?source=history",
        )
        routes.forEach { transport.getFullHtmlRaw(origin, it) }
        transport.postCatalogForm(origin, "/novinki/", mapOf("xsort" to "1", "xs_field" to "clearallfields"))
        transport.getRaw(origin, "/engine/ajax/controller.php?mod=favorites&action=plus")

        assertEquals(routes.size + 3, requests.size)
        assertEquals("action=mobiledisable", requests.first().url.encodedQuery)
        assertEquals(routes, requests.drop(1).take(routes.size).map { request ->
            request.url.encodedPath + request.url.encodedQuery?.let { "?$it" }.orEmpty()
        })
        assertEquals("POST", requests[requests.lastIndex - 1].method)
        assertEquals("/novinki/", requests[requests.lastIndex - 1].url.encodedPath)
        assertNull(requests[requests.lastIndex - 1].url.encodedQuery)
        assertEquals("mod=favorites&action=plus", requests.last().url.encodedQuery)
        assertEquals("XMLHttpRequest", requests.last().header("X-Requested-With"))
    }

    @Test
    fun sessionResponseNeverPrintsHtmlOrQueryTokens() {
        val response = SessionHttpResponse(
            requestedOrigin = "https://kinogo.parts",
            resolvedOrigin = "https://kinogo.parts",
            relativePath = "/index.php?token=secret-query",
            statusCode = 200,
            body = "<input value='secret-hidden-token'>",
        )

        assertFalse(response.toString().contains("secret-query"))
        assertFalse(response.toString().contains("secret-hidden-token"))
    }

    @Test
    fun statefulCatalogClientUsesHttp11Only() {
        val transport = KinogoSessionHttpClient()
        val clientField = KinogoSessionHttpClient::class.java.declaredFields
            .single { field -> OkHttpClient::class.java.isAssignableFrom(field.type) }
            .apply { isAccessible = true }
        val configuredClient = clientField.get(transport) as OkHttpClient

        assertEquals(listOf(Protocol.HTTP_1_1), configuredClient.protocols)
    }

    @Test
    fun sessionRouteAllowsQueryButRejectsCrossOriginAndTraversal() {
        assertEquals(
            "/engine/ajax/controller.php?mod=mylist",
            SessionRouteNormalizer.normalize("/engine/ajax/controller.php?mod=mylist"),
        )
        listOf("https://evil.example/", "//evil.example/", "/../secret", "/%2e%2e/secret").forEach {
            runCatching { SessionRouteNormalizer.normalize(it) }
                .onSuccess { error("Expected rejection for $it") }
        }
    }

    @Test
    fun finalSessionRouteKeepsTerminalRedirectPathAndQueryWithoutOrigin() {
        assertEquals(
            "/serialy/35182-ierihon.html?source=history",
            finalSessionRelativePath(
                URI("https://kinogo.example/serialy/35182-ierihon.html?source=history#player"),
            ),
        )
    }

    @Test
    fun cookiesAreIsolatedByOriginAndDeletionIsHonored() {
        val store = OriginCookieStore()
        assertEquals(0L, store.epoch("https://kinogo.parts"))
        store.absorb(
            "https://kinogo.parts",
            mapOf("Set-Cookie" to listOf("PHPSESSID=abc; Path=/; Secure; HttpOnly")),
        )

        assertEquals("PHPSESSID=abc", store.header("https://kinogo.parts"))
        assertEquals(1L, store.epoch("https://kinogo.parts"))
        assertNull(store.header("https://another.example"))

        store.absorb(
            "https://kinogo.parts",
            mapOf("Set-Cookie" to listOf("PHPSESSID=abc; Path=/; Secure; HttpOnly")),
        )
        assertEquals(1L, store.epoch("https://kinogo.parts"))

        store.absorb(
            "https://kinogo.parts",
            mapOf("set-cookie" to listOf("PHPSESSID=; Max-Age=0; Path=/")),
        )
        assertNull(store.header("https://kinogo.parts"))
        assertEquals(2L, store.epoch("https://kinogo.parts"))

        store.absorb(
            "https://kinogo.parts",
            listOf("PHPSESSID=next; Path=/; Secure; HttpOnly"),
        )
        assertEquals(3L, store.epoch("https://kinogo.parts"))
        store.clear("https://kinogo.parts")
        assertEquals(4L, store.epoch("https://kinogo.parts"))
        store.clear("https://kinogo.parts")
        assertEquals(4L, store.epoch("https://kinogo.parts"))
        assertEquals(2L, store.clearGeneration("https://kinogo.parts"))
        assertEquals(0L, store.clearGeneration("https://another.example"))
    }
}
