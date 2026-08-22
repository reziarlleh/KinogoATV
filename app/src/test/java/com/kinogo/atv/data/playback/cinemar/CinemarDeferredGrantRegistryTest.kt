package com.kinogo.atv.data.playback.cinemar

import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CinemarDeferredGrantRegistryTest {
    @Test
    fun `registration is network-lazy and only the selected reference loads a grant`() {
        val calls = mutableListOf<Pair<String, String>>()
        val ids = ArrayDeque(listOf("grant-ref-01", "grant-ref-02"))
        val registry = CinemarDeferredGrantRegistry(
            loader = CinemarDeferredGrantLoader { embedUrl, stream ->
                calls += embedUrl to stream.id
                ready(stream, "https://media.example.test/${stream.id}/master.m3u8")
            },
            idFactory = ids::removeFirst,
            maxEntries = 4,
        )

        val first = registry.register(
            embedUrl = "https://cinemar.cc/embed/101/offer-a",
            stream = deferredStream("voice-a", "fixture-token-a-do-not-log"),
        )
        val selected = registry.register(
            embedUrl = "https://cinemar.cc/embed/101/offer-b",
            stream = deferredStream("voice-b", "fixture-token-b-do-not-log"),
        )

        assertTrue(first.startsWith("kinogo-cinemar://grant/"))
        assertEquals(0, calls.size)
        assertNull(registry.resolve("https://media.example.test/already-resolved/master.m3u8"))
        assertEquals(0, calls.size)

        assertEquals(
            "https://media.example.test/voice-b/master.m3u8",
            registry.resolve(selected),
        )
        // HLS may reopen the original manifest DataSpec. The opaque provider grant is issued once
        // for the session entry rather than once for every Media3 retry/playlist refresh.
        assertEquals(
            "https://media.example.test/voice-b/master.m3u8",
            registry.resolve(selected),
        )
        assertEquals(
            listOf("https://cinemar.cc/embed/101/offer-b" to "voice-b"),
            calls,
        )
        assertFalse(selected.contains("fixture-token"))
    }

    @Test
    fun `concurrent opens share one in-flight grant request`() {
        val loaderEntered = CountDownLatch(1)
        val allowLoaderToFinish = CountDownLatch(1)
        val calls = AtomicInteger()
        val registry = CinemarDeferredGrantRegistry(
            loader = CinemarDeferredGrantLoader { _, stream ->
                calls.incrementAndGet()
                loaderEntered.countDown()
                assertTrue(allowLoaderToFinish.await(5, TimeUnit.SECONDS))
                ready(stream, "https://media.example.test/shared/master.m3u8")
            },
            idFactory = { "concurrent-ref-01" },
        )
        val reference = registry.register(
            "https://cinemar.cc/embed/106/offer",
            deferredStream("concurrent", "fixture-concurrent-token-do-not-log"),
        )
        val executor = Executors.newFixedThreadPool(8)

        try {
            val opens = List(8) {
                executor.submit<String> { requireNotNull(registry.resolve(reference)) }
            }
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS))
            allowLoaderToFinish.countDown()

            assertEquals(
                List(8) { "https://media.example.test/shared/master.m3u8" },
                opens.map { it.get(5, TimeUnit.SECONDS) },
            )
            assertEquals(1, calls.get())
        } finally {
            allowLoaderToFinish.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `failed grant is memoized with a redacted error for the active session`() {
        val calls = AtomicInteger()
        val registry = CinemarDeferredGrantRegistry(
            loader = CinemarDeferredGrantLoader { _, _ ->
                calls.incrementAndGet()
                CinemarGrantResolution.Rejected(CinemarGrantFailureCode.NETWORK_ERROR)
            },
            idFactory = { "failed-ref-01" },
        )
        val reference = registry.register(
            "https://cinemar.cc/embed/107/offer",
            deferredStream("failed", "fixture-failed-token-do-not-log"),
        )

        val first = expectIOException { registry.resolve(reference) }
        val second = expectIOException { registry.resolve(reference) }

        assertEquals(1, calls.get())
        assertEquals("Не удалось обновить источник Cinemar", first.message)
        assertEquals(first.message, second.message)
        assertFalse(first.message.orEmpty().contains("fixture-failed-token"))
    }

    @Test
    fun `expired reference is rejected before the opaque token reaches loader`() {
        var nowMs = 100L
        var calls = 0
        val registry = CinemarDeferredGrantRegistry(
            loader = CinemarDeferredGrantLoader { _, stream ->
                calls++
                ready(stream, "https://media.example.test/master.m3u8")
            },
            nowMs = { nowMs },
            idFactory = { "expired-ref-01" },
            entryTtlMs = 1_000L,
        )
        val reference = registry.register(
            "https://cinemar.cc/embed/102/offer",
            deferredStream("voice-expired", "fixture-expired-token-do-not-log"),
        )

        nowMs = 1_100L
        val error = expectIOException { registry.resolve(reference) }

        assertEquals(0, calls)
        assertEquals("Отложенный источник Cinemar устарел", error.message)
        assertFalse(error.message.orEmpty().contains("fixture-expired-token"))
    }

    @Test
    fun `bounded registry evicts least recently used reference`() {
        val calls = mutableListOf<String>()
        val counter = AtomicInteger()
        val registry = CinemarDeferredGrantRegistry(
            loader = CinemarDeferredGrantLoader { _, stream ->
                calls += stream.id
                ready(stream, "https://media.example.test/${stream.id}/master.m3u8")
            },
            idFactory = { "bounded-ref-${counter.incrementAndGet()}" },
            maxEntries = 2,
        )
        val first = registry.register(
            "https://cinemar.cc/embed/103/first",
            deferredStream("first", "fixture-token-first-do-not-log"),
        )
        val second = registry.register(
            "https://cinemar.cc/embed/103/second",
            deferredStream("second", "fixture-token-second-do-not-log"),
        )

        // Resolving is also an access, so the second reference becomes the LRU entry.
        assertEquals("https://media.example.test/first/master.m3u8", registry.resolve(first))
        val third = registry.register(
            "https://cinemar.cc/embed/103/third",
            deferredStream("third", "fixture-token-third-do-not-log"),
        )

        val evicted = expectIOException { registry.resolve(second) }
        assertEquals("Отложенный источник Cinemar устарел", evicted.message)
        assertEquals("https://media.example.test/third/master.m3u8", registry.resolve(third))
        assertEquals(listOf("first", "third"), calls)
    }

    @Test
    fun `default bound retains one complete parser-sized catalog`() {
        val counter = AtomicInteger()
        val calls = AtomicInteger()
        val registry = CinemarDeferredGrantRegistry(
            loader = CinemarDeferredGrantLoader { _, stream ->
                calls.incrementAndGet()
                ready(stream, "https://media.example.test/${stream.id}/master.m3u8")
            },
            idFactory = { "parser-node-${counter.incrementAndGet().toString().padStart(4, '0')}" },
        )
        var firstReference = ""
        repeat(2_000) { index ->
            val reference = registry.register(
                "https://cinemar.cc/embed/108/offer",
                deferredStream(
                    id = "node-$index",
                    token = "fixture-parser-token-$index-do-not-log",
                ),
            )
            if (index == 0) firstReference = reference
        }

        assertEquals(
            "https://media.example.test/node-0/master.m3u8",
            registry.resolve(firstReference),
        )
        assertEquals(1, calls.get())
    }

    @Test
    fun `malformed local reference is rejected without echoing its query`() {
        var calls = 0
        val registry = CinemarDeferredGrantRegistry(
            loader = CinemarDeferredGrantLoader { _, _ ->
                calls++
                CinemarGrantResolution.Rejected(CinemarGrantFailureCode.HTTP_ERROR)
            },
        )
        val raw = "kinogo-cinemar://grant/reference-01?token=fixture-secret-do-not-log"

        val error = expectIOException { registry.resolve(raw) }

        assertEquals(0, calls)
        assertEquals("Некорректная ссылка отложенного источника Cinemar", error.message)
        assertFalse(error.message.orEmpty().contains("fixture-secret"))
    }

    @Test
    fun `non-HLS grant fails closed without exposing returned media address`() {
        val registry = CinemarDeferredGrantRegistry(
            loader = CinemarDeferredGrantLoader { _, stream ->
                CinemarGrantResolution.Ready(
                    stream.copy(
                        mediaVariants = listOf(
                            CinemarMediaVariant(
                                id = "${stream.id}:dash",
                                label = "Авто",
                                kind = CinemarMediaKind.DASH,
                                url = CinemarTransientUrl(
                                    URI("https://media.example.test/fixture-secret/manifest.mpd"),
                                ),
                            ),
                        ),
                        grantToken = null,
                    ),
                )
            },
            idFactory = { "dash-only-ref" },
        )
        val reference = registry.register(
            "https://cinemar.cc/embed/104/offer",
            deferredStream("dash-only", "fixture-token-dash-do-not-log"),
        )

        val error = expectIOException { registry.resolve(reference) }

        assertEquals("Cinemar не вернул совместимый HLS-источник", error.message)
        assertFalse(error.message.orEmpty().contains("media.example.test"))
        assertFalse(error.message.orEmpty().contains("fixture-secret"))
    }

    private fun deferredStream(
        id: String,
        token: String,
    ) = CinemarStream(
        id = id,
        title = "Дубляж",
        contextTitle = null,
        providerNodeId = id,
        sourceId = null,
        voiceId = id,
        durationMs = null,
        folderPath = emptyList(),
        mediaVariants = emptyList(),
        subtitles = emptyList(),
        grantToken = CinemarGrantToken(token),
    )

    private fun ready(
        stream: CinemarStream,
        mediaUrl: String,
    ) = CinemarGrantResolution.Ready(
        stream.copy(
            mediaVariants = listOf(
                CinemarMediaVariant(
                    id = "${stream.id}:hls",
                    label = "Авто",
                    kind = CinemarMediaKind.HLS,
                    url = CinemarTransientUrl(URI(mediaUrl)),
                ),
            ),
            grantToken = null,
        ),
    )

    private fun expectIOException(block: () -> Unit): IOException = try {
        block()
        fail("Expected IOException")
        throw AssertionError("unreachable")
    } catch (error: IOException) {
        error
    }
}
