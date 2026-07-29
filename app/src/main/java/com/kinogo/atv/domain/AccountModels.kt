package com.kinogo.atv.domain

data class StoredCredentials(
    val login: String,
    val password: String,
) {
    init {
        require(login.isNotBlank())
        require(password.isNotEmpty())
    }

    /** Prevent accidental disclosure through logs, crash reports, or result wrappers. */
    override fun toString(): String =
        "StoredCredentials(login=<redacted>, password=<redacted>)"
}

enum class AccountConnectionPhase {
    NO_CREDENTIALS,
    WAITING_FOR_MIRROR,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class AccountConnectionState(
    val phase: AccountConnectionPhase = AccountConnectionPhase.NO_CREDENTIALS,
    val login: String? = null,
    val displayName: String? = null,
    val credentialsSaved: Boolean = false,
    val message: String? = null,
) {
    val isAuthenticated: Boolean
        get() = phase == AccountConnectionPhase.CONNECTED
}

data class AccountSession(
    val origin: String,
    val loginHash: String,
    val login: String,
    val displayName: String? = null,
)
