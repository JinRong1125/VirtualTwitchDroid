package com.example.virtualtwitchdroid.feature.voice

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class VoiceSupportTest {
    @Test
    fun requiresApi26_and64Bit() {
        assertTrue(VoiceSupport.isVoiceSupported(26, has64BitAbi = true))
        assertTrue(VoiceSupport.isVoiceSupported(35, has64BitAbi = true))
        assertFalse(VoiceSupport.isVoiceSupported(25, has64BitAbi = true)) // VOICEVOX AAR minSdk
        assertFalse(VoiceSupport.isVoiceSupported(30, has64BitAbi = false)) // no 64-bit libraries shipped
    }
}
