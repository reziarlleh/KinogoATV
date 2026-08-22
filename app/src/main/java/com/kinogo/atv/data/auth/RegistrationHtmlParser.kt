package com.kinogo.atv.data.auth

import com.kinogo.atv.data.catalog.SessionRouteNormalizer
import com.kinogo.atv.data.mirror.MirrorUrlNormalizer
import java.net.URI
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

enum class RegistrationCaptchaKind {
    IMAGE,
    INTERACTIVE_UNSUPPORTED,
}

class RegistrationCaptchaDescriptor(
    val kind: RegistrationCaptchaKind,
    val fieldName: String?,
    internal val relativeImagePath: String?,
) {
    override fun toString(): String =
        "RegistrationCaptchaDescriptor(kind=$kind, fieldName=$fieldName, " +
            "relativeImagePath=${if (relativeImagePath == null) "<none>" else "<same-origin>"})"
}

data class RegistrationConsent(
    val fieldName: String,
    val value: String,
    val label: String,
)

/**
 * Parsed, in-memory registration form. Hidden field values may contain one-time server state and
 * are therefore deliberately omitted from [toString].
 */
class RegistrationForm internal constructor(
    val origin: String,
    val actionRelativePath: String,
    val loginFieldName: String,
    val emailFieldName: String,
    val passwordFieldName: String,
    val passwordConfirmationFieldName: String,
    val captcha: RegistrationCaptchaDescriptor?,
    val consent: RegistrationConsent?,
    private val baseFields: Map<String, String>,
) {
    val hiddenFieldNames: Set<String> get() = baseFields.keys

    internal fun buildSubmission(input: RegistrationInput): Map<String, String> =
        linkedMapOf<String, String>().apply {
            putAll(baseFields)
            put(loginFieldName, input.login.trim())
            put(emailFieldName, input.email.trim())
            put(passwordFieldName, input.password)
            put(passwordConfirmationFieldName, input.passwordConfirmation)
            captcha?.fieldName?.let { put(it, input.captchaText.trim()) }
            consent?.takeIf { input.acceptedTerms }?.let { put(it.fieldName, it.value) }
        }

    override fun toString(): String =
        "RegistrationForm(origin=$origin, actionRelativePath=${actionRelativePath.redactQuery()}, " +
            "loginFieldName=$loginFieldName, emailFieldName=$emailFieldName, " +
            "passwordFieldName=$passwordFieldName, " +
            "passwordConfirmationFieldName=$passwordConfirmationFieldName, " +
            "captcha=$captcha, consent=$consent, hiddenFields=<${baseFields.size} names>)"
}

/** First DLE registration step. The server reveals account fields only after explicit consent. */
class RegistrationRulesPage internal constructor(
    val origin: String,
    val actionRelativePath: String,
    val rulesText: String,
    private val submissionFields: Map<String, String>,
) {
    internal fun buildSubmission(): Map<String, String> = LinkedHashMap(submissionFields)

    override fun toString(): String =
        "RegistrationRulesPage(origin=$origin, actionRelativePath=${actionRelativePath.redactQuery()}, " +
            "rulesText=<${rulesText.length} chars>, submissionFields=<${submissionFields.size} names>)"
}

sealed interface RegistrationDocument {
    data class Form(
        val form: RegistrationForm,
        val message: String? = null,
    ) : RegistrationDocument

    data class Completed(val message: String) : RegistrationDocument
    data class Rules(val page: RegistrationRulesPage) : RegistrationDocument
    data class Unavailable(val message: String) : RegistrationDocument
}

