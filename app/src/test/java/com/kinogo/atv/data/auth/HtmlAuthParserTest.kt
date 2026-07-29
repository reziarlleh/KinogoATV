package com.kinogo.atv.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlAuthParserTest {
    @Test
    fun guestGroupIsNotAuthenticated() {
        val result = HtmlAuthParser.parse(
            """
            <html><body><form><input name="login_password"></form>
            <script>var dle_group = 5; var dle_login_hash = 'guest-hash';</script></body></html>
            """.trimIndent(),
        )

        assertFalse(result.isAuthenticated)
        assertEquals("guest-hash", result.loginHash)
    }

    @Test
    fun registeredGroupAndHashCreateAuthenticatedSnapshot() {
        val result = HtmlAuthParser.parse(
            """
            <html><body><span class="loginUserName">tester</span>
            <script>var dle_group = 4; var dle_login_hash = 'fresh-token';</script></body></html>
            """.trimIndent(),
        )

        assertTrue(result.isAuthenticated)
        assertEquals("fresh-token", result.loginHash)
        assertEquals("tester", result.displayName)
    }

    @Test
    fun explicitLoginErrorIsExtracted() {
        val result = HtmlAuthParser.parse(
            "<div class=berrors>Неверный пароль</div><script>var dle_group=5;</script>",
        )

        assertFalse(result.isAuthenticated)
        assertEquals("Неверный пароль", result.errorMessage)
        assertNull(result.displayName)
    }
}
