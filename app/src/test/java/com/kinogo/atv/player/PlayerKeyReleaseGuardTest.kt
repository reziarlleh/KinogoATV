package com.kinogo.atv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeyReleaseGuardTest {
    @Test
    fun `matching release is consumed exactly once`() {
        val guard = PlayerKeyReleaseGuard()

        guard.arm(23)

        assertTrue(guard.consumeRelease(23))
        assertFalse(guard.consumeRelease(23))
    }

    @Test
    fun `unrelated release does not disarm the pending key`() {
        val guard = PlayerKeyReleaseGuard()

        guard.arm(23)

        assertFalse(guard.consumeRelease(22))
        assertTrue(guard.consumeRelease(23))
    }
}
