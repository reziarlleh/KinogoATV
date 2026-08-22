package com.kinogo.atv.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationHtmlParserTest {
    @Test
    fun `parses DLE rules gate without accepting it`() {
        val parsed = RegistrationHtmlParser.parse(
            ORIGIN,
            "/index.php?do=register",
            rulesHtml(),
        )

        assertTrue(parsed is RegistrationDocument.Rules)
        val page = (parsed as RegistrationDocument.Rules).page
        assertEquals("/index.php?do=register", page.actionRelativePath)
        assertTrue(page.rulesText.contains("правила сообщества"))
        assertEquals(
            mapOf("do" to "register", "dle_rules_accept" to "yes"),
            page.buildSubmission(),
        )
    }

    @Test
    fun `parses same-origin DLE form and image captcha`() {
        val parsed = RegistrationHtmlParser.parse(ORIGIN, "/index.php?do=register", formHtml())

        assertTrue(parsed is RegistrationDocument.Form)
        val form = (parsed as RegistrationDocument.Form).form
        assertEquals("/index.php?do=register", form.actionRelativePath)
        assertEquals("name", form.loginFieldName)
        assertEquals("email", form.emailFieldName)
        assertEquals("password1", form.passwordFieldName)
        assertEquals("password2", form.passwordConfirmationFieldName)
        assertEquals(RegistrationCaptchaKind.IMAGE, form.captcha?.kind)
        assertEquals("sec_code", form.captcha?.fieldName)
        assertEquals("/engine/modules/antibot/antibot.php?fresh=1", form.captcha?.relativeImagePath)
        assertEquals(setOf("do", "submit_reg"), form.hiddenFieldNames)
        assertEquals("rules", form.consent?.fieldName)
    }

    @Test
    fun `rejects captcha image on another origin`() {
        val html = formHtml().replace(
            "/engine/modules/antibot/antibot.php?fresh=1",
            "https://evil.example/captcha.png",
        )

        val parsed = RegistrationHtmlParser.parse(ORIGIN, "/index.php?do=register", html)

        assertTrue(parsed is RegistrationDocument.Unavailable)
        assertTrue((parsed as RegistrationDocument.Unavailable).message.contains("пределами"))
    }

    @Test
    fun `marks interactive captcha unsupported instead of bypassing it`() {
        val html = formHtml()
            .replace("<span id='dle-captcha'><img src='/engine/modules/antibot/antibot.php?fresh=1'></span>", "<div class='g-recaptcha' data-sitekey='public'></div>")
            .replace("<input name='sec_code'>", "<input name='g-recaptcha-response'>")

        val parsed = RegistrationHtmlParser.parse(ORIGIN, "/index.php?do=register", html)

        assertTrue(parsed is RegistrationDocument.Form)
        assertEquals(
            RegistrationCaptchaKind.INTERACTIVE_UNSUPPORTED,
            (parsed as RegistrationDocument.Form).form.captcha?.kind,
        )
    }

    @Test
    fun `parses successful completion without retaining form`() {
        val parsed = RegistrationHtmlParser.parse(
            ORIGIN,
            "/index.php?do=register",
            "<html><body><div class='information'>Регистрация прошла успешно. Проверьте почту.</div></body></html>",
        )

        assertTrue(parsed is RegistrationDocument.Completed)
        assertTrue((parsed as RegistrationDocument.Completed).message.contains("успешно"))
    }

    @Test
    fun `disabled registration is not mistaken for successful completion`() {
        val parsed = RegistrationHtmlParser.parse(
            ORIGIN,
            "/index.php?do=register",
            "<html><body><p>Регистрация пользователей временно отключена.</p></body></html>",
        )

        assertTrue(parsed is RegistrationDocument.Unavailable)
    }

    @Test
    fun `registration form and input redact ephemeral values`() {
        val form = (RegistrationHtmlParser.parse(
            ORIGIN,
            "/index.php?do=register",
            formHtml(hiddenToken = "secret-form-token"),
        ) as RegistrationDocument.Form).form
        val input = RegistrationInput(
            login = "private-login",
            email = "private@example.test",
            password = "private-password",
            passwordConfirmation = "private-password",
            captchaText = "1234",
        )

        assertFalse(form.toString().contains("secret-form-token"))
        assertFalse(input.toString().contains("private-login"))
        assertFalse(input.toString().contains("private-password"))
        assertNull(form.captcha?.relativeImagePath?.takeIf { "secret" in it })
    }

    private fun formHtml(hiddenToken: String = "token-1"): String = """
        <html><body>
          <form method='post' action='/index.php?do=register'>
            <input type='hidden' name='do' value='register'>
            <input type='hidden' name='submit_reg' value='$hiddenToken'>
            <input name='name'>
            <input name='email'>
            <input type='password' name='password1'>
            <input type='password' name='password2'>
            <span id='dle-captcha'><img src='/engine/modules/antibot/antibot.php?fresh=1'></span>
            <input name='sec_code'>
            <label for='rules'>Принимаю правила</label>
            <input id='rules' type='checkbox' name='rules' value='yes' required>
            <button type='submit'>Зарегистрироваться</button>
          </form>
        </body></html>
    """.trimIndent()

    private fun rulesHtml(): String = """
        <html><body><div id='dle-content'><div class='staticPage'>
          <p>Пожалуйста, прочитайте правила сообщества перед регистрацией.</p>
          <form id='registration' method='post' action=''>
            <input type='submit' value='Принимаю'>
            <input type='button' value='Не принимаю'>
            <input type='hidden' name='do' value='register'>
            <input type='hidden' name='dle_rules_accept' value='yes'>
          </form>
        </div></div></body></html>
    """.trimIndent()

    private companion object {
        const val ORIGIN = "https://kinogo.parts"
    }
}
