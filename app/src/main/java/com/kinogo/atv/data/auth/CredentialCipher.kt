package com.kinogo.atv.data.auth

/** Encrypts a complete credential payload, including the IV needed for a later decryption. */
interface CredentialCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(encryptedPayload: ByteArray): ByteArray
}
