package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule(order = 0)
    val permissionRule = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        }
    }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_name_is_voxforge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("VoxForge", context.getString(R.string.app_name))
    }

    @Test
    fun three_main_tabs_and_single_start_recording_button_verified() {
        composeRule.waitForIdle()

        // Verify all 3 main tabs exist
        composeRule.onNodeWithTag("tab_recorder").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_voice_changer").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_noise_gate").assertIsDisplayed()

        // Verify Start Recording button exists on Recorder tab (only 1 in app)
        composeRule.onAllNodesWithTag("start_recording_button").assertCountEquals(1)
        composeRule.onNodeWithTag("start_recording_button").performScrollTo().assertIsDisplayed()

        // Switch to Voice Changer tab
        composeRule.onNodeWithTag("tab_voice_changer").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("start_recording_button").assertCountEquals(0)

        // Select Girl preset -> updates Pitch slider value (+4.5 st)
        composeRule.onNodeWithTag("preset_girl").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vc_pitch_value").performScrollTo().assertTextContains("+4.5 st")

        // Verify Monitoring Delay + / Reset controls
        composeRule.onNodeWithTag("monitoring_delay_plus").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("monitoring_delay_status")
            .assertTextContains("More Delay", substring = true)

        composeRule.onNodeWithTag("monitoring_delay_reset").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("monitoring_delay_status")
            .assertTextContains("Default", substring = true)

        // Switch to Noise Gate tab
        composeRule.onNodeWithTag("tab_noise_gate").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("start_recording_button").assertCountEquals(0)
        composeRule.onNodeWithTag("noise_gate_switch").assertIsDisplayed()
        composeRule.onNodeWithText("Threshold").assertIsDisplayed()
    }
}
