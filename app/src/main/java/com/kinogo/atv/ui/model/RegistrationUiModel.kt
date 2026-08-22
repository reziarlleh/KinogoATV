package com.kinogo.atv.ui.model

enum class RegistrationUiPhase {
    LOADING,
    RULES,
    READY,
    SUBMITTING,
    COMPLETED,
    UNAVAILABLE,
    ERROR,
}

class RegistrationSubmissionUiInput(
    val login: String,
    val email: String,
    val password: String,
    val passwordConfirmation: String,
    val captchaText: String,
    val acceptedTerms: Boolean,
) {
    override fun toString(): String =
        "RegistrationSubmissionUiInput(login=<redacted>, email=<redacted>, " +
            "password=<redacted>, passwordConfirmation=<redacted>, " +
            "captchaText=<redacted>, acceptedTerms=$acceptedTerms)"
}

class RegistrationUiModel(
    val phase: RegistrationUiPhase,
    val message: String? = null,
    captchaBytes: ByteArray? = null,
    val captchaMimeType: String? = null,
    val requiresCaptcha: Boolean = false,
    val requiresConsent: Boolean = false,
    val consentLabel: String = "Я принимаю правила сайта",
    val rulesText: String? = null,
) {
    val captchaBytes: ByteArray? = captchaBytes?.copyOf()

    override fun toString(): String =
        "RegistrationUiModel(phase=$phase, message=${if (message == null) "<none>" else "<present>"}, " +
            "captchaBytes=${captchaBytes?.let { "<${it.size} bytes>" } ?: "<none>"}, " +
            "captchaMimeType=$captchaMimeType, requiresCaptcha=$requiresCaptcha, " +
            "requiresConsent=$requiresConsent, consentLabel=<redacted>, " +
            "rulesText=${if (rulesText == null) "<none>" else "<${rulesText.length} chars>"})"
}
