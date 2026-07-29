package com.kinogo.atv.data.playback

import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderEmbedDocumentClientTest {
    @Test
    fun `follows validated redirect and redacts transient values`() = runTest {
        val calls = mutableListOf<URI>()
        val transport = ProviderDocumentTransport { url, _ ->
            calls += url
            if (calls.size == 1) {
                ProviderDocumentHttpResponse(
                    statusCode = 302,
                    contentType = null,
                    location = "https://provider-cdn.example/embed/42?token=secret",
                    body = ByteArray(0),
                )
            } else {
                ProviderDocumentHttpResponse(
                    statusCode = 200,
                    contentType = "text/html; charset=utf-8",
                    location = null,
                    body = "<html>fixture</html>".encodeToByteArray(),
                )
            }
        }
        val client = testClient(transport)

        val result = client.fetch(
            embedUrl = "https://provider.example/embed/42",
            refererUrl = "https://kinogo.example/movie/42",
        )

        assertTrue(result is ProviderEmbedDocumentResult.Ready)
        val document = (result as ProviderEmbedDocumentResult.Ready).document
        assertEquals("<html>fixture</html>", document.html)
        assertEquals(2, calls.size)
        assertFalse(document.toString().contains("token=secret"))
        assertFalse(document.toString().contains("fixture"))
    }

    @Test
    fun `rejects non https location before second request`() = runTest {
        var calls = 0
        val client = testClient(
            ProviderDocumentTransport { _, _ ->
                calls++
                ProviderDocumentHttpResponse(
                    statusCode = 302,
                    contentType = null,
                    location = "http://provider.example/embed/42",
                    body = ByteArray(0),
                )
            },
        )

        val result = client.fetch(
            embedUrl = "https://provider.example/embed/42",
            refererUrl = "https://kinogo.example/movie/42",
        )

        assertEquals(
            ProviderEmbedDocumentFailure.REDIRECT_REJECTED,
            (result as ProviderEmbedDocumentResult.Failed).reason,
        )
        assertEquals(1, calls)
    }

    @Test
    fun `does not reveal an HTTP address in failure`() = runTest {
        val secretUrl = "https://provider.example/embed/42?token=do-not-log"
        val result = testClient(
            ProviderDocumentTransport { _, _ ->
                ProviderDocumentHttpResponse(
                    statusCode = 404,
                    contentType = "text/html",
                    location = null,
                    body = "not found".encodeToByteArray(),
                )
            },
        ).fetch(secretUrl, "https://kinogo.example/movie/42")

        assertTrue(result is ProviderEmbedDocumentResult.Failed)
        assertFalse(result.toString().contains(secretUrl))
        assertFalse(result.toString().contains("do-not-log"))
    }

    @Test
    fun `web fallback validation is independent from provider preflight response`() = runTest {
        var transportCalls = 0
        val client = testClient(
            ProviderDocumentTransport { _, _ ->
                transportCalls++
                error("Web fallback validation must not fetch the document")
            },
        )

        val validated = client.validatedWebFallbackUrl(
            embedUrl = "https://provider.example/embed/42?token=fixture",
            refererUrl = "https://kinogo.example/movie/42",
        )
        val rejected = client.validatedWebFallbackUrl(
            embedUrl = "http://provider.example/embed/42",
            refererUrl = "https://kinogo.example/movie/42",
        )

        assertEquals("https://provider.example/embed/42?token=fixture", validated)
        assertEquals(null, rejected)
        assertEquals(0, transportCalls)
    }

    @Test
    fun `destination validation never blocks the caller thread`() = runTest {
        val callerThread = Thread.currentThread()
        val validatedOn = mutableListOf<Thread>()
        val client = testClient(
            transport = ProviderDocumentTransport { _, _ ->
                ProviderDocumentHttpResponse(
                    statusCode = 200,
                    contentType = "text/html",
                    location = null,
                    body = "<html>fixture</html>".encodeToByteArray(),
                )
            },
            destinationValidator = {
                validatedOn += Thread.currentThread()
                check(Thread.currentThread() !== callerThread) {
                    "DNS destination validation ran on the playback caller thread"
                }
            },
        )

        val result = client.fetch(
            embedUrl = "https://provider.example/embed/42",
            refererUrl = "https://kinogo.example/movie/42",
        )

        assertTrue(result is ProviderEmbedDocumentResult.Ready)
        assertTrue(validatedOn.isNotEmpty())
        assertTrue(validatedOn.all { it !== callerThread })
    }

    private fun testClient(
        transport: ProviderDocumentTransport,
        destinationValidator: (URI) -> Unit = { uri -> require(uri.scheme == "https") },
    ) =
        ProviderEmbedDocumentClient(
            transport = transport,
            destinationValidator = destinationValidator,
            maxRedirects = 2,
        )
}
