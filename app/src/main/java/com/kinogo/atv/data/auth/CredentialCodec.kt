package com.kinogo.atv.data.auth

import com.kinogo.atv.domain.StoredCredentials
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Strict, versioned binary codec used before encryption.
 *
 * Length prefixes preserve every character in the login and password, including whitespace and
 * line breaks. Malformed or unsupported payloads are rejected as a whole.
 */
object CredentialCodec {
    private val MAGIC = byteArrayOf('K'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte(), 'V'.code.toByte())
    private const val VERSION: Byte = 1
    private const val LENGTH_BYTES = Int.SIZE_BYTES
    private const val MAX_FIELD_BYTES = 64 * 1024
    private const val HEADER_BYTES = 4 + 1 + LENGTH_BYTES + LENGTH_BYTES
    private const val MAX_PAYLOAD_BYTES = HEADER_BYTES + (MAX_FIELD_BYTES * 2)

    fun encode(credentials: StoredCredentials): ByteArray {
        val login = credentials.login.toByteArray(StandardCharsets.UTF_8)
        val password = credentials.password.toByteArray(StandardCharsets.UTF_8)
        require(login.size in 1..MAX_FIELD_BYTES) { "Credential field has an invalid size" }
        require(password.size in 1..MAX_FIELD_BYTES) { "Credential field has an invalid size" }

        return ByteBuffer.allocate(HEADER_BYTES + login.size + password.size)
            .put(MAGIC)
            .put(VERSION)
            .putInt(login.size)
            .put(login)
            .putInt(password.size)
            .put(password)
            .array()
    }

    fun decode(payload: ByteArray): StoredCredentials =
        decodeOrNull(payload) ?: throw IllegalArgumentException("Invalid credentials payload")

    fun decodeOrNull(payload: ByteArray?): StoredCredentials? {
        if (payload == null || payload.size !in HEADER_BYTES..MAX_PAYLOAD_BYTES) return null
        return runCatching { decodeStrict(payload) }.getOrNull()
    }

    private fun decodeStrict(payload: ByteArray): StoredCredentials {
        val buffer = ByteBuffer.wrap(payload)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC))
        require(buffer.get() == VERSION)

        val login = buffer.readField()
        val password = buffer.readField()
        require(!buffer.hasRemaining())

        return StoredCredentials(
            login = login.decodeUtf8Strict(),
            password = password.decodeUtf8Strict(),
        )
    }

    private fun ByteBuffer.readField(): ByteArray {
        require(remaining() >= LENGTH_BYTES)
        val length = int
        require(length in 1..MAX_FIELD_BYTES && remaining() >= length)
        return ByteArray(length).also(::get)
    }

    private fun ByteArray.decodeUtf8Strict(): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
}
