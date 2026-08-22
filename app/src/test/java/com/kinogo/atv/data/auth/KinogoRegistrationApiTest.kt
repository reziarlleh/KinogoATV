package com.kinogo.atv.data.auth

import com.kinogo.atv.data.catalog.SessionBinaryResponse
import com.kinogo.atv.data.catalog.SessionHttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KinogoRegistrationApiTest {
    @Test
    fun `rules gate is never posted until user explicitly accepts`() = runTest {
        val transport = FakeRegistrationTransport(
            initialHtml = rulesHtml(),
            submitHtml = formHtml(),
        )
        val api = KinogoRegistrationApi(transport)

        val initial = api.load(ORIGIN)

        assertTrue(initial is RegistrationLoadResult.ConsentRequired)
        assertEquals(null, transport.submitted)

        val accepted = api.acceptRules(
            (initial as RegistrationLoadResult.ConsentRequired).page,
        )

        assertTrue(accepted is RegistrationLoadResult.Ready)
        assertEquals("register", transport.submitted?.get("do"))
        assertEquals("yes", transport.submitted?.get("dle_rules_accept"))
        assertEquals(2, transport.submitted?.size)
    }

    @Test
    fun `load keeps captcha in the origin cookie transport and validates bitmap`() = runTest {
        val transport = FakeRegistrationTransport(formHtml())
        val api = KinogoRegistrationApi(transport)

        val result = api.load(ORIGIN)

        assertTrue(result is RegistrationLoadResult.Ready)
        val page = (result as RegistrationLoadResult.Ready).page
        assertEquals("image/png", page.captchaImage?.mimeType)
        assertEquals("/engine/modules/antibot/antibot.php", transport.binaryPath)
        assertFalse(page.toString().contains("token-secret"))
    }

    @Test
    fun `submit sends dynamic DLE fields and reports completion`() = runTest {
        val transport = FakeRegistrationTransport(
            initialHtml = formHtml(),
            submitHtml = "<div class='information'>Регистрация прошла успешно</div>",
        )
        val api = KinogoRegistrationApi(transport)
        val page = (api.load(ORIGIN) as RegistrationLoadResult.Ready).page

        val result = api.submit(
            page,
            RegistrationInput(
                login = "viewer",
                email = "viewer@example.test",
                password = "password-123",
                passwordConfirmation = "password-123",
                captchaText = "1234",
            ),
        )

        assertTrue(result is RegistrationSubmitResult.Completed)
        assertEquals("viewer", transport.submitted?.get("name"))
        assertEquals("viewer@example.test", transport.submitted?.get("email"))
        assertEquals("password-123", transport.submitted?.get("password1"))
        assertEquals("password-123", transport.submitted?.get("password2"))
        assertEquals("1234", transport.submitted?.get("sec_code"))
        assertEquals("token-secret", transport.submitted?.get("registration_token"))
    }

    @Test
    fun `server rejection returns refreshed captcha page`() = runTest {
        val transport = FakeRegistrationTransport(
            initialHtml = formHtml(),
            submitHtml = formHtml("<div class='berrors'>Неверный код безопасности</div>"),
        )
        val api = KinogoRegistrationApi(transport)
        val page = (api.load(ORIGIN) as RegistrationLoadResult.Ready).page

        val result = api.submit(page, validInput())

        assertTrue(result is RegistrationSubmitResult.Rejected)
        result as RegistrationSubmitResult.Rejected
        assertTrue(result.message.contains("Неверный"))
        assertNotNull(result.refreshedPage?.captchaImage)
        assertEquals(2, transport.binaryLoads)
    }

    @Test
    fun `invalid password is rejected before network submission`() = runTest {
        val transport = FakeRegistrationTransport(formHtml())
        val api = KinogoRegistrationApi(transport)
        val page = (api.load(ORIGIN) as RegistrationLoadResult.Ready).page

        val result = api.submit(
            page,
            RegistrationInput("viewer", "viewer@example.test", "short", "different", "1234"),
        )

        assertTrue(result is RegistrationSubmitResult.Rejected)
        assertEquals(null, transport.submitted)
    }

    private fun validInput() = RegistrationInput(
        login = "viewer",
        email = "viewer@example.test",
        password = "password-123",
        passwordConfirmation = "password-123",
        captchaText = "1234",
    )

    private fun formHtml(prefix: String = ""): String = """
        <html><body>$prefix
          <form method='post' action='/index.php?do=register'>
            <input type='hidden' name='registration_token' value='token-secret'>
            <input name='name'><input name='email'>
            <input type='password' name='password1'><input type='password' name='password2'>
            <span id='dle-captcha'><img src='/engine/modules/antibot/antibot.php'></span>
            <input name='sec_code'>
            <button type='submit' name='submit_reg' value='submit_reg'>Создать</button>
          </form>
        </body></html>
    """.trimIndent()

    private fun rulesHtml(): String = """
        <html><body><div class='staticPage'>
          <p>Правила выбранного сайта.</p>
          <form id='registration' method='post' action='/index.php?do=register'>
            <input type='hidden' name='do' value='register'>
            <input type='hidden' name='dle_rules_accept' value='yes'>
            <input type='submit' value='Принимаю'>
          </form>
        </div></body></html>
    """.trimIndent()

    private class FakeRegistrationTransport(
        private val initialHtml: String,
        private val submitHtml: String = initialHtml,
    ) : RegistrationTransport {
        var binaryPath: String? = null
        var binaryLoads: Int = 0
        var submitted: Map<String, String>? = null

        override suspend fun get(origin: String, relativePath: String): SessionHttpResponse =
            text(initialHtml)

        override suspend fun post(
            origin: String,
            relativePath: String,
            form: Map<String, String>,
        ): SessionHttpResponse {
            submitted = form
            return text(submitHtml)
        }

        override suspend fun getBinary(
            origin: String,
            relativePath: String,
            maxBytes: Int,
        ): SessionBinaryResponse {
            binaryPath = relativePath
            binaryLoads++
            return SessionBinaryResponse(
                requestedOrigin = origin,
                resolvedOrigin = origin,
                relativePath = relativePath,
                statusCode = 200,
                contentType = "image/png",
                body = PNG_BYTES,
            )
        }

        private fun text(body: String) = SessionHttpResponse(
            requestedOrigin = ORIGIN,
            resolvedOrigin = ORIGIN,
            relativePath = "/index.php?do=register",
            statusCode = 200,
            body = body,
        )
    }

    private companion object {
        const val ORIGIN = "https://kinogo.parts"
        val PNG_BYTES = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0, 0, 0, 0, 0,
        )
    }
}
