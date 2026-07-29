package com.kinogo.atv.data.playback.collaps

import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import com.kinogo.atv.data.playback.CollapsEmbedUrlPolicy
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Converts Collaps' browser-visible player options into a native selection tree.
 *
 * It does not execute provider JavaScript, fetch remote playlist scripts, bypass DRM, or persist
 * expiring media URLs. The caller must fetch a fresh embed document immediately before playback.
 */
class CollapsNativePlaybackAdapter internal constructor(
    private val destinationValidator: (URI) -> Unit,
) {
    constructor() : this(collapsNativeDestinationValidator())

    suspend fun resolve(
        embedUrl: String,
        html: String,
    ): CollapsNativePlaybackResult {
        val embedUri = CollapsEmbedUrlPolicy.validatedEmbedUri(embedUrl)
        if (embedUri == null) {
            return CollapsNativePlaybackResult.Rejected(
                CollapsNativeRejection.INVALID_EMBED_URL,
            )
        }

        val parsed = try {
            CollapsPlayerConfigParser.parse(html)
        } catch (_: CollapsConfigTooLargeException) {
            return CollapsNativePlaybackResult.Rejected(CollapsNativeRejection.CONFIG_TOO_LARGE)
        } catch (_: CollapsConfigNotFoundException) {
            return CollapsNativePlaybackResult.Rejected(CollapsNativeRejection.CONFIG_NOT_FOUND)
        } catch (_: CollapsRemotePlaylistException) {
            return CollapsNativePlaybackResult.Rejected(
                CollapsNativeRejection.REMOTE_PLAYLIST_UNSUPPORTED,
            )
        } catch (_: CollapsBlockedException) {
            return CollapsNativePlaybackResult.Rejected(CollapsNativeRejection.BLOCKED)
        } catch (_: CollapsNoPlayableItemsException) {
            return CollapsNativePlaybackResult.Rejected(
                CollapsNativeRejection.NO_PLAYABLE_ITEMS,
            )
        } catch (_: CollapsUnsafeUrlException) {
            return CollapsNativePlaybackResult.Rejected(
                CollapsNativeRejection.UNSAFE_DESTINATION,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CollapsNativePlaybackResult.Rejected(CollapsNativeRejection.MALFORMED_CONFIG)
        }

        return try {
            withContext(Dispatchers.IO) {
                sequence {
                    yield(embedUri)
                    parsed.playableItems.forEach { item ->
                        item.streams.forEach { yield(it.uri) }
                        item.subtitles.forEach { yield(it.uri) }
                    }
                }
                    .distinct()
                    .forEach { uri ->
                        currentCoroutineContext().ensureActive()
                        destinationValidator(uri)
                    }
            }
            CollapsNativePlaybackResult.Ready(parsed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // URLs and exception messages are deliberately omitted: query strings are transient.
            CollapsNativePlaybackResult.Rejected(CollapsNativeRejection.UNSAFE_DESTINATION)
        }
    }
}

private fun collapsNativeDestinationValidator(): (URI) -> Unit {
    val dns = ResilientPublicDns()
    return { uri -> NetworkDestinationValidator.validateHttpsPublic(uri, dns) }
}
