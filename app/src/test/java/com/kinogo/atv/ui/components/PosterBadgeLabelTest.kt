package com.kinogo.atv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterBadgeLabelTest {
    @Test
    fun `display boundary removes legacy Russian and Ukrainian quality prefixes`() {
        assertEquals("WEB-DL 720p", "Качество: WEB-DL 720p".posterBadgeLabel())
        assertEquals("BD-Rip", "  Якість  BD-Rip ".posterBadgeLabel())
    }

    @Test
    fun `empty legacy quality label is not rendered`() {
        assertNull("Качество: ".posterBadgeLabel())
        assertNull(null.posterBadgeLabel())
    }
}
