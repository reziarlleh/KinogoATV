package com.kinogo.atv.data.auth

import com.kinogo.atv.data.catalog.KinogoSessionHttpClient
import com.kinogo.atv.domain.AccountConnectionPhase
import com.kinogo.atv.domain.AccountConnectionState
import com.kinogo.atv.domain.AccountSession
import com.kinogo.atv.domain.StoredCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AuthAttempt(
    val session: AccountSession?,
    val errorMessage: String? = null,
)

class KinogoAuthApi(
    private val client: KinogoSessionHttpClient,
) {
    suspend fun authenticate(
        origin: String,
        credentials: StoredCredentials,
        forceLogin: Boolean,
    ): AuthAttempt {
        if (!forceLogin) {
            val existing = client.getRaw(origin, "/")
            if (existing.statusCode in 200..299) {
                val page = HtmlAuthParser.parse(existing.body)
                if (page.isAuthenticated && !page.loginHash.isNullOrBlank()) {
                    return AuthAttempt(
                        AccountSession(origin, page.loginHash, credentials.login, page.displayName),
                    )
                }
            }
        } else {
            client.clearCookies(origin)
        }

        val response = client.postForm(
            rawOrigin = origin,
            rawRelativePath = "/",
            form = linkedMapOf(
                "login_name" to credentials.login,
                "login_password" to credentials.password,
                "login" to "submit",
            ),
        )
        if (response.statusCode !in 200..299) {
            return AuthAttempt(null, "Сервер вернул HTTP ${response.statusCode}")
        }
        var page = HtmlAuthParser.parse(response.body)
        if (page.isAuthenticated && page.loginHash.isNullOrBlank()) {
            val refreshed = client.getRaw(origin, "/")
            if (refreshed.statusCode in 200..299) page = HtmlAuthParser.parse(refreshed.body)
        }
        return if (page.isAuthenticated && !page.loginHash.isNullOrBlank()) {
            AuthAttempt(
                AccountSession(origin, requireNotNull(page.loginHash), credentials.login, page.displayName),
            )
        } else {
            AuthAttempt(
                session = null,
                errorMessage = page.errorMessage ?: "Логин или пароль не приняты сайтом",
            )
        }
    }
}

/** Owns origin-isolated cookie sessions and silently restores them from persisted credentials. */
class KinogoSessionManager(
    private val credentialStore: CredentialStore,
    private val authApi: KinogoAuthApi,
    private val client: KinogoSessionHttpClient,
) {
    private val mutex = Mutex()
    private val sessionsByOrigin = linkedMapOf<String, AccountSession>()
    private val mutableState = MutableStateFlow(AccountConnectionState())

    val state: StateFlow<AccountConnectionState> = mutableState.asStateFlow()

    suspend fun restore(origin: String): AccountSession? = mutex.withLock {
        val read = credentialStore.readResult()
        when (read) {
            CredentialReadResult.Missing -> {
                mutableState.value = AccountConnectionState()
                null
            }
            CredentialReadResult.Unreadable -> {
                mutableState.value = AccountConnectionState(
                    phase = AccountConnectionPhase.ERROR,
                    credentialsSaved = true,
                    message = "Сохранённые данные входа повреждены или недоступны на этом устройстве",
                )
                null
            }
            is CredentialReadResult.Available -> authenticateLocked(origin, read.credentials, false)
        }
    }

    suspend fun saveAndLogin(origin: String, credentials: StoredCredentials): AccountSession? =
        mutex.withLock {
            // Credentials remain saved even when the current password is rejected by the site.
            val saved = credentialStore.save(credentials)
            if (saved != CredentialWriteResult.Saved) {
                mutableState.value = AccountConnectionState(
                    phase = AccountConnectionPhase.ERROR,
                    login = credentials.login,
                    credentialsSaved = false,
                    message = "Не удалось сохранить данные входа на устройстве",
                )
                return@withLock null
            }
            sessionsByOrigin.keys.toList().forEach(client::clearCookies)
            sessionsByOrigin.clear()
            authenticateLocked(origin, credentials, true)
        }

    /** Persists credentials even when no verified mirror is currently available. */
    suspend fun saveForLater(credentials: StoredCredentials): Boolean = mutex.withLock {
        val saved = credentialStore.save(credentials)
        if (saved == CredentialWriteResult.Saved) {
            sessionsByOrigin.keys.toList().forEach(client::clearCookies)
            sessionsByOrigin.clear()
            mutableState.value = AccountConnectionState(
                phase = AccountConnectionPhase.WAITING_FOR_MIRROR,
                login = credentials.login,
                credentialsSaved = true,
                message = "Данные сохранены. Вход продолжится после проверки зеркала.",
            )
            true
        } else {
            mutableState.value = AccountConnectionState(
                phase = AccountConnectionPhase.ERROR,
                login = credentials.login,
                credentialsSaved = false,
                message = "Не удалось сохранить данные входа на устройстве",
            )
            false
        }
    }

    suspend fun ensureAuthenticated(origin: String): AccountSession? = mutex.withLock {
        sessionsByOrigin[origin]?.let { return@withLock it }
        val credentials = credentialStore.read()
        if (credentials == null) {
            mutableState.value = AccountConnectionState()
            return@withLock null
        }
        authenticateLocked(origin, credentials, false)
    }

    suspend fun reauthenticate(origin: String): AccountSession? = mutex.withLock {
        val credentials = credentialStore.read() ?: return@withLock null
        sessionsByOrigin.remove(origin)
        authenticateLocked(origin, credentials, true)
    }

    suspend fun removeSavedAccount() = mutex.withLock {
        sessionsByOrigin.keys.toList().forEach(client::clearCookies)
        sessionsByOrigin.clear()
        credentialStore.clear()
        mutableState.value = AccountConnectionState()
    }

    private suspend fun authenticateLocked(
        origin: String,
        credentials: StoredCredentials,
        forceLogin: Boolean,
    ): AccountSession? {
        mutableState.value = AccountConnectionState(
            phase = AccountConnectionPhase.CONNECTING,
            login = credentials.login,
            credentialsSaved = true,
            message = "Подключаем аккаунт…",
        )
        return try {
            val attempt = authApi.authenticate(origin, credentials, forceLogin)
            val session = attempt.session
            if (session != null) {
                sessionsByOrigin[origin] = session
                mutableState.value = AccountConnectionState(
                    phase = AccountConnectionPhase.CONNECTED,
                    login = credentials.login,
                    displayName = session.displayName,
                    credentialsSaved = true,
                    message = "Закладки и статусы синхронизируются",
                )
            } else {
                sessionsByOrigin.remove(origin)
                mutableState.value = AccountConnectionState(
                    phase = AccountConnectionPhase.ERROR,
                    login = credentials.login,
                    credentialsSaved = true,
                    message = attempt.errorMessage,
                )
            }
            session
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            sessionsByOrigin.remove(origin)
            mutableState.value = AccountConnectionState(
                phase = AccountConnectionPhase.ERROR,
                login = credentials.login,
                credentialsSaved = true,
                message = "Не удалось подключиться. Данные входа сохранены для повтора.",
            )
            null
        }
    }
}
