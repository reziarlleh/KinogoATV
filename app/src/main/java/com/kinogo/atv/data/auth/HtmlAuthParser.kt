package com.kinogo.atv.data.auth

import org.jsoup.Jsoup

data class AuthPageSnapshot(
    val isAuthenticated: Boolean,
    val loginHash: String?,
    val displayName: String?,
    val errorMessage: String?,
)

/** Parses the small stable DLE authentication surface from server-rendered HTML. */
object HtmlAuthParser {
    private val groupPattern = Regex("(?:var\\s+)?dle_group\\s*=\\s*['\"]?(\\d+)")
    private val hashPattern = Regex("(?:var\\s+)?dle_login_hash\\s*=\\s*['\"]([^'\"]*)")

    fun parse(html: String): AuthPageSnapshot {
        val document = Jsoup.parse(html)
        val group = groupPattern.find(html)?.groupValues?.get(1)?.toIntOrNull()
        val authenticated = group != null && group != GUEST_GROUP
        val displayName = sequenceOf(
            ".loginUserName",
            ".login-user-name",
            "#loginpanel .name",
            "[data-username]",
        ).mapNotNull { selector ->
            document.selectFirst(selector)?.let { element ->
                element.attr("data-username").ifBlank { element.text().trim() }
            }
        }.firstOrNull(String::isNotBlank)
        val error = sequenceOf(
            ".berrors",
            ".dle-error",
            ".ui-state-error",
            ".error",
        ).mapNotNull { selector -> document.selectFirst(selector)?.text()?.trim() }
            .firstOrNull(String::isNotBlank)

        return AuthPageSnapshot(
            isAuthenticated = authenticated,
            loginHash = hashPattern.find(html)?.groupValues?.get(1)?.takeIf(String::isNotBlank),
            displayName = displayName,
            errorMessage = error,
        )
    }

    private const val GUEST_GROUP = 5
}
