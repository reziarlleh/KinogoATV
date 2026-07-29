package com.kinogo.atv.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvPlayerKeyMapperTest {
    private val mapper = TvPlayerKeyMapper()

    @Test
    fun `maps every TV OK variant to primary action`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_SELECT,
        ).forEach { keyCode ->
            assertEquals(PlayerIntent.PrimaryAction(100L), key(keyCode))
        }
    }

    @Test
    fun `maps arrows and HUD keys`() {
        assertEquals(
            PlayerIntent.Seek(SeekDirection.BACKWARD, RemoteKeySource.DPAD, 100L),
            key(KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            PlayerIntent.Seek(SeekDirection.FORWARD, RemoteKeySource.DPAD, 100L),
            key(KeyEvent.KEYCODE_DPAD_RIGHT),
        )
        assertEquals(PlayerIntent.ShowHud(100L), key(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(PlayerIntent.ShowHud(100L), key(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `maps dedicated media transport keys`() {
        assertEquals(PlayerIntent.TogglePlayback(100L), key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(PlayerIntent.Play(100L), key(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(PlayerIntent.Pause(100L), key(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(PlayerIntent.Stop, key(KeyEvent.KEYCODE_MEDIA_STOP))
        assertEquals(PlayerIntent.NextEpisode, key(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(PlayerIntent.PreviousEpisode, key(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(
            PlayerIntent.Seek(SeekDirection.FORWARD, RemoteKeySource.MEDIA_KEY, 100L),
            key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
        )
        assertEquals(
            PlayerIntent.Seek(SeekDirection.BACKWARD, RemoteKeySource.MEDIA_KEY, 100L),
            key(KeyEvent.KEYCODE_MEDIA_REWIND),
        )
    }

    @Test
    fun `maps regular and numpad digits to episode input`() {
        assertEquals(PlayerIntent.EpisodeDigit(7, 100L), key(KeyEvent.KEYCODE_7))
        assertEquals(PlayerIntent.EpisodeDigit(4, 100L), key(KeyEvent.KEYCODE_NUMPAD_4))
    }

    @Test
    fun `maps back variants and ignores unrelated key`() {
        assertEquals(PlayerIntent.Back, key(KeyEvent.KEYCODE_BACK))
        assertEquals(PlayerIntent.Back, key(KeyEvent.KEYCODE_BUTTON_B))
        assertNull(key(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun `ignores key up and non-repeatable repeats but keeps seek repeats`() {
        assertNull(
            mapper.map(
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.ACTION_UP,
                0,
                100L,
            ),
        )
        assertNull(
            mapper.map(
                KeyEvent.KEYCODE_5,
                KeyEvent.ACTION_DOWN,
                1,
                100L,
            ),
        )
        assertEquals(
            PlayerIntent.Seek(SeekDirection.FORWARD, RemoteKeySource.DPAD, 100L),
            mapper.map(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.ACTION_DOWN,
                2,
                100L,
            ),
        )
    }

    private fun key(keyCode: Int): PlayerIntent? = mapper.map(
        keyCode = keyCode,
        action = KeyEvent.ACTION_DOWN,
        repeatCount = 0,
        eventTimeMs = 100L,
    )
}