/** Pure parser for the browser-visible DLE registration surface. */
object RegistrationHtmlParser {
    fun parse(
        origin: String,
        responseRelativePath: String,
        html: String,
    ): RegistrationDocument {
        val normalizedOrigin = MirrorUrlNormalizer.normalize(origin)
        val normalizedResponsePath = SessionRouteNormalizer.normalize(responseRelativePath)
        val document = Jsoup.parse(html, "$normalizedOrigin$normalizedResponsePath")
        val message = extractMessage(document.body())
        val formElement = document.select("form").firstOrNull(::looksLikeRegistrationForm)

        if (formElement == null) {
            document.select("form").firstOrNull(::looksLikeRulesForm)?.let { rulesForm ->
                return parseRulesPage(
                    origin = normalizedOrigin,
                    responsePath = normalizedResponsePath,
                    form = rulesForm,
                )
            }
            val pageText = document.body().text().trim()
            val successText = sequenceOf(message, pageText)
                .filterNotNull()
                .firstOrNull(::looksLikeSuccess)
            return if (successText != null) {
                RegistrationDocument.Completed(successText.take(MAX_MESSAGE_LENGTH))
            } else {
                RegistrationDocument.Unavailable(
                    message?.take(MAX_MESSAGE_LENGTH)
                        ?: "Сайт не вернул форму регистрации. Возможно, регистрация временно отключена.",
                )
            }
        }

        val action = normalizeSameOriginAction(
            origin = normalizedOrigin,
            responsePath = normalizedResponsePath,
            rawAction = formElement.attr("action"),
        ) ?: return RegistrationDocument.Unavailable(
            "Форма регистрации ведёт за пределы выбранного зеркала",
        )
        if (!formElement.attr("method").ifBlank { "get" }.equals("post", ignoreCase = true)) {
            return RegistrationDocument.Unavailable("Сайт вернул неподдерживаемый способ отправки формы")
        }

        val loginField = formElement.findNamedInput(LOGIN_FIELD_NAMES)
        val emailField = formElement.findNamedInput(EMAIL_FIELD_NAMES)
        val passwordField = formElement.findNamedInput(PASSWORD_FIELD_NAMES)
        val confirmationField = formElement.findNamedInput(PASSWORD_CONFIRMATION_FIELD_NAMES)
        if (loginField == null || emailField == null || passwordField == null || confirmationField == null) {
            return RegistrationDocument.Unavailable("Структура формы регистрации изменилась")
        }

        val captchaInput = formElement.findNamedInput(CAPTCHA_FIELD_NAMES)
        val interactiveCaptcha = formElement.selectFirst(
            ".g-recaptcha, [data-sitekey], iframe[src*=recaptcha], iframe[src*=hcaptcha], iframe[src*=turnstile]",
        )
        val captchaImage = captchaInput?.let { findCaptchaImage(formElement, it) }
        val captcha = when {
            interactiveCaptcha != null -> RegistrationCaptchaDescriptor(
                kind = RegistrationCaptchaKind.INTERACTIVE_UNSUPPORTED,
                fieldName = captchaInput?.attr("name")?.trim()?.takeIf(String::isNotBlank),
                relativeImagePath = null,
            )
            captchaInput != null && captchaImage != null -> {
                val relativePath = normalizeSameOriginAction(
                    origin = normalizedOrigin,
                    responsePath = normalizedResponsePath,
                    rawAction = captchaImage.attr("src"),
                ) ?: return RegistrationDocument.Unavailable(
                    "Изображение CAPTCHA находится за пределами выбранного зеркала",
                )
                RegistrationCaptchaDescriptor(
                    kind = RegistrationCaptchaKind.IMAGE,
                    fieldName = captchaInput.attr("name").trim(),
                    relativeImagePath = relativePath,
                )
            }
            captchaInput != null -> return RegistrationDocument.Unavailable(
                "Сайт запросил CAPTCHA, но не предоставил безопасное изображение",
            )
            else -> null
        }

        val userFieldNames = setOf(
            loginField.attr("name"),
            emailField.attr("name"),
            passwordField.attr("name"),
            confirmationField.attr("name"),
            captchaInput?.attr("name"),
        ).filterNotNull().toSet()
        val baseFields = linkedMapOf<String, String>()
        formElement.select("input[type=hidden][name]")
            .take(MAX_BASE_FIELDS)
            .forEach { input ->
                val name = input.attr("name").trim()
                val value = input.attr("value")
                if (validFieldName(name) && name !in userFieldNames && value.length <= MAX_FIELD_VALUE_LENGTH) {
                    baseFields.putIfAbsent(name, value)
                }
            }
        formElement.select("input[type=submit][name], button[type=submit][name]")
            .firstOrNull()
            ?.let { submit ->
                val name = submit.attr("name").trim()
                val value = submit.attr("value").ifBlank { submit.text().trim() }
                if (validFieldName(name) && name !in userFieldNames && value.length <= MAX_FIELD_VALUE_LENGTH) {
                    baseFields.putIfAbsent(name, value)
                }
            }
        if ("submit_reg" !in baseFields && formElement.selectFirst("[name=submit_reg]") == null) {
            // This is the stable DLE server switch. Adding it only when the form did not provide
            // another value keeps old and new templates compatible.
            baseFields["submit_reg"] = "submit_reg"
        }

        val consent = formElement.select("input[type=checkbox][name]")
            .firstOrNull { checkbox ->
                checkbox.hasAttr("required") ||
                    checkbox.attr("name").lowercase(Locale.ROOT).let { name ->
                        "rule" in name || "agree" in name || "accept" in name
                    }
            }
            ?.let { checkbox ->
                RegistrationConsent(
                    fieldName = checkbox.attr("name").trim(),
                    value = checkbox.attr("value").ifBlank { "yes" },
                    label = consentLabel(formElement, checkbox),
                )
            }

        return RegistrationDocument.Form(
            form = RegistrationForm(
                origin = normalizedOrigin,
                actionRelativePath = action,
                loginFieldName = loginField.attr("name").trim(),
                emailFieldName = emailField.attr("name").trim(),
                passwordFieldName = passwordField.attr("name").trim(),
                passwordConfirmationFieldName = confirmationField.attr("name").trim(),
                captcha = captcha,
                consent = consent,
                baseFields = baseFields,
            ),
            message = message?.take(MAX_MESSAGE_LENGTH),
        )
    }

