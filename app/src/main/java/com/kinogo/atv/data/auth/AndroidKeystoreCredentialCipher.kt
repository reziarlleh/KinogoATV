package com.kinogo.atv.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES/GCM credentials encryption with a non-exportable Android Keystore key. */
class AndroidKeystoreCredentialCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : CredentialCipher {
    init {
        require(keyAlias.isNotBlank()) { "Key alias must not be blank" }
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_PLAINTEXT_BYTES) {
            "Plaintext has an invalid size"
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size in MIN_IV_BYTES..MAX_IV_BYTES)

        return ByteBuffer.allocate(MAGIC.size + 1 + iv.size + ciphertext.size)
            .put(MAGIC)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    override fun decrypt(encryptedPayload: ByteArray): ByteArray {
        require(encryptedPayload.size in MIN_ENCRYPTED_BYTES..MAX_ENCRYPTED_BYTES) {
            "Encrypted payload has an invalid size"
        }

        val buffer = ByteBuffer.wrap(encryptedPayload)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC))
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength in MIN_IV_BYTES..MAX_IV_BYTES)
        require(buffer.remaining() >= ivLength + GCM_TAG_BYTES)
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey) ?: run {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            keyGenerator.generateKey()
        }
    }

    private companion object {
        const val DEFAULT_KEY_ALIAS = "kinogo_auth_credentials_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / Byte.SIZE_BITS
        const val MIN_IV_BYTES = 12
        const val MAX_IV_BYTES = 32
        const val MAX_PLAINTEXT_BYTES = 160 * 1024
        const val MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + MAX_IV_BYTES + GCM_TAG_BYTES + 5
        const val MIN_ENCRYPTED_BYTES = 4 + 1 + MIN_IV_BYTES + GCM_TAG_BYTES
        val MAGIC = byteArrayOf('K'.code.toByte(), 'A'.code.toByte(), 'E'.code.toByte(), 1)
        val AAD = "com.kinogo.atv:credentials:v1".toByteArray(Charsets.UTF_8)
        val KEY_LOCK = Any()
    }
}
