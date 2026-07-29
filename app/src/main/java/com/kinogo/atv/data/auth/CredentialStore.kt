package com.kinogo.atv.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kinogo.atv.domain.StoredCredentials
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

sealed interface CredentialReadResult {
    data object Missing : CredentialReadResult

    data class Available(val credentials: StoredCredentials) : CredentialReadResult

    /** A blob exists but cannot be decoded or decrypted with the current device key. */
    data object Unreadable : CredentialReadResult
}

sealed interface CredentialWriteResult {
    data object Saved : CredentialWriteResult

    /** Keystore or persistent storage was unavailable; no sensitive details are exposed. */
    data object Unavailable : CredentialWriteResult
}

/** Persistent encrypted login/password storage. It deliberately does not expire credentials. */
class CredentialStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: CredentialCipher,
) {
    suspend fun read(): StoredCredentials? =
        (readResult() as? CredentialReadResult.Available)?.credentials

    suspend fun readResult(): CredentialReadResult {
        val stored = try {
            dataStore.data.first()[CREDENTIALS_KEY]
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CredentialReadResult.Unreadable
        } ?: return CredentialReadResult.Missing

        if (stored.isBlank() || stored.length > MAX_STORED_BLOB_CHARS) {
            return CredentialReadResult.Unreadable
        }

        var encrypted: ByteArray? = null
        var plaintext: ByteArray? = null
        return try {
            encrypted = Base64.getUrlDecoder().decode(stored)
            plaintext = cipher.decrypt(encrypted)
            CredentialCodec.decodeOrNull(plaintext)
                ?.let(CredentialReadResult::Available)
                ?: CredentialReadResult.Unreadable
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CredentialReadResult.Unreadable
        } finally {
            encrypted?.fill(0)
            plaintext?.fill(0)
        }
    }

    suspend fun save(credentials: StoredCredentials): CredentialWriteResult {
        var plaintext: ByteArray? = null
        var encrypted: ByteArray? = null
        return try {
            plaintext = CredentialCodec.encode(credentials)
            encrypted = cipher.encrypt(plaintext)
            val stored = Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
            require(stored.length <= MAX_STORED_BLOB_CHARS)
            dataStore.edit { preferences -> preferences[CREDENTIALS_KEY] = stored }
            CredentialWriteResult.Saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CredentialWriteResult.Unavailable
        } finally {
            plaintext?.fill(0)
            encrypted?.fill(0)
        }
    }

    suspend fun clear(): CredentialWriteResult = try {
        dataStore.edit { preferences -> preferences.remove(CREDENTIALS_KEY) }
        CredentialWriteResult.Saved
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CredentialWriteResult.Unavailable
    }

    private companion object {
        val CREDENTIALS_KEY = stringPreferencesKey("encrypted_credentials_v1")
        const val MAX_STORED_BLOB_CHARS = 256 * 1024
    }
}
