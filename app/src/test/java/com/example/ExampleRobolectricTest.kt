package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.ImportedAudioEngine
import com.example.data.ImportedAudioSource
import com.example.dsp.BuiltInEffectPresets
import com.example.dsp.EffectsRackConfig
import com.example.dsp.EqualizerConfig
import com.example.dsp.LimiterConfig
import com.example.dsp.MusicalKey
import com.example.dsp.MusicalScale
import com.example.dsp.PitchCorrectionConfig
import com.example.dsp.PitchFormantConfig
import com.example.dsp.ReverbPreset
import com.example.dsp.StudioEffectPreset
import com.example.dsp.StudioEffectType
import com.example.dsp.VoicePreset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

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
    fun presets_and_imported_audio_engine_continuity_verified() {
        val pc = PitchCorrectionConfig(
            enabled = true,
            key = MusicalKey.F_SHARP,
            scale = MusicalScale.MINOR,
            correctionStrengthPercent = 82,
            referenceTuningHz = 442f
        )
        val parsedPc = PitchCorrectionConfig.fromJson(pc.toJson(), enabled = true)
        assertEquals(pc, parsedPc)

        val eq = EqualizerConfig(enabled = true, lowGainDb = 5f, highGainDb = -3f)
        val userList = listOf(
            StudioEffectPreset(
                id = "usr_test_1",
                name = "My Podcast EQ",
                effectType = StudioEffectType.EQUALIZER,
                isBuiltIn = false,
                payloadJson = eq.toJson().toString()
            )
        )
        val encoded = BuiltInEffectPresets.encodeUserPresets(userList)
        val decoded = BuiltInEffectPresets.decodeUserPresets(encoded)
        assertEquals(userList, decoded)

        val tempPcm = File.createTempFile("voxforge_import_", ".pcm")
        try {
            tempPcm.writeBytes(ByteArray(48_000 * 10 * 2))
            val engine = ImportedAudioEngine()
            engine.loadSource(
                ImportedAudioSource(
                    uri = Uri.fromFile(tempPcm),
                    displayName = "sample_track.wav",
                    formatLabel = "WAV",
                    fromVideo = false,
                    pcmFile = tempPcm,
                    totalFrames48k = 48_000L * 10L,
                    durationMs = 10_000L
                )
            )
            // Seek to 5 seconds (240,000 frames)
            engine.seekTo(5_000L)
            assertEquals(240_000L, engine.currentPlayheadFrame())
            assertEquals(5_000L, engine.state.value.positionMs)

            // Updating effects and voice changer must NEVER reset timeline to 0
            engine.updateEffectsConfig(
                EffectsRackConfig(
                    pitchFormant = PitchFormantConfig(enabled = true, pitchShiftSt = 3.0f, formantShiftSt = -1.5f),
                    reverb = ReverbPreset.HALL.applyTo(enabled = true),
                    limiter = LimiterConfig(enabled = true)
                )
            )
            engine.updateVoiceChangerConfig(VoicePreset.WOMAN.defaultConfig(enabled = true))

            assertEquals(240_000L, engine.currentPlayheadFrame())
            assertEquals(5_000L, engine.state.value.positionMs)
        } finally {
            tempPcm.delete()
        }
    }

    @Test
    fun import_audio_ui_and_ten_studio_effects_verified() {
        composeRule.waitForIdle()

        // Home screen must display both Start Recording and Import Audio; Save Audio stays hidden initially
        composeRule.onNodeWithTag("start_recording_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("import_audio_button").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("save_audio_button").assertCountEquals(0)

        // Open Import Audio overlay -> verify top Media & Audio tabs
        composeRule.onNodeWithTag("import_audio_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("import_audio_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("import_tab_media").assertIsDisplayed()
        composeRule.onNodeWithTag("import_tab_audio").assertIsDisplayed()
        composeRule.onNodeWithTag("import_choose_video_button").assertIsDisplayed()

        // Switch to Audio tab (without permission shows grant button)
        composeRule.onNodeWithTag("import_tab_audio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grant_audio_permission_button").assertIsDisplayed()

        // Close Import Audio overlay
        composeRule.onNodeWithTag("import_close_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("import_audio_screen").assertCountEquals(0)

        // Verify AUDIO EFFECTS & STUDIO CONTROLS header & all 10 collapsible effect cards
        composeRule.onNodeWithTag("home_lazy_column").performScrollToKey("studio_effects_header")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_effects_header").assertIsDisplayed()

        val expectedCardKeys = listOf(
            "studio_fx_pitch_correction",
            "studio_fx_pitch_formant",
            "studio_fx_equalizer",
            "studio_fx_compression",
            "studio_fx_saturation",
            "studio_fx_reverb",
            "studio_fx_delay",
            "studio_fx_noise_gate",
            "studio_fx_de_esser",
            "studio_fx_limiter"
        )
        for (cardKey in expectedCardKeys) {
            composeRule.onNodeWithTag("home_lazy_column").performScrollToKey(cardKey)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("${cardKey}_card").assertIsDisplayed()
            composeRule.onNodeWithTag("${cardKey}_switch").assertIsOff()
        }

        // Expand Pitch Correction -> verifies scale/retune controls + Preset buttons; toggle OFF collapses
        composeRule.onNodeWithTag("home_lazy_column").performScrollToKey("studio_fx_pitch_correction")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_fx_pitch_correction_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_fx_pitch_correction_switch").assertIsOn()
        composeRule.onNodeWithTag("studio_fx_pitch_correction_reset").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("studio_fx_pitch_correction_save_preset").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Correction Strength").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Retune Speed").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("studio_fx_pitch_correction_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Correction Strength").assertCountEquals(0)

        // Expand Equalizer -> verifies EQ frequency response graph
        composeRule.onNodeWithTag("home_lazy_column").performScrollToKey("studio_fx_equalizer")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_fx_equalizer_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("eq_frequency_graph").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("studio_fx_equalizer_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("eq_frequency_graph").assertCountEquals(0)

        // Enable Noise Gate on Home Screen -> verify synchronized ON in Effects Tab, then reset OFF
        composeRule.onNodeWithTag("home_lazy_column").performScrollToKey("studio_fx_noise_gate")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_fx_noise_gate_switch").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab_effects").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fx_noise_gate_switch").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("fx_noise_gate_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fx_noise_gate_switch").assertIsOff()
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
