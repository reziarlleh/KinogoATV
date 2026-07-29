package com.kinogo.atv.player

/**
 * Classifies the small set of keys that may need temporary ownership by the player root while a
 * newly shown HUD has not transferred focus to its requested control yet.
 */
internal enum class VisibleHudKeyKind {
    PRIMARY,
    SEEK,
    OTHER,
}

/**
 * Returns true only during the short root-focus hand-off window.
 *
 * Once Compose has moved focus to the timeline, [rootFocused] becomes false and the timeline owns
 * D-pad seek itself. This prevents both a lost rapid second key and a duplicate seek.
 */
internal fun shouldDispatchVisibleHudKeyAtRoot(
    rootFocused: Boolean,
    focusTarget: PlayerHudFocusTarget,
    episodeNumberInputActive: Boolean,
    keyKind: VisibleHudKeyKind,
): Boolean = when (keyKind) {
    VisibleHudKeyKind.PRIMARY -> episodeNumberInputActive || (
        rootFocused &&
            (
                focusTarget == PlayerHudFocusTarget.PLAY_PAUSE ||
                    focusTarget == PlayerHudFocusTarget.TIMELINE
                )
        )
    VisibleHudKeyKind.SEEK -> rootFocused && focusTarget == PlayerHudFocusTarget.TIMELINE
    VisibleHudKeyKind.OTHER -> false
}
