package com.kinogo.atv.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUiPolicyTest {
    @Test
    fun `typing debounce is deliberately longer than old 350ms delay`() {
        assertTrue(SEARCH_DEBOUNCE_MILLIS >= 700L)
    }
}
