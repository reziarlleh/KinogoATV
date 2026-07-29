package com.kinogo.atv.data.playback.cinemar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemarPublicConfigParserTest {
    private val parser = CinemarPublicConfigParser()

    @Test
    fun `does not confuse text inside a JavaScript string with the call`() {
        val html = """
            <script>
              const decoy = "Cinemar({not: 'json'})";
              const actual = Cinemar({"vid":44,"file":[
                {"id":"ok","voice_id":1,"title":"Voice",
                 "file":"https://media.example.test/video/master.m3u8","subtitle":""}
              ]});
            </script>
        """.trimIndent()

        val result = parser.parse("https://provider.example/embed/44", html)

        assertTrue(result is CinemarConfigParseResult.Parsed)
    }

    @Test
    fun `rejects malformed packed payload without exposing it`() {
        val secretMarker = "signed-secret-do-not-leak"
        val html = """
            <script>
              Cinemar({"vid":45,"file":"#236${secretMarker}x"});
            </script>
        """.trimIndent()

        val result = parser.parse("https://provider.example/embed/45", html)

        assertEquals(
            CinemarNativeFailureCode.MALFORMED_CONFIG,
            (result as CinemarConfigParseResult.Rejected).code,
        )
        assertTrue(!result.toString().contains(secretMarker))
    }

    @Test
    fun `applies a hard document size limit before parsing`() {
        val result = parser.parse(
            rawEmbedUrl = "https://provider.example/embed/46",
            html = "x".repeat(2 * 1_024 * 1_024 + 1),
        )

        assertEquals(
            CinemarNativeFailureCode.DOCUMENT_TOO_LARGE,
            (result as CinemarConfigParseResult.Rejected).code,
        )
    }
}
