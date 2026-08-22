package com.kinogo.atv.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import com.kinogo.atv.ui.KinogoTvTheme
import com.kinogo.atv.ui.model.RegistrationUiModel
import com.kinogo.atv.ui.model.RegistrationUiPhase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegistrationDialogDpadTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rulesGateStartsOnDeclineAndAcceptsOnlyExplicitCenterPress() {
        var accepted = false
        composeRule.setContent {
            KinogoTvTheme {
                RegistrationDialog(
                    state = RegistrationUiModel(
                        phase = RegistrationUiPhase.RULES,
                        rulesText = "Правила выбранного сайта",
                    ),
                    onDismiss = {},
                    onRetry = {},
                    onAcceptRules = { accepted = true },
                    onRefreshCaptcha = {},
                    onSubmit = {},
                )
            }
        }

        composeRule.onNodeWithText("Не принимаю").assertIsFocused()
        assertTrue(!accepted)

        composeRule.onNodeWithText("Не принимаю").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("registration_rules_scroll").assertIsFocused()
        composeRule.onNodeWithTag("registration_rules_scroll").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithText("Не принимаю").assertIsFocused()

        val accept = composeRule.onNodeWithText("Принимаю и продолжить")
        accept.performSemanticsAction(SemanticsActions.RequestFocus)
        accept.performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertTrue(accepted) }
    }
}
