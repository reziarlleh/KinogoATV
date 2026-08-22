package com.kinogo.atv.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.kinogo.atv.domain.SubtitlePreference
import com.kinogo.atv.domain.TvPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlayerPreferencesTest {
    @Test
    fun `persisted seek step becomes reducer seek step`() {
        val config = TvPreferences(seekStepSeconds = 30).toPlayerReducerConfig()

        assertEquals(30_000L, config.seekStepMs)
        val reduction = TvPlayerReducer(config).reduce(
            TvPlayerState(),
            PlayerIntent.Seek(SeekDirection.FORWARD, RemoteKeySource.DPAD, 100L),
        )
        assertEquals(PlayerEffect.SeekRelative(30_000L), reduction.effects.first())
    }

    @Test
    fun `enabled subtitles select any text track including undetermined language`() {
        val parameters = TrackSelectionParameters.DEFAULT.withSubtitlePreference(
            preference = SubtitlePreference.ENABLED,
            systemCaptionsEnabled = false,
        )

        assertFalse(C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes)
        assertTrue(parameters.selectTextByDefault)
        assertTrue(parameters.selectUndeterminedTextLanguage)
    }

    @Test
    fun `system subtitles retain normal selection and follow system enablement`() {
        val enabled = TrackSelectionParameters.DEFAULT.withSubtitlePreference(
            preference = SubtitlePreference.SYSTEM,
            systemCaptionsEnabled = true,
        )
        val disabled = TrackSelectionParameters.DEFAULT.withSubtitlePreference(
            preference = SubtitlePreference.SYSTEM,
            systemCaptionsEnabled = false,
        )

        assertFalse(C.TRACK_TYPE_TEXT in enabled.disabledTrackTypes)
        assertFalse(enabled.selectTextByDefault)
        assertFalse(enabled.selectUndeterminedTextLanguage)
        assertTrue(C.TRACK_TYPE_TEXT in disabled.disabledTrackTypes)
    }

    @Test
    fun `disabled subtitles block text tracks and clear forced selection`() {
        val initiallyForced = TrackSelectionParameters.DEFAULT
            .withSubtitlesEnabled(true)

        val disabled = initiallyForced.withSubtitlePreference(
            preference = SubtitlePreference.DISABLED,
            systemCaptionsEnabled = true,
        )

        assertTrue(C.TRACK_TYPE_TEXT in disabled.disabledTrackTypes)
        assertFalse(disabled.selectTextByDefault)
        assertFalse(disabled.selectUndeterminedTextLanguage)
    }

    @Test
    fun `in-player subtitle switch selects a real track when re-enabled`() {
        val disabled = TrackSelectionParameters.DEFAULT.withSubtitlesEnabled(false)
        val enabled = disabled.withSubtitlesEnabled(true)

        assertFalse(C.TRACK_TYPE_TEXT in enabled.disabledTrackTypes)
        assertTrue(enabled.selectTextByDefault)
        assertTrue(enabled.selectUndeterminedTextLanguage)
    }

    @Test
    fun `fixed quality intent applies playlist wide adaptive cap`() {
        val fixed = DefaultTrackSelector.Parameters.DEFAULT
            .withVideoQualityIntent("WEB-DL 1080p")

        assertEquals(1080, fixed.maxVideoHeight)
        assertEquals(Int.MAX_VALUE, fixed.maxVideoWidth)
        assertTrue((fixed as DefaultTrackSelector.Parameters).exceedVideoConstraintsIfNecessary)
    }

    @Test
    fun `automatic quality removes adaptive cap for following playlist items`() {
        val automatic = DefaultTrackSelector.Parameters.DEFAULT
            .withVideoQualityIntent("Авто")

        assertEquals(Int.MAX_VALUE, automatic.maxVideoHeight)
        assertEquals(Int.MAX_VALUE, automatic.maxVideoWidth)
        assertTrue(
            (automatic as DefaultTrackSelector.Parameters).exceedVideoConstraintsIfNecessary,
        )
    }
}
