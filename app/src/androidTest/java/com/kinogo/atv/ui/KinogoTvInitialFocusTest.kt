package com.kinogo.atv.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

class KinogoTvInitialFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun coldShellStartsOnSelectedNavigationItem() {
        composeRule.setContent {
            KinogoTvApp()
        }

        composeRule.onNodeWithContentDescription("Главная").assertIsFocused()
    }
}
