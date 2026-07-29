package com.kinogo.atv.player.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemarWebViewRecoveryStateTest {
    @Test
    fun `ordinary load error retries on the same live WebView`() {
        val initial = CinemarWebViewRecoveryState(instanceGeneration = 4)

        val retry = initial.retry(currentInstanceCanReload = true)

        assertEquals(CinemarWebViewRecoveryAction.RELOAD, retry.action)
        assertEquals(initial, retry.nextState)
    }

    @Test
    fun `renderer crash retries with a fresh WebView generation`() {
        val crashed = CinemarWebViewRecoveryState(instanceGeneration = 4).onRendererGone()

        val retry = crashed.retry(currentInstanceCanReload = true)

        assertTrue(crashed.rendererGone)
        assertEquals(CinemarWebViewRecoveryAction.RECREATE, retry.action)
        assertEquals(5, retry.nextState.instanceGeneration)
        assertFalse(retry.nextState.rendererGone)
    }

    @Test
    fun `missing or destroyed current instance is recreated even without crash flag`() {
        val initial = CinemarWebViewRecoveryState(instanceGeneration = 9)

        val retry = initial.retry(currentInstanceCanReload = false)

        assertEquals(CinemarWebViewRecoveryAction.RECREATE, retry.action)
        assertEquals(10, retry.nextState.instanceGeneration)
        assertFalse(retry.nextState.rendererGone)
    }
}
