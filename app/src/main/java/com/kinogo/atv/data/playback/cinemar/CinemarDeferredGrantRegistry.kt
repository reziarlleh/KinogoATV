package com.kinogo.atv.data.playback.cinemar

import java.io.IOException
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.LinkedHashMap
import java.util.UUID
import com.kinogo.atv.domain.PlaybackMediaUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal fun interface CinemarDeferredGrantLoader {
    suspend fun load(
        embedUrl: String,
        stream: CinemarStream,
    ): CinemarGrantResolution
}

/**
 * Bounded in-memory bridge between an opaque Cinemar playlist leaf and Media3.
 *
 * The public playback plan contains only a random local reference. The provider token and iframe
 * address never enter a MediaItem URI, logs or persistence. The actual grant is requested only
 * when Media3 opens the selected film/episode, so a large series does not issue one POST per
 * translation up front.
 */
internal class CinemarDeferredGrantRegistry(
    private val loader: CinemarDeferredGrantLoader,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val entryTtlMs: Long = DEFAULT_ENTRY_TTL_MS,
) : PlaybackMediaUrlResolver {
    private class Entry(
        val embedUrl: String,
        val stream: CinemarStream,
        val createdAtMs: Long,
    ) {
        /** The completed future caches both success and failure and coalesces concurrent opens. */
        var resolution: CompletableFuture<String>? = null
    }

    constructor() : this(loader = ProductionCinemarDeferredGrantLoader)

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    init {
        require(maxEntries > 0)
        require(entryTtlMs > 0L)
    }

    fun register(
        embedUrl: String,
        stream: CinemarStream,
    ): String {
        require(embedUrl.isNotBlank())
        require(stream.grantToken != null)
        val id = idFactory()
        require(REFERENCE_ID.matches(id))
        val createdAtMs = nowMs()
        synchronized(entries) {
            purgeExpiredLocked(createdAtMs)
            require(id !in entries) { "Deferred Cinemar reference collision" }
            entries[id] = Entry(embedUrl, stream, createdAtMs)
            while (entries.size > maxEntries) {
                entries.entries.iterator().run {
                    next()
                    remove()
                }
            }
        }
        return "$REFERENCE_SCHEME://$REFERENCE_HOST/$id"
    }

    /** Returns null for an ordinary network URI and a fresh HTTPS HLS address for a local grant. */
    @Throws(IOException::class)
    override fun resolveOrNull(mediaUrl: String): String? = resolve(mediaUrl)

    @Throws(IOException::class)
    fun resolve(rawUri: String): String? {
        val id = referenceId(rawUri) ?: return null
        val entry = synchronized(entries) {
            val currentTime = nowMs()
            purgeExpiredLocked(currentTime)
            entries[id]
        } ?: throw IOException("Отложенный источник Cinemar устарел")

        var shouldLoad = false
        val future = synchronized(entry) {
            entry.resolution ?: CompletableFuture<String>().also {
                entry.resolution = it
                shouldLoad = true
            }
        }

        if (shouldLoad) {
            try {
                val resolution = runBlocking(Dispatchers.IO) {
                    loader.load(entry.embedUrl, entry.stream)
                }
                val resolvedUrl = when (resolution) {
                    is CinemarGrantResolution.Ready -> resolution.stream.mediaVariants
                        .firstOrNull { it.kind == CinemarMediaKind.HLS }
                        ?.url
                        ?.valueForPlayback()
                        ?: throw IOException("Cinemar не вернул совместимый HLS-источник")
                    is CinemarGrantResolution.Rejected -> throw IOException(resolution.userMessage)
                }
                future.complete(resolvedUrl)
            } catch (error: Exception) {
                future.completeExceptionally(
                    error as? IOException ?: IOException("Не удалось обновить источник Cinemar"),
                )
            }
        }

        return try {
            future.get()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Обновление источника Cinemar прервано")
        } catch (error: ExecutionException) {
            throw error.cause as? IOException
                ?: IOException("Не удалось обновить источник Cinemar")
        }
    }

    private fun purgeExpiredLocked(currentTimeMs: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (currentTimeMs - entry.createdAtMs >= entryTtlMs) iterator.remove()
        }
    }

    private fun referenceId(rawUri: String): String? {
        val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
        if (!uri.scheme.equals(REFERENCE_SCHEME, ignoreCase = true)) return null
        if (
            !uri.host.equals(REFERENCE_HOST, ignoreCase = true) ||
            uri.rawUserInfo != null ||
            uri.rawQuery != null ||
            uri.rawFragment != null ||
            uri.port != -1
        ) {
            throw IOException("Некорректная ссылка отложенного источника Cinemar")
        }
        val id = uri.rawPath?.removePrefix("/")
            ?.takeIf { it.isNotBlank() && '/' !in it && REFERENCE_ID.matches(it) }
            ?: throw IOException("Некорректная ссылка отложенного источника Cinemar")
        return id
    }

    private companion object {
        const val REFERENCE_SCHEME = "kinogo-cinemar"
        const val REFERENCE_HOST = "grant"
        // The parser already bounds the complete provider document and node count. Matching that
        // node ceiling prevents LRU eviction inside one legitimate large series before selection.
        const val DEFAULT_MAX_ENTRIES = 2_000
        const val DEFAULT_ENTRY_TTL_MS = 6 * 60 * 60 * 1_000L
        val REFERENCE_ID = Regex("[A-Za-z0-9_-]{8,128}")
    }

    override fun toString(): String = "CinemarDeferredGrantRegistry(<redacted>)"
}

/** Shared HTTP machinery only; opaque entries themselves remain owned by one media plan. */
private object ProductionCinemarDeferredGrantLoader : CinemarDeferredGrantLoader {
    private val client = CinemarGrantClient()

    override suspend fun load(
        embedUrl: String,
        stream: CinemarStream,
    ): CinemarGrantResolution = client.load(embedUrl, stream)
}
