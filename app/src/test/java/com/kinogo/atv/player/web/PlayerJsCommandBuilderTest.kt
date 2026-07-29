package com.kinogo.atv.player.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlayerJsCommandBuilderTest {
    @Test
    fun `builder exposes only documented fixed transport commands`() {
        val expected = mapOf(
            PlayerJsCommand.Play to "player.api('play')",
            PlayerJsCommand.Pause to "player.api('pause')",
            PlayerJsCommand.Toggle to "player.api('toggle')",
            PlayerJsCommand.Stop to "player.api('stop')",
            PlayerJsCommand.Previous to "player.api('prev')",
            PlayerJsCommand.Next to "player.api('next')",
        )

        expected.forEach { (command, apiCall) ->
            val javascript = PlayerJsCommandBuilder.javascript(command)
            assertTrue(javascript.contains(apiCall.replace("player", "api")))
            assertTrue(javascript.startsWith("(()=>{try{"))
            assertTrue(javascript.contains("document.querySelector('video')"))
            assertFalse(javascript.contains("http"))
        }
    }

    @Test
    fun `relative seek reads current time and clamps at zero`() {
        val backward = PlayerJsCommandBuilder.javascript(PlayerJsCommand.SeekRelative(-30))
        val forward = PlayerJsCommandBuilder.javascript(PlayerJsCommand.SeekRelative(15))

        assertTrue(backward.contains("Number(api?api.api('time'):(media?media.currentTime:NaN))"))
        assertTrue(backward.contains("Math.max(0,current-30)"))
        assertTrue(forward.contains("Math.max(0,current+15)"))
        assertTrue(forward.contains("media.currentTime=target"))
    }

    @Test
    fun `relative seek rejects zero and unreasonable values`() {
        listOf(0, -601, 601).forEach { seconds ->
            try {
                PlayerJsCommand.SeekRelative(seconds)
                fail("Expected $seconds to be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }
}
