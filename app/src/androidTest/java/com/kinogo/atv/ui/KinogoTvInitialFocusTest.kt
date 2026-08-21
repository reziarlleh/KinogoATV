package com.kinogo.atv.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
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

    @Test
    fun railBrandOpensTheSharedAboutDialog() {
        composeRule.setContent {
            KinogoTvApp(appVersionName = "test")
        }

        composeRule.onNodeWithContentDescription("Главная")
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithContentDescription("О программе")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithText("Версия test · Android TV 9+").assertIsDisplayed()
    }
}
