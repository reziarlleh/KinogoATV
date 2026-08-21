package com.kinogo.atv.data.playback

import com.kinogo.atv.data.playback.cinemar.CinemarFolder
import com.kinogo.atv.data.playback.cinemar.CinemarFolderPathEntry
import com.kinogo.atv.data.playback.cinemar.CinemarGrantToken
import com.kinogo.atv.data.playback.cinemar.CinemarParsedCatalog
import com.kinogo.atv.data.playback.cinemar.CinemarStream
import com.kinogo.atv.domain.PlaybackEpisodeCoordinate
import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaUrlResolver
import com.kinogo.atv.domain.PlaybackMediaVariant
import com.kinogo.atv.ui.screens.PlaybackSourceSelectionModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemarDeferredGrantPlaybackPlanTest {
    @Test
    fun `deferred plan keeps sparse cross-season navigation without persisting provider token`() {
        val secret = "fixture-plan-token-do-not-log"
        val streams = listOf(
            stream("s1-e1", 1, 1, secret),
            stream("s1-e3", 1, 3, secret),
            stream("s2-e2", 2, 2, secret),
        )
        val catalog = CinemarParsedCatalog(
            videoId = 105L,
            roots = listOf(
                CinemarFolder(
                    id = "root",
                    title = "Сериал",
                    children = streams,
                ),
            ),
            streams = streams,
        )

        val plan = NativePlaybackPlanMapper.fromCinemar(
            catalog = catalog,
            deferredEmbedUrl = "https://cinemar.cc/embed/105/fixture-offer",
        )

        assertEquals(3, plan.variants.size)
        assertNotNull(plan.mediaUrlResolver)
        assertTrue(plan.variants.all { it.mediaUrl.startsWith("kinogo-cinemar://grant/") })
        assertTrue(plan.variants.all { it.quality == "Авто" })
        assertEquals(
            PlaybackEpisodeCoordinate(seasonNumber = 2, episodeNumber = 2),
            plan.nextEpisodeCoordinate(
                sourceId = "cinemar",
                seasonNumber = 1,
                episodeNumber = 3,
                voiceover = "Дубляж",
            ),
        )
        assertEquals(
            PlaybackEpisodeCoordinate(seasonNumber = 1, episodeNumber = 3),
            plan.previousEpisodeCoordinate(
                sourceId = "cinemar",
                seasonNumber = 2,
                episodeNumber = 2,
                voiceover = "Дубляж",
            ),
        )
        assertFalse(plan.toString().contains(secret))
        assertFalse(plan.toString().contains("cinemar.cc/embed"))
        assertEquals("CinemarDeferredGrantRegistry(<redacted>)", plan.mediaUrlResolver.toString())
    }

    @Test
    fun `each deferred plan owns a distinct resolver session`() {
        val first = NativePlaybackPlanMapper.fromCinemar(
            catalog = singleStreamCatalog("first", "fixture-token-first-do-not-log"),
            deferredEmbedUrl = "https://cinemar.cc/embed/109/first",
        )
        val second = NativePlaybackPlanMapper.fromCinemar(
            catalog = singleStreamCatalog("second", "fixture-token-second-do-not-log"),
            deferredEmbedUrl = "https://cinemar.cc/embed/110/second",
        )

        assertNotNull(first.mediaUrlResolver)
        assertNotNull(second.mediaUrlResolver)
        assertNotSame(first.mediaUrlResolver, second.mediaUrlResolver)
    }

    @Test
    fun `source preference keeps the session resolver attached to reordered plan`() {
        val resolver = object : PlaybackMediaUrlResolver {
            override fun resolveOrNull(mediaUrl: String): String? =
                mediaUrl.takeIf { it.startsWith("kinogo-cinemar:") }
                    ?.let { "https://media.example.test/master.m3u8" }
        }
        val plan = PlaybackMediaPlan(
            variants = listOf(
                filmVariant("source-a", "https://media.example.test/a.m3u8"),
                filmVariant("source-b", "kinogo-cinemar://grant/fixture-ref-01"),
            ),
            mediaUrlResolver = resolver,
        )

        val reordered = PlaybackSourceSelectionModel.preferSource(plan, "source-b")

        assertEquals("source-b", reordered.defaultSourceId)
        assertSame(resolver, reordered.mediaUrlResolver)
    }

    private fun stream(
        id: String,
        season: Int,
        episode: Int,
        token: String,
    ): CinemarStream {
        val seasonFolder = CinemarFolderPathEntry("season-$season", "$season сезон")
        val episodeFolder = CinemarFolderPathEntry("episode-$season-$episode", "$episode серия")
        return CinemarStream(
            id = id,
            title = "Дубляж",
            contextTitle = null,
            providerNodeId = id,
            sourceId = null,
            voiceId = "voice-dub",
            durationMs = null,
            folderPath = listOf(seasonFolder, episodeFolder),
            mediaVariants = emptyList(),
            subtitles = emptyList(),
            grantToken = CinemarGrantToken("$token-$id"),
        )
    }

    private fun singleStreamCatalog(
        id: String,
        token: String,
    ): CinemarParsedCatalog {
        val stream = CinemarStream(
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
        return CinemarParsedCatalog(
            videoId = 109L,
            roots = listOf(stream),
            streams = listOf(stream),
        )
    }

    private fun filmVariant(
        sourceId: String,
        mediaUrl: String,
    ) = PlaybackMediaVariant(
        id = "variant-$sourceId",
        sourceId = sourceId,
        sourceLabel = sourceId,
        episodeNumber = null,
        voiceover = "Дубляж",
        quality = "Авто",
        mediaUrl = mediaUrl,
        mimeType = "application/x-mpegURL",
    )
}
