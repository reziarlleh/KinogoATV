package com.kinogo.atv.data.catalog

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.Charset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CatalogHtmlPolicyTest {
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
