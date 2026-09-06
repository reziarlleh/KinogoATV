package com.kinogo.atv.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrictHttpsUriTest {
    @Test
    fun `accepts canonical HTTPS URI and explicit port 443`() {
        assertEquals("https://example.com/path?q=1", strictHttpsUriOrNull("https://example.com/path?q=1")?.toString())
        assertEquals("https://example.com:443/path", strictHttpsUriOrNull("https://example.com:443/path")?.toString())
    }

    @Test
    fun `rejects unsafe or ambiguous URI shapes`() {
        listOf(
            "",
            " https://example.com/",
            "https://example.com/ ",
            "https://example.com/\\path",
            "https://example.com/\u0000",
            "http://example.com/",
            "https:opaque",
            "https:///missing-host",
            "https://user@example.com/",
            "https://example.com/#fragment",
            "https://example.com:8443/",
        ).forEach { value ->
            assertNull(value, strictHttpsUriOrNull(value))
        }
    }
}
