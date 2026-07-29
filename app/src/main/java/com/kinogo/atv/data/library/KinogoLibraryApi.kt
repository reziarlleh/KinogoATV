package com.kinogo.atv.data.library

import com.kinogo.atv.data.catalog.KinogoHtmlParser
import com.kinogo.atv.data.catalog.KinogoSessionHttpClient
import com.kinogo.atv.data.auth.HtmlAuthParser
import com.kinogo.atv.domain.AccountSession
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.LibraryRecord
import com.kinogo.atv.domain.WatchStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class RemoteLibrarySnapshot(
    val records: List<LibraryRecord>,
)

data class FavoriteMutationResponse(
    val successful: Boolean,
    val authenticationRequired: Boolean,
)

class KinogoLibraryApi(
    private val client: KinogoSessionHttpClient,
    private val parser: KinogoHtmlParser,
) {
    suspend fun setStatus(
        session: AccountSession,
        postId: Long,
        status: WatchStatus?,
    ): MyListMutationResponse {
        val response = client.postForm(
            rawOrigin = session.origin,
            rawRelativePath = "/engine/ajax/controller.php?mod=mylist",
            form = mapOf(
                "post_id" to postId.toString(),
                "folder" to (status?.folder ?: "0"),
            ),
        )
        return HtmlLibraryParser.parseMyListMutation(response.body)
    }

    suspend fun setFavorite(
        session: AccountSession,
        postId: Long,
        enabled: Boolean,
    ): FavoriteMutationResponse {
        val query = linkedMapOf(
            "mod" to "favorites",
            "fav_id" to postId.toString(),
            "action" to if (enabled) "plus" else "minus",
            "skin" to "kinogoB",
            "alert" to "0",
            "user_hash" to session.loginHash,
        ).entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val response = client.getRaw(
            session.origin,
            "/engine/ajax/controller.php?$query",
        )
        val authenticationRequired = HtmlLibraryParser.favoriteMutationRequiresLogin(response.body)
        return FavoriteMutationResponse(
            successful = response.statusCode in 200..299 && !authenticationRequired,
            authenticationRequired = authenticationRequired,
        )
    }

    /** Pulls all four status lists and the independent favorites list from rendered pages. */
    suspend fun pullSnapshot(session: AccountSession, maxPagesPerList: Int = 50): RemoteLibrarySnapshot {
        require(maxPagesPerList > 0)
        val records = linkedMapOf<String, LibraryRecord>()
        WatchStatus.entries.forEach { status ->
            loadList(session, "/favorites/${status.folder}/", maxPagesPerList).forEach { item ->
                val previous = records[item.id]
                records[item.id] = LibraryRecord(
                    item = item,
                    status = status,
                    favorite = previous?.favorite == true,
                )
            }
        }
        loadList(session, "/favorites/", maxPagesPerList).forEach { item ->
            val previous = records[item.id]
            records[item.id] = LibraryRecord(
                item = previous?.item ?: item,
                status = previous?.status,
                favorite = true,
            )
        }
        return RemoteLibrarySnapshot(records.values.toList())
    }

    private suspend fun loadList(
        session: AccountSession,
        basePath: String,
        maxPages: Int,
    ): List<CatalogItem> {
        val result = mutableListOf<CatalogItem>()
        var page = 1
        while (page <= maxPages) {
            val path = if (page == 1) basePath else "${basePath}page/$page/"
            val response = client.getRaw(session.origin, path)
            if (response.statusCode == 401 || response.statusCode == 403) {
                throw LibraryAuthenticationException()
            }
            if (response.statusCode !in 200..299) throw LibraryHttpException(response.statusCode)
            if (!HtmlAuthParser.parse(response.body).isAuthenticated) {
                throw LibraryAuthenticationException()
            }
            val parsed = parser.parseCatalog(response.body, session.origin, page)
            result += parsed.items
            val next = parsed.nextPage ?: break
            if (next <= page) break
            page = next
        }
        return result.distinctBy(CatalogItem::id)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

class LibraryAuthenticationException : Exception("Kinogo account session is not authenticated")
class LibraryHttpException(val statusCode: Int) : Exception("Kinogo library returned HTTP $statusCode")
