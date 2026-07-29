package com.kinogo.atv.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.kinogo.atv.domain.TvPreferences
import com.kinogo.atv.ui.model.KinogoFixtures
import com.kinogo.atv.ui.model.withPreferences
import org.junit.Rule
import org.junit.Test

class SettingsScreenDpadTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusedSettingHandlesLeftRightAndCenterWithoutLosingFocus() {
        composeRule.setContent {
            var preferences by remember { mutableStateOf(TvPreferences()) }
            MaterialTheme {
                SettingsScreen(
                    sections = KinogoFixtures.settings.withPreferences(preferences),
                    onSettingChanged = { settingId, direction ->
                        preferences = preferences.cycle(settingId, direction)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasTestTag("setting-quality"))
        val quality = composeRule.onNodeWithTag("setting-quality")
        quality.performSemanticsAction(SemanticsActions.RequestFocus)

        quality.performKeyInput { pressKey(Key.DirectionRight) }
        quality.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "4K"),
        )

        quality.performKeyInput { pressKey(Key.DirectionLeft) }
        quality.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Авто"),
        )

        quality.performKeyInput { pressKey(Key.Enter) }
        quality.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "4K"),
        )
    }
}
