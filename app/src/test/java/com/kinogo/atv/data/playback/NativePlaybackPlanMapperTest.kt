package com.kinogo.atv.data.playback

import com.kinogo.atv.data.playback.cinemar.CinemarNativeResolution
import com.kinogo.atv.data.playback.cinemar.CinemarNativeSourceAdapter
import com.kinogo.atv.data.playback.collaps.CollapsNativePlaybackAdapter
import com.kinogo.atv.data.playback.collaps.CollapsNativePlaybackResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlaybackPlanMapperTest {
    @Test
    fun `maps Cinemar hierarchy to source season episode voice quality and subtitles`() = runTest {
        val parsed = CinemarNativeSourceAdapter { }.resolve(
            embedUrl = "https://cinemar-fixture.example/embed/9002",
            html = fixture("cinemar/series_v2.html"),
        ) as CinemarNativeResolution.Ready

        val plan = NativePlaybackPlanMapper.fromCinemar(parsed.catalog)

        assertEquals(listOf("cinemar"), plan.sourceOptions.map { it.id })
        assertEquals(listOf(1), plan.seasonNumbersFor("cinemar"))
        assertEquals(listOf(1, 2), plan.episodeNumbersFor("cinemar", 1))
        assertTrue(plan.variants.all { it.sourceLabel == "Cinemar" })
    }

    @Test
    fun `maps Collaps audio order and external subtitles without leaking URLs`() = runTest {
        val parsed = CollapsNativePlaybackAdapter { }.resolve(
            embedUrl = "https://api.ortified.ws/embed/movie/42",
            html = fixture("collaps_movie_public_config.html"),
        ) as CollapsNativePlaybackResult.Ready

        val plan = NativePlaybackPlanMapper.fromCollaps(parsed.catalog)

        assertFalse(plan.isEpisodic)
        assertEquals(listOf("collaps"), plan.sourceOptions.map { it.id })
        assertEquals(listOf("Дубляж", "Оригинал"), plan.voiceoversFor(null))
        assertEquals(setOf(1, 0), plan.variants.mapNotNull { it.preferredAudioTrackIndex }.toSet())
        assertNotNull(plan.variants.first().subtitleTracks.singleOrNull())
        assertFalse(plan.toString().contains("session="))
    }

    @Test
    fun `merges compatible providers into one source selector`() = runTest {
        val cinemar = CinemarNativeSourceAdapter { }.resolve(
            "https://cinemar-fixture.example/embed/9001",
            fixture("cinemar/movie_v2.html"),
        ) as CinemarNativeResolution.Ready
        val collaps = CollapsNativePlaybackAdapter { }.resolve(
            "https://api.ortified.ws/embed/movie/42",
            fixture("collaps_movie_public_config.html"),
        ) as CollapsNativePlaybackResult.Ready

        val plan = NativePlaybackPlanMapper.merge(
            listOf(
                NativePlaybackPlanMapper.fromCinemar(cinemar.catalog),
                NativePlaybackPlanMapper.fromCollaps(collaps.catalog),
            ),
        )

        assertEquals(listOf("cinemar", "collaps"), plan.sourceOptions.map { it.id })
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/playback/$name")).readText()
}
