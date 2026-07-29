package com.kinogo.atv.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlayerQualityLabelTest {
    @Test
    fun `adaptive provider labels retain auto semantics`() {
        assertTrue(isAutoQuality("Авто"))
        assertTrue(isAutoQuality("Авто · HLS"))
        assertTrue(isAutoQuality("Auto · DASH"))
        assertFalse(isAutoQuality("1080p"))
        assertFalse(isAutoQuality("Автоматически"))
    }
}
