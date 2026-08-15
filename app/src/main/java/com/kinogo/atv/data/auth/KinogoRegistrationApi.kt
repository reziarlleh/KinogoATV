package com.kinogo.atv.data.auth

import com.kinogo.atv.data.catalog.KinogoSessionHttpClient
import com.kinogo.atv.data.catalog.SessionBinaryResponse
import com.kinogo.atv.data.catalog.SessionHttpResponse
import kotlinx.coroutines.CancellationException

class RegistrationInput(
    val login: String,
    val email: String,
    val password: String,
    val passwordConfirmation: String,
    val captchaText: String = "",
    val acceptedTerms: Boolean = false,
) {
    override fun toString(): String =
        "RegistrationInput(login=<redacted>, email=<redacted>, password=<redacted>, " +
            "passwordConfirmation=<redacted>, captchaText=<redacted>, acceptedTerms=$acceptedTerms)"
}

class RegistrationCaptchaImage(
    bytes: ByteArray,
    val mimeType: String,
) {
    val bytes: ByteArray = bytes.copyOf()

    override fun toString(): String =
        "RegistrationCaptchaImage(mimeType=$mimeType, bytes=<${bytes.size} bytes>)"
}

class RegistrationPage(
    val form: RegistrationForm,
    val captchaImage: RegistrationCaptchaImage?,
    val message: String? = null,
) {
    override fun toString(): String =
        "RegistrationPage(form=$form, captchaImage=$captchaImage, " +
            "message=${if (message == null) "<none>" else "<present>"})"
}

sealed interface RegistrationLoadResult {
    data class Ready(val page: RegistrationPage) : RegistrationLoadResult
    data class ConsentRequired(val page: RegistrationRulesPage) : RegistrationLoadResult
    data class Unavailable(val message: String) : RegistrationLoadResult
    data class Failed(val message: String) : RegistrationLoadResult
}

sealed interface RegistrationSubmitResult {
    data class Completed(val message: String) : RegistrationSubmitResult
    data class Rejected(
        val message: String,
        val refreshedPage: RegistrationPage?,
    ) : RegistrationSubmitResult
    data class Failed(val message: String) : RegistrationSubmitResult
}

internal interface RegistrationTransport {
    suspend fun get(origin: String, relativePath: String): SessionHttpResponse
    suspend fun post(
        origin: String,
        relativePath: String,
        form: Map<String, String>,
    ): SessionHttpResponse
    suspend fun getBinary(
        origin: String,
        relativePath: String,
        maxBytes: Int,
    ): SessionBinaryResponse
}

private class SessionRegistrationTransport(
    private val client: KinogoSessionHttpClient,
) : RegistrationTransport {
    override suspend fun get(origin: String, relativePath: String): SessionHttpResponse =
        client.getRaw(origin, relativePath)

    override suspend fun post(
        origin: String,
        relativePath: String,
        form: Map<String, String>,
    ): SessionHttpResponse = client.postForm(origin, relativePath, form)

    override suspend fun getBinary(
        origin: String,
        relativePath: String,
        maxBytes: Int,
    ): SessionBinaryResponse = client.getBinaryRaw(origin, relativePath, maxBytes)
}

/**
 * Native, same-origin DLE registration client. CAPTCHA is displayed for the user to solve; it is
 * never bypassed or sent to an external recognition service.
 */