    private fun looksLikeRegistrationForm(form: Element): Boolean =
        form.findNamedInput(LOGIN_FIELD_NAMES) != null &&
            form.findNamedInput(EMAIL_FIELD_NAMES) != null &&
            form.findNamedInput(PASSWORD_FIELD_NAMES) != null &&
            form.findNamedInput(PASSWORD_CONFIRMATION_FIELD_NAMES) != null

    private fun looksLikeRulesForm(form: Element): Boolean {
        val doField = form.selectFirst("input[name=do]")
        val acceptField = form.selectFirst("input[name=dle_rules_accept]")
        return doField?.attr("value")?.equals("register", ignoreCase = true) == true &&
            acceptField != null
    }

    private fun parseRulesPage(
        origin: String,
        responsePath: String,
        form: Element,
    ): RegistrationDocument {
        if (!form.attr("method").ifBlank { "get" }.equals("post", ignoreCase = true)) {
            return RegistrationDocument.Unavailable("Сайт вернул неподдерживаемый способ принятия правил")
        }
        val action = normalizeSameOriginAction(origin, responsePath, form.attr("action"))
            ?: return RegistrationDocument.Unavailable(
                "Форма принятия правил ведёт за пределы выбранного зеркала",
            )
        val fields = linkedMapOf<String, String>()
        form.select("input[type=hidden][name]")
            .take(MAX_BASE_FIELDS)
            .forEach { input ->
                val name = input.attr("name").trim()
                val value = input.attr("value")
                if (validFieldName(name) && value.length <= MAX_FIELD_VALUE_LENGTH) {
                    fields.putIfAbsent(name, value)
                }
            }
        if (fields["do"]?.equals("register", ignoreCase = true) != true ||
            fields["dle_rules_accept"].isNullOrBlank()
        ) {
            return RegistrationDocument.Unavailable("Структура формы принятия правил изменилась")
        }
        return RegistrationDocument.Rules(
            RegistrationRulesPage(
                origin = origin,
                actionRelativePath = action,
                rulesText = extractRulesText(form),
                submissionFields = fields,
            ),
        )
    }

    private fun extractRulesText(form: Element): String {
        val container = form.parents().firstOrNull { parent ->
            parent.hasClass("staticPage") || parent.id() == "dle-content" || parent.tagName() == "article"
        } ?: form.parent()
        val copy = container?.clone()
        copy?.select("form, script, style, nav")?.remove()
        return copy?.text()?.trim()?.takeIf(String::isNotBlank)?.take(MAX_RULES_LENGTH)
            ?: "Для регистрации необходимо принять правила выбранного сайта."
    }

