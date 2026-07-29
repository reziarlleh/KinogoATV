package com.kinogo.atv.player

import android.view.KeyEvent

/** Converts Android TV/remote key events into platform-independent player intents. */
class TvPlayerKeyMapper {
    fun map(event: KeyEvent): PlayerIntent? = map(
        keyCode = event.keyCode,
        action = event.action,
        repeatCount = event.repeatCount,
        eventTimeMs = event.eventTime,
    )

    /** Primitive overload keeps local JVM tests independent from constructing Android stubs. */
    fun map(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        eventTimeMs: Long,
    ): PlayerIntent? {
        if (action != KeyEvent.ACTION_DOWN) return null
        val repeatable = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
            keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        if (repeatCount > 0 && !repeatable) return null

        digitForKeyCode(keyCode)?.let { digit ->
            return PlayerIntent.EpisodeDigit(digit, eventTimeMs)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            -> PlayerIntent.PrimaryAction(eventTimeMs)

            KeyEvent.KEYCODE_DPAD_LEFT -> PlayerIntent.Seek(
                SeekDirection.BACKWARD,
                RemoteKeySource.DPAD,
                eventTimeMs,
            )
            KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerIntent.Seek(
                SeekDirection.FORWARD,
                RemoteKeySource.DPAD,
                eventTimeMs,
            )
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            -> PlayerIntent.ShowHud(eventTimeMs)

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_BUTTON_START,
            -> PlayerIntent.TogglePlayback(eventTimeMs)
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerIntent.Play(eventTimeMs)
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerIntent.Pause(eventTimeMs)
            KeyEvent.KEYCODE_MEDIA_STOP -> PlayerIntent.Stop
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlayerIntent.NextEpisode
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlayerIntent.PreviousEpisode
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            -> PlayerIntent.Seek(
                SeekDirection.BACKWARD,
                RemoteKeySource.MEDIA_KEY,
                eventTimeMs,
            )
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            -> PlayerIntent.Seek(
                SeekDirection.FORWARD,
                RemoteKeySource.MEDIA_KEY,
                eventTimeMs,
            )

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_B,
            -> PlayerIntent.Back
            else -> null
        }
    }

    private fun digitForKeyCode(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
            keyCode - KeyEvent.KEYCODE_NUMPAD_0
        }
        else -> null
    }
}