class KinogoRegistrationApi internal constructor(
    private val transport: RegistrationTransport,
) {
    constructor(client: KinogoSessionHttpClient) : this(SessionRegistrationTransport(client))

    suspend fun load(origin: String): RegistrationLoadResult = safely {
        val response = transport.get(origin, REGISTRATION_PATH)
        if (response.statusCode !in 200..299) {
            return@safely RegistrationLoadResult.Failed(httpError(response.statusCode))
        }
        when (val parsed = RegistrationHtmlParser.parse(
            origin = response.resolvedOrigin,
            responseRelativePath = response.relativePath,
            html = response.body,
        )) {
            is RegistrationDocument.Form -> loadPage(parsed)
            is RegistrationDocument.Rules -> RegistrationLoadResult.ConsentRequired(parsed.page)
            is RegistrationDocument.Completed -> RegistrationLoadResult.Unavailable(parsed.message)
            is RegistrationDocument.Unavailable -> RegistrationLoadResult.Unavailable(parsed.message)
        }
    }

    /** Refreshes both form state and CAPTCHA; reloading only the image can desynchronise DLE. */
    suspend fun refresh(origin: String): RegistrationLoadResult = load(origin)

    suspend fun acceptRules(page: RegistrationRulesPage): RegistrationLoadResult = safely {
        val response = transport.post(
            origin = page.origin,
            relativePath = page.actionRelativePath,
            form = page.buildSubmission(),
        )
        if (response.statusCode !in 200..299) {
            return@safely RegistrationLoadResult.Failed(httpError(response.statusCode))
        }
        when (val parsed = RegistrationHtmlParser.parse(
            origin = response.resolvedOrigin,
            responseRelativePath = response.relativePath,
            html = response.body,
        )) {
            is RegistrationDocument.Form -> loadPage(parsed)
            is RegistrationDocument.Rules -> RegistrationLoadResult.Unavailable(
                "Сервер снова запросил принятие правил",
            )
            is RegistrationDocument.Completed -> RegistrationLoadResult.Unavailable(parsed.message)
            is RegistrationDocument.Unavailable -> RegistrationLoadResult.Unavailable(parsed.message)
        }
    }

    suspend fun submit(
        page: RegistrationPage,
        input: RegistrationInput,
    ): RegistrationSubmitResult {
        validate(page.form, input)?.let { error ->
            return RegistrationSubmitResult.Rejected(error, page)
        }
        if (page.form.captcha?.kind == RegistrationCaptchaKind.INTERACTIVE_UNSUPPORTED) {
            return RegistrationSubmitResult.Failed(
                "Сайт использует интерактивную CAPTCHA, которую нельзя безопасно показать в нативной форме",
            )
        }

        return safelySubmit {
            val response = transport.post(
                origin = page.form.origin,
                relativePath = page.form.actionRelativePath,
                form = page.form.buildSubmission(input),
            )
            if (response.statusCode !in 200..299) {
                return@safelySubmit RegistrationSubmitResult.Failed(httpError(response.statusCode))
            }
            when (val parsed = RegistrationHtmlParser.parse(
                origin = response.resolvedOrigin,
                responseRelativePath = response.relativePath,
                html = response.body,
            )) {
                is RegistrationDocument.Completed -> RegistrationSubmitResult.Completed(parsed.message)
                is RegistrationDocument.Rules -> RegistrationSubmitResult.Failed(
                    "Сайт снова запросил принятие правил. Откройте регистрацию заново.",
                )
                is RegistrationDocument.Unavailable -> RegistrationSubmitResult.Failed(parsed.message)
                is RegistrationDocument.Form -> {
                    val refreshed = when (val loaded = loadPage(parsed)) {
                        is RegistrationLoadResult.Ready -> loaded.page
                        else -> null
                    }
                    RegistrationSubmitResult.Rejected(
                        message = parsed.message ?: "Сайт не принял регистрационные данные",
                        refreshedPage = refreshed,
                    )
                }
            }
        }
    }

    private suspend fun loadPage(parsed: RegistrationDocument.Form): RegistrationLoadResult {
        val descriptor = parsed.form.captcha
        if (descriptor?.kind == RegistrationCaptchaKind.INTERACTIVE_UNSUPPORTED) {
            return RegistrationLoadResult.Unavailable(
                "Сайт использует интерактивную CAPTCHA. Встроенная регистрация поддерживает только изображение DLE.",
            )
        }
        val captchaImage = descriptor?.relativeImagePath?.let { path ->
            val response = transport.getBinary(parsed.form.origin, path, MAX_CAPTCHA_BYTES)
            if (response.statusCode !in 200..299) {
                return RegistrationLoadResult.Failed("Не удалось загрузить CAPTCHA: HTTP ${response.statusCode}")
            }
            CaptchaImagePolicy.decode(response)
                ?: return RegistrationLoadResult.Failed("Сайт вернул неподдерживаемое изображение CAPTCHA")
        }
        return RegistrationLoadResult.Ready(
            RegistrationPage(parsed.form, captchaImage, parsed.message),
        )
    }

    private fun validate(form: RegistrationForm, input: RegistrationInput): String? = when {
        input.login.trim().length !in 3..40 -> "Логин должен содержать от 3 до 40 символов"
        input.email.trim().length !in 3..254 || '@' !in input.email -> "Введите корректный e-mail"
        input.password.length !in 6..128 -> "Пароль должен содержать от 6 до 128 символов"
        input.password != input.passwordConfirmation -> "Пароли не совпадают"
        form.captcha?.kind == RegistrationCaptchaKind.IMAGE && input.captchaText.isBlank() ->
            "Введите код с изображения"
        form.consent != null && !input.acceptedTerms -> "Подтвердите согласие с правилами сайта"
        else -> null
    }

    private suspend fun safely(block: suspend () -> RegistrationLoadResult): RegistrationLoadResult =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RegistrationLoadResult.Failed("Не удалось загрузить форму регистрации")
        }

    private suspend fun safelySubmit(
        block: suspend () -> RegistrationSubmitResult,
    ): RegistrationSubmitResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        RegistrationSubmitResult.Failed("Не удалось отправить форму регистрации")
    }

    private fun httpError(statusCode: Int): String = "Сервер вернул HTTP $statusCode"

    private companion object {
        const val REGISTRATION_PATH = "/index.php?do=register"
        const val MAX_CAPTCHA_BYTES = 512 * 1_024
    }
}

internal object CaptchaImagePolicy {
    fun decode(response: SessionBinaryResponse): RegistrationCaptchaImage? {
        if (response.body.size !in 16..MAX_BYTES) return null
        val detected = detectMimeType(response.body) ?: return null
        val declared = response.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (declared != null && declared !in ALLOWED_DECLARED_TYPES) return null
        return RegistrationCaptchaImage(response.body, detected)
    }

    private fun detectMimeType(bytes: ByteArray): String? = when {
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a") -> "image/gif"
        bytes.size >= 12 && bytes.startsWithAscii("RIFF") &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { index ->
            (this[index].toInt() and 0xFF) == expected[index]
        }

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && value.indices.all { index -> this[index] == value[index].code.toByte() }

    private val ALLOWED_DECLARED_TYPES = setOf(
        "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "application/octet-stream",
    )
    private const val MAX_BYTES = 512 * 1_024
}
