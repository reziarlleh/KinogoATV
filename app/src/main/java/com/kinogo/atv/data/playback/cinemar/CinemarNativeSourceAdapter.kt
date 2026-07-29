package com.kinogo.atv.data.playback.cinemar

import com.kinogo.atv.data.mirror.NetworkDestinationValidator
import com.kinogo.atv.data.network.ResilientPublicDns
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns already-fetched Cinemar HTML into an ephemeral, remote-friendly playback catalog.
 *
 * Fetching remains outside this class so the caller can attach the fresh gateway-derived Referer
 * and cookies. The adapter does not execute JavaScript and does not persist or log media URLs.
 */
class CinemarNativeSourceAdapter private constructor(
    private val parser: CinemarPublicConfigParser,
    private val destinationValidator: (URI) -> Unit,
) {
    constructor() : this(
        parser = CinemarPublicConfigParser(),
        destinationValidator = cinemarPublicDestinationValidator(),
    )

    internal constructor(
        destinationValidator: (URI) -> Unit,
    ) : this(
        parser = CinemarPublicConfigParser(),
        destinationValidator = destinationValidator,
    )

    suspend fun resolve(
        embedUrl: String,
        html: String,
    ): CinemarNativeResolution {
        val parsed = when (val result = parser.parse(embedUrl, html)) {
            is CinemarConfigParseResult.Parsed -> result
            is CinemarConfigParseResult.Rejected -> {
                return CinemarNativeResolution.Rejected(result.code)
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val destinations = linkedMapOf<String, URI>()
                fun addDestination(uri: URI) {
                    destinations.putIfAbsent(uri.toASCIIString(), uri)
                }
                addDestination(parsed.embedUri)
                parsed.catalog.streams.forEach { stream ->
                    stream.mediaVariants.forEach { addDestination(it.url.asUri()) }
                    stream.subtitles.forEach { addDestination(it.url.asUri()) }
                }
                destinations.values.forEach(destinationValidator)
                CinemarNativeResolution.Ready(parsed.catalog)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CinemarNativeResolution.Rejected(
                    CinemarNativeFailureCode.UNSAFE_NETWORK_DESTINATION,
                )
            }
        }
    }
}

private fun cinemarPublicDestinationValidator(): (URI) -> Unit {
    val dns = ResilientPublicDns()
    return { uri -> NetworkDestinationValidator.validateHttpsPublic(uri, dns) }
}
