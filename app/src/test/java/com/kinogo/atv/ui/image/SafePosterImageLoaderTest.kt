package com.kinogo.atv.ui.image

import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafePosterImageLoaderTest {
    @Test
    fun `poster client refuses redirects and uses bounded timeouts`() {
        val client = SafePosterImageLoader.buildClient(
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(InetAddress.getByName("8.8.8.8"))
            },
        )

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertEquals(7_000, client.connectTimeoutMillis)
        assertEquals(12_000, client.readTimeoutMillis)
    }
}
