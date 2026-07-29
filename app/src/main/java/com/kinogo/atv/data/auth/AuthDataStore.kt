package com.kinogo.atv.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

const val KINOGO_AUTH_DATASTORE_NAME = "kinogo_auth"

/** Auth is isolated from catalog/history preferences and persists across app restarts. */
val Context.kinogoAuthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = KINOGO_AUTH_DATASTORE_NAME,
)

fun Context.createCredentialStore(
    cipher: CredentialCipher = AndroidKeystoreCredentialCipher(),
): CredentialStore {
    val appContext = applicationContext
    return CredentialStore(appContext.kinogoAuthDataStore, cipher)
}
