package com.kinogo.atv.data.catalog

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.security.cert.Certificate
import java.util.concurrent.CancellationException
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SafeHtmlClientTest {
    @Test
    fun `route normalizer retains a safe path and query`() {
        assertEquals(
            "/catalog/page/2?sort=new&year=2024",
            CatalogRouteNormalizer.normalize("/catalog/page/2?sort=new&year=2024"),
        )

        val unicodeRoute = CatalogRouteNormalizer.normalize("/фильмы?жанр=драма")
        assertTrue(unicodeRoute.all { it.code < 128 })
        assertEquals("/фильмы", URI(unicodeRoute).path)
        assertEquals("жанр=драма", URI(unicodeRoute).query)
    }

    @Test
    fun `route normalizer rejects origin replacement and ambiguous paths`() {
        val invalidRoutes =
            listOf(
                "https://evil.example/catalog",
                "//evil.example/catalog",
                "/catalog#fragment",
                "/catalog/../admin",
                "/catalog/%2e%2E/admin",
                "/catalog/%2Fadmin",
                "/catalog/%5cadmin",
                "/catalog/%252e%252e/admin",
                "/catalog%00admin",
                "/catalog\\admin",
                "/catalog admin",
                "/catalog%zz",
            )

        invalidRoutes.forEach { route ->
            try {
                CatalogRouteNormalizer.normalize(route)
                fail("Expected route to be rejected: $route")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test
    fun `body decoder honors a quoted legacy charset without network`() = runTest {
        val source = "<title>КиноГо</title><div id='dle-content'>тест</div>"
        val bytes = source.toByteArray(Charset.forName("windows-1251"))

        val decoded =
            CatalogHtmlBodyDecoder(maxBodyBytes = 4_096).read(
                input = ByteArrayInputStream(bytes),
                contentType = "Text/HTML; Charset=\"windows-1251\"",
                declaredLength = bytes.size.toLong(),
            )

        assertEquals(source, decoded)
        CatalogHtmlDocumentPolicy.validate(decoded)
    }

    @Test
    fun `body decoder defaults to UTF-8 when content type is absent`() = runTest {
        val source = "KinoGo <div id='dle-content'>каталог</div>"
        val decoded =
            CatalogHtmlBodyDecoder(maxBodyBytes = 1_024).read(
                input = ByteArrayInputStream(source.toByteArray()),
                contentType = null,
            )

        assertEquals(source, decoded)
    }

    @Test
    fun `content type matching is exact and unsupported charsets are rejected`() {
        expectException<CatalogContentTypeException> {
            CatalogHtmlMetadata.charsetFor("image/text/html-malicious; charset=utf-8")
        }
        expectException<CatalogCharsetException> {
            CatalogHtmlMetadata.charsetFor("text/html; charset=no-such-charset")
        }
        expectException<CatalogCharsetException> {
            CatalogHtmlMetadata.charsetFor("text/html; charset=utf-8; charset=windows-1251")
        }
        expectException<CatalogCharsetException> {
            CatalogHtmlMetadata.charsetFor("text/html; charset=")
        }
        assertEquals(
            Charsets.UTF_8,
            CatalogHtmlMetadata.charsetFor("application/xhtml+xml; charset=UTF-8"),
        )
    }

    @Test
    fun `declared and streamed body sizes are both bounded`() = runTest {
        val decoder = CatalogHtmlBodyDecoder(maxBodyBytes = 8)
        val untouched = CountingInputStream(ByteArray(1))
        val declaredError =
            expectSuspendException<CatalogResponseTooLargeException> {
                decoder.read(
                    input = untouched,
                    contentType = "text/html",
                    declaredLength = 9,
                )
            }
        assertEquals(8, declaredError.limitBytes)
        assertEquals(0, untouched.readCount)

        assertEquals(
            "12345678",
            decoder.read(
                input = ByteArrayInputStream("12345678".toByteArray()),
                contentType = "text/html",
            ),
        )
        val streamedError =
            expectSuspendException<CatalogResponseTooLargeException> {
                decoder.read(
                    input = ByteArrayInputStream("123456789".toByteArray()),
                    contentType = "text/html",
                )
            }
        assertEquals(8, streamedError.limitBytes)
    }

    @Test
    fun `challenge is distinguished from an unrelated fingerprint failure`() {
        expectException<CatalogChallengeException> {
            CatalogHtmlDocumentPolicy.validate(
                "<title>Just a moment</title><div id='dle-content'>KinoGo</div>",
            )
        }
        expectException<CatalogFingerprintException> {
            CatalogHtmlDocumentPolicy.validate("<html><title>Unrelated site</title></html>")
        }
    }

    @Test
    fun `client can be exercised without DNS and decodes a valid response`() = runTest {
        val source = "<title>KinoGo</title><div id='dle-content'>каталог</div>"
        val connection =
            FakeHttpsConnection(
                statusCode = 200,
                body = source.toByteArray(),
                contentTypeValue = "text/html; charset=utf-8",
            )
        val validated = mutableListOf<URI>()
        val opened = mutableListOf<URI>()
        val client =
            SafeHtmlClient(
                connectTimeoutMs = 100,
                readTimeoutMs = 100,
                maxRedirects = 1,
                maxBodyBytes = 4_096,
                destinationValidator = { validated += it },
                connectionFactory = { uri ->
                    opened += uri
                    connection
                },
            )

        val response = client.get("KINOGO.PARTS", "/фильмы?page=2")

        assertEquals("https://kinogo.parts", response.requestedOrigin)
        assertEquals("https://kinogo.parts", response.resolvedOrigin)
        assertEquals("/фильмы?page=2", URI(response.relativePath).toString().let(URI::create).let {
            buildString {
                append(it.path)
                it.rawQuery?.let { query -> append('?').append(query) }
            }
        })
        assertEquals(source, response.body)
        assertEquals(validated, opened)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `cancellation is propagated unchanged even when cleanup fails`() = runTest {
        val cancellation = CancellationException("test cancellation")
        val connection =
            FakeHttpsConnection(
                responseFailure = cancellation,
                disconnectFailure = IllegalStateException("cleanup failed"),
            )
        val client =
            SafeHtmlClient(
                connectTimeoutMs = 100,
                readTimeoutMs = 100,
                maxRedirects = 1,
                maxBodyBytes = 4_096,
                destinationValidator = {},
                connectionFactory = { connection },
            )

        val actual =
            expectSuspendException<CancellationException> {
                client.get("kinogo.parts", "/")
            }

        // withContext may copy CancellationException for stack-trace recovery; the important
        // contract is that cancellation is not wrapped into CatalogNetworkException.
        assertEquals(cancellation.message, actual.message)
        assertTrue(connection.disconnected)
    }
}

private inline fun <reified T : Throwable> expectException(block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw error
    }
    fail("Expected ${T::class.java.simpleName}")
    error("Unreachable")
}

private suspend inline fun <reified T : Throwable> expectSuspendException(
    crossinline block: suspend () -> Unit,
): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw error
    }
    fail("Expected ${T::class.java.simpleName}")
    error("Unreachable")
}

private class CountingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var readCount: Int = 0
        private set

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        readCount++
        return super.read(buffer, offset, length)
    }
}

private class FakeHttpsConnection(
    private val statusCode: Int = 200,
    private val body: ByteArray = ByteArray(0),
    private val contentTypeValue: String? = "text/html; charset=utf-8",
    private val location: String? = null,
    private val responseFailure: RuntimeException? = null,
    private val disconnectFailure: RuntimeException? = null,
) : HttpsURLConnection(URL("https://kinogo.parts/")) {
    var disconnected: Boolean = false
        private set

    override fun connect() = Unit

    override fun disconnect() {
        disconnected = true
        disconnectFailure?.let { throw it }
    }

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int {
        responseFailure?.let { throw it }
        return statusCode
    }

    override fun getHeaderField(name: String?): String? =
        if (name.equals("Location", ignoreCase = true)) location else null

    override fun getInputStream(): InputStream = ByteArrayInputStream(body)

    override fun getErrorStream(): InputStream = ByteArrayInputStream(body)

    override fun getContentType(): String? = contentTypeValue

    override fun getContentLengthLong(): Long = body.size.toLong()

    override fun getCipherSuite(): String = "test"

    override fun getLocalCertificates(): Array<Certificate>? = null

    override fun getServerCertificates(): Array<Certificate> = emptyArray()
}
