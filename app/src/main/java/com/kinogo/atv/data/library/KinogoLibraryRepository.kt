package com.kinogo.atv.data.library

import com.kinogo.atv.data.auth.KinogoSessionManager
import com.kinogo.atv.domain.AccountSession
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.LibraryRecord
import com.kinogo.atv.domain.WatchStatus
import kotlinx.coroutines.CancellationException

data class LibrarySyncResult(
    val records: List<LibraryRecord>,
    val authenticated: Boolean,
    val pendingCount: Int,
    val message: String,
)

class KinogoLibraryRepository(
    private val store: LibraryStateStore,
    private val sessionManager: KinogoSessionManager,
    private val remote: KinogoLibraryApi,
) {
    suspend fun list(): List<LibraryRecord> = store.list()

    suspend fun setFavorite(item: CatalogItem, enabled: Boolean): List<LibraryRecord> =
        store.setFavorite(item, enabled)

    suspend fun setStatus(item: CatalogItem, status: WatchStatus?): List<LibraryRecord> =
        store.setStatus(item, status)

    suspend fun sync(origin: String, login: String): LibrarySyncResult {
        var session = sessionManager.ensureAuthenticated(origin)
            ?: return localOnly("Аккаунт не подключён")
        return try {
            var pull = pullWithOneRelogin(origin, session)
                ?: return localOnly("Сессия сайта не восстановлена")
            session = pull.session
            var records = store.mergeRemote(login, pull.snapshot)
            val pending = store.pending()
            var pushedAny = false
            pending.forEach { mutation ->
                val postId = mutation.contentId.toLongOrNull()?.takeIf { it > 0L }
                    ?: return@forEach
                var result = applyMutation(session, postId, mutation)
                if (result.authenticationRequired) {
                    session = sessionManager.reauthenticate(origin)
                        ?: return@forEach
                    result = applyMutation(session, postId, mutation)
                }
                if (result.successful) {
                    store.acknowledge(mutation)
                    pushedAny = true
                }
            }
            if (pushedAny) {
                pull = pullWithOneRelogin(origin, session) ?: pull
                session = pull.session
                records = store.mergeRemote(login, pull.snapshot)
            }
            val pendingCount = store.pending().size
            LibrarySyncResult(
                records = records,
                authenticated = true,
                pendingCount = pendingCount,
                message = if (pendingCount == 0) {
                    "Закладки и статусы синхронизированы"
                } else {
                    "Ожидают отправки: $pendingCount"
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            localOnly("Синхронизация временно недоступна")
        }
    }

    private suspend fun pullWithOneRelogin(
        origin: String,
        initialSession: AccountSession,
    ): PullResult? = try {
        PullResult(remote.pullSnapshot(initialSession), initialSession)
    } catch (_: LibraryAuthenticationException) {
        val restored = sessionManager.reauthenticate(origin) ?: return null
        PullResult(remote.pullSnapshot(restored), restored)
    }

    private suspend fun applyMutation(
        session: AccountSession,
        postId: Long,
        mutation: PendingLibraryMutation,
    ): MutationAttempt = when (mutation.kind) {
        LibraryMutationKind.FAVORITE -> {
            val result = remote.setFavorite(
                session = session,
                postId = postId,
                enabled = mutation.value.toBoolean(),
            )
            MutationAttempt(result.successful, result.authenticationRequired)
        }
        LibraryMutationKind.STATUS -> {
            val status = WatchStatus.fromFolder(mutation.value)
            val result = remote.setStatus(session, postId, status)
            MutationAttempt(result.successful, result.authenticationRequired)
        }
    }

    private suspend fun localOnly(message: String): LibrarySyncResult {
        val records = store.list()
        return LibrarySyncResult(records, false, store.pending().size, message)
    }

    private data class MutationAttempt(
        val successful: Boolean,
        val authenticationRequired: Boolean,
    )

    private data class PullResult(
        val snapshot: RemoteLibrarySnapshot,
        val session: AccountSession,
    )
}
