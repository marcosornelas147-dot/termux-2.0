package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `main activity renders browser bridge external terminal and workspace destinations`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tab_switcher_button").assertIsDisplayed()
        composeRule.onNodeWithTag("address_bar_input").assertIsDisplayed()
        composeRule.onNodeWithTag("start_page_bridge_card").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_bridge").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bridge_status_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("bridge_connect_button").assertExists()

        composeRule.onNodeWithTag("nav_external_terminal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("external_terminal_input").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_workspace").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("terminal_command_input").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_tab_projects").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace_code_editor").assertIsDisplayed()
    }
}
