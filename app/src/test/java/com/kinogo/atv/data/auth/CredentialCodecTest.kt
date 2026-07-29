package com.kinogo.atv.data.auth

import com.kinogo.atv.domain.StoredCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialCodecTest {
    @Test
    fun `codec round trips exact unicode credentials and whitespace`() {
        val credentials = StoredCredentials(
            login = " пользователь@example.org\nTV ",
            password = " пароль\tс emoji 📺 и переводом строки\n ",
        )

        assertEquals(credentials, CredentialCodec.decode(CredentialCodec.encode(credentials)))
    }

    @Test
    fun `decoder rejects truncated random and trailing payloads`() {
        val encoded = CredentialCodec.encode(StoredCredentials("login", "password"))

        assertNull(CredentialCodec.decodeOrNull(encoded.copyOf(encoded.size - 1)))
        assertNull(CredentialCodec.decodeOrNull(byteArrayOf(1, 2, 3, 4, 5)))
        assertNull(CredentialCodec.decodeOrNull(encoded + 0))
    }

    @Test
    fun `decoder rejects unsupported version`() {
        val encoded = CredentialCodec.encode(StoredCredentials("login", "password"))
        encoded[4] = 2

        assertNull(CredentialCodec.decodeOrNull(encoded))
    }

    @Test
    fun `credential string representation never contains login or password`() {
        val credentials = StoredCredentials("distinct-login", "distinct-password")
        val representation = credentials.toString()

        assertFalse(representation.contains(credentials.login))
        assertFalse(representation.contains(credentials.password))
        assertTrue(representation.contains("redacted"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `model rejects an empty password`() {
        StoredCredentials("login", "")
    }
}
