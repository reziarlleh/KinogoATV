package com.kinogo.atv.data.catalog

import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class KinogoSessionHttpClientTest {
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
    }
}
