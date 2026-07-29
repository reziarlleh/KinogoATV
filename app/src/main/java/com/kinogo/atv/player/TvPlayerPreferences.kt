package com.kinogo.atv.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import com.kinogo.atv.domain.SubtitlePreference
import com.kinogo.atv.domain.TvPreferences

internal fun TvPreferences.toPlayerReducerConfig(): PlayerReducerConfig =
    PlayerReducerConfig(seekStepMs = seekStepMs)

/**
 * Applies the persisted subtitle mode to Media3's track selector.
 *
 * Merely enabling the text renderer is not enough: Media3 normally leaves a text track
 * unselected when it has neither a preferred language nor default/forced metadata. The explicit
 * ENABLED mode therefore asks Media3 to select any text track and also accepts an undetermined
 * language. SYSTEM keeps Media3's normal selection rules, while DISABLED blocks the renderer.
 */
internal fun TrackSelectionParameters.withSubtitlePreference(
    preference: SubtitlePreference,
    systemCaptionsEnabled: Boolean,
): TrackSelectionParameters {
    val enabled = preference.textTrackEnabled(systemCaptionsEnabled)
    val forceAnyTextTrack = preference == SubtitlePreference.ENABLED
    return buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
        .setSelectTextByDefault(forceAnyTextTrack)
        .setSelectUndeterminedTextLanguage(forceAnyTextTrack)
        .build()
}

/** Applies the in-player On/Off switch. Re-enabling must select an actual subtitle track. */
internal fun TrackSelectionParameters.withSubtitlesEnabled(
    enabled: Boolean,
): TrackSelectionParameters = buildUpon()
    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
    .setSelectTextByDefault(enabled)
    .setSelectUndeterminedTextLanguage(enabled)
    .build()
