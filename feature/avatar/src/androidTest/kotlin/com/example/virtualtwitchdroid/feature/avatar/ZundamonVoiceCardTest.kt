package com.example.virtualtwitchdroid.feature.avatar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.virtualtwitchdroid.core.common.media.PcmSink
import com.example.virtualtwitchdroid.core.common.media.SpeechStreamSource
import com.example.virtualtwitchdroid.core.common.media.VoiceState
import com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * The Avatar tab's voice card on a fake voice: the mandatory VOICEVOX credit is always visible, Listen starts
 * the voice and turns into Stop while it runs, and the rehearsal controls yield while Go Live owns the voice.
 */
class ZundamonVoiceCardTest {

    @get:Rule val rule = createComposeRule()

    private class FakeVoice : SpeechStreamSource {
        override val supported = true
        override val state = MutableStateFlow(VoiceState.IDLE)
        override val lastText = MutableStateFlow<String?>(null)
        override val lagSeconds = MutableStateFlow(0f)
        override val error = MutableStateFlow<String?>(null)
        override val muted = MutableStateFlow(false)
        override val broadcasting = MutableStateFlow(false)
        override fun start() {
            state.value = VoiceState.LISTENING
        }

        override fun stop() {
            state.value = VoiceState.IDLE
        }

        override fun say(text: String) = Unit

        override fun setMuted(muted: Boolean) = Unit

        override fun attachAudioSink(sink: PcmSink) = Unit

        override fun detachAudioSink() = Unit
    }

    @Test
    fun creditIsAlwaysShown_andTheButtonFollowsTheVoiceState() {
        val voice = FakeVoice()
        rule.setContent { TwitchTheme { ZundamonVoiceCard(voice) } }
        rule.onNodeWithText("VOICEVOX:ずんだもん · ReazonSpeech k2 v2 (sherpa-onnx)").assertIsDisplayed()
        rule.onNodeWithText("Voice off").assertIsDisplayed()
        rule.onNodeWithText("Listen (日本語)").assertIsEnabled()
        // (Tapping Listen asks for RECORD_AUDIO first when the test process lacks it, so the state is driven here.)
        voice.state.value = VoiceState.LISTENING
        rule.onNodeWithText("Stop").assertIsDisplayed()
        rule.onNodeWithText("Listening · lag 0.0 s").assertIsDisplayed()
        rule.onNodeWithText("VOICEVOX:ずんだもん · ReazonSpeech k2 v2 (sherpa-onnx)").assertIsDisplayed()
        rule.onNodeWithText("Stop").performClick()
        rule.runOnIdle { assertEquals(VoiceState.IDLE, voice.state.value) }
        rule.onNodeWithText("Voice off").assertIsDisplayed()
    }

    @Test
    fun controlsYieldWhileGoLiveOwnsTheVoice() {
        val voice = FakeVoice().apply { broadcasting.value = true }
        rule.setContent { TwitchTheme { ZundamonVoiceCard(voice) } }
        rule.onNodeWithText("Listen (日本語)").assertIsNotEnabled()
        rule.onNodeWithText("In use by Go Live").assertIsDisplayed()
    }
}