    private fun Element.findNamedInput(names: Set<String>): Element? =
        select("input[name]").firstOrNull { input ->
            input.attr("name").trim().lowercase(Locale.ROOT) in names
        }

    private fun findCaptchaImage(form: Element, captchaInput: Element): Element? =
        sequenceOf(
            form.selectFirst("#dle-captcha img[src]"),
            form.selectFirst("img[src*=antibot]"),
            form.selectFirst("img[src*=captcha]"),
            captchaInput.parent()?.selectFirst("img[src]"),
            captchaInput.parent()?.parent()?.selectFirst("img[src]"),
        ).filterNotNull().firstOrNull()

    private fun normalizeSameOriginAction(
        origin: String,
        responsePath: String,
        rawAction: String,
    ): String? = runCatching {
        val base = URI.create(origin).resolve(responsePath)
        val resolved = if (rawAction.isBlank()) base else base.resolve(rawAction.trim())
        val authority = resolved.rawAuthority ?: return@runCatching null
        if (MirrorUrlNormalizer.normalize("https://$authority") != origin) return@runCatching null
        val relative = buildString {
            append(resolved.rawPath?.takeIf(String::isNotEmpty) ?: "/")
            resolved.rawQuery?.let { append('?').append(it) }
        }
        SessionRouteNormalizer.normalize(relative)
    }.getOrNull()

    private fun extractMessage(root: Element?): String? = root?.let { body ->
        MESSAGE_SELECTORS.asSequence()
            .mapNotNull { selector -> body.selectFirst(selector)?.text()?.trim() }
            .firstOrNull(String::isNotBlank)
    }

    private fun looksLikeSuccess(value: String): Boolean {
        val normalized = value.lowercase(Locale.ROOT)
        return SUCCESS_MARKERS.any(normalized::contains)
    }

    private fun consentLabel(form: Element, checkbox: Element): String {
        val id = checkbox.id()
        val explicit = id.takeIf(String::isNotBlank)?.let { fieldId ->
            form.getElementsByAttributeValue("for", fieldId).firstOrNull()?.text()
        }
        return explicit?.trim()?.takeIf(String::isNotBlank)?.take(MAX_CONSENT_LABEL_LENGTH)
            ?: checkbox.parent()?.text()?.trim()?.takeIf(String::isNotBlank)?.take(MAX_CONSENT_LABEL_LENGTH)
            ?: "Я принимаю правила сайта"
    }

    private fun validFieldName(value: String): Boolean =
        value.length in 1..64 && FIELD_NAME_PATTERN.matches(value)

    private val LOGIN_FIELD_NAMES = setOf("name", "login", "login_name", "username")
    private val EMAIL_FIELD_NAMES = setOf("email", "e-mail")
    private val PASSWORD_FIELD_NAMES = setOf("password1", "password", "password_1")
    private val PASSWORD_CONFIRMATION_FIELD_NAMES =
        setOf("password2", "password_repeat", "password_confirmation", "password_2")
    private val CAPTCHA_FIELD_NAMES = setOf("sec_code", "captcha", "captcha_code")
    private val FIELD_NAME_PATTERN = Regex("[A-Za-z0-9_.-]+")
    private val MESSAGE_SELECTORS = listOf(
        ".berrors",
        ".dle-error",
        ".ui-state-error",
        ".errorbox",
        ".error",
        ".information",
        ".success",
    )
    private val SUCCESS_MARKERS = listOf(
        "регистрация завершена", "регистрация прошла успешно", "успешно зарегистрирован",
        "аккаунт создан", "пользователь зарегистрирован",
        "registration complete", "successfully registered",
    )
    private const val MAX_BASE_FIELDS = 64
    private const val MAX_FIELD_VALUE_LENGTH = 4_096
    private const val MAX_MESSAGE_LENGTH = 500
    private const val MAX_CONSENT_LABEL_LENGTH = 240
    private const val MAX_RULES_LENGTH = 4_000
}

private fun String.redactQuery(): String =
    substringBefore('?') + if ('?' in this) "?<redacted>" else ""
