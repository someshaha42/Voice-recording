package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
    fun voice_changer_redesign_and_effects_tab_verified() {
        composeRule.waitForIdle()

        // Verify all 3 main tabs exist: Recorder, Voice Changer, Effects
        composeRule.onNodeWithTag("tab_recorder").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_voice_changer").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_effects").assertIsDisplayed()

        // Verify Start Recording button exists on Recorder tab (only 1 in app)
        composeRule.onAllNodesWithTag("start_recording_button").assertCountEquals(1)
        composeRule.onNodeWithTag("start_recording_button").performScrollTo().assertIsDisplayed()

        // Switch to Voice Changer tab
        composeRule.onNodeWithTag("tab_voice_changer").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("start_recording_button").assertCountEquals(0)

        // Default Voice Changer state must be OFF
        composeRule.onNodeWithTag("voice_changer_switch").performScrollTo().assertIsOff()

        // Verify all 5 presets exist: Normal, Female, Child, Girl, Woman
        composeRule.onNodeWithTag("preset_normal").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preset_female").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preset_child").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preset_girl").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preset_woman").performScrollTo().assertIsDisplayed()

        // Select Girl preset -> loads Girl's optimized Pitch Level (88%), Tone Level (82%), Clarity (68%)
        composeRule.onNodeWithTag("preset_girl").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vc_pitch_level_value").performScrollTo().assertTextContains("88%")
        composeRule.onNodeWithTag("vc_tone_level_value").performScrollTo().assertTextContains("82%")
        composeRule.onNodeWithTag("vc_clarity_value").performScrollTo().assertTextContains("68%")

        // Toggle Voice Changer ON
        composeRule.onNodeWithTag("voice_changer_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_changer_switch").assertIsOn()

        // Verify removed individual DSP controllers are NOT present
        composeRule.onAllNodesWithText("Formant").assertCountEquals(0)
        composeRule.onAllNodesWithText("Fine Tune").assertCountEquals(0)
        composeRule.onAllNodesWithText("Dry / Wet Mix").assertCountEquals(0)

        // Verify Monitoring Delay & Volume controls are preserved
        composeRule.onNodeWithTag("monitoring_delay_plus").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("monitoring_delay_status")
            .assertTextContains("More Delay", substring = true)

        composeRule.onNodeWithTag("monitoring_delay_reset").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("monitoring_delay_status")
            .assertTextContains("Default", substring = true)

        composeRule.onNodeWithTag("monitoring_volume_value").performScrollTo().assertTextContains("100%")
        composeRule.onNodeWithTag("monitoring_volume_plus").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("monitoring_volume_value").assertTextContains("110%")
        composeRule.onNodeWithTag("monitoring_aec_switch").performScrollTo().assertIsDisplayed()

        // Switch to Effects tab
        composeRule.onNodeWithTag("tab_effects").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("start_recording_button").assertCountEquals(0)

        // All 7 effects must be present and collapsed when OFF
        composeRule.onNodeWithText("Noise Gate").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Low-pass").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("High-pass").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Equalizer").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reverb").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Echo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hall").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Threshold").assertCountEquals(0)

        // Enable Noise Gate -> expands controls; Disable -> collapses back
        composeRule.onNodeWithTag("fx_noise_gate_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Threshold").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hold").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Range").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("fx_noise_gate_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Threshold").assertCountEquals(0)
    }
}
