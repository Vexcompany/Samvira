package com.vexcompany.samvira

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test for the Compose shell.
 *
 * Requires a device/emulator. CI compiles this source set (see workflow) but
 * does not run it, since the hosted runner has no emulator; connected tests can
 * be enabled later with a device farm or emulator-based runner.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_displaysBrand() {
        composeRule.onNodeWithText("SAMVIRA").assertIsDisplayed()
    }
}
