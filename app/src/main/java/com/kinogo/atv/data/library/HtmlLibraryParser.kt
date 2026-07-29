package com.kinogo.atv.data.library

import com.kinogo.atv.domain.WatchStatus
import org.jsoup.Jsoup

data class ParsedServerLibraryState(
    val watchStatus: WatchStatus?,
    val favorite: Boolean?,
)

data class MyListMutationResponse(
    val successful: Boolean,
    val authenticationRequired: Boolean,
    val errorMessage: String?,
)

object HtmlLibraryParser {
    private val jsonError = Regex("\"error\"\\s*:\\s*\"([^\"]*)\"")
    private val jsonSuccessFalse = Regex("\"success\"\\s*:\\s*false", RegexOption.IGNORE_CASE)

    fun parseDetails(html: String): ParsedServerLibraryState {
        val document = Jsoup.parse(html)
        val statusFolder = document
            .selectFirst(".js-mylist button.is-active[data-folder]")
            ?.attr("data-folder")
        val favoriteAction = document.selectFirst(
            "[id^=fav-id-] [onclick*=doFavorites], [onclick*=doFavorites]",
        )?.attr("onclick").orEmpty()
        val favorite = when {
            "'minus'" in favoriteAction || "\"minus\"" in favoriteAction -> true
            "'plus'" in favoriteAction || "\"plus\"" in favoriteAction -> false
            document.selectFirst(".addFav[title*=зарегистрироваться]") != null -> null
            else -> null
        }
        return ParsedServerLibraryState(
            watchStatus = WatchStatus.fromFolder(statusFolder),
            favorite = favorite,
        )
    }

    fun parseMyListMutation(body: String): MyListMutationResponse {
        val error = jsonError.find(body)?.groupValues?.get(1)?.unescapeJson()?.takeIf(String::isNotBlank)
        val requiresLogin = error?.contains("авториз", ignoreCase = true) == true
        val successful = !jsonSuccessFalse.containsMatchIn(body) && error == null
        return MyListMutationResponse(
            successful = successful,
            authenticationRequired = requiresLogin,
            errorMessage = error,
        )
    }

    fun favoriteMutationRequiresLogin(body: String): Boolean =
        body.trim().equals("error", ignoreCase = true) ||
            body.contains("авториз", ignoreCase = true)

    private fun String.unescapeJson(): String =
        replace("\\/", "/").replace("\\\"", "\"").replace("\\n", " ").replace("\\r", " ")
}
