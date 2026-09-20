package com.example.virtualtwitchdroid.feature.voice.tts

import jp.hiroshiba.voicevoxcore.AccelerationMode
import kotlin.test.assertEquals
import org.junit.Test

class AccelerationModeTest {
    @Test
    fun gpuOnlyWhenTheRuntimeReportsAGpuDevice() {
        // VOICEVOX's Android runtime reports neither CUDA nor DirectML → CPU.
        assertEquals(AccelerationMode.CPU, VoicevoxSpeaker.accelerationModeFor(gpuCuda = false, gpuDml = false))
        assertEquals(AccelerationMode.GPU, VoicevoxSpeaker.accelerationModeFor(gpuCuda = true, gpuDml = false))
        assertEquals(AccelerationMode.GPU, VoicevoxSpeaker.accelerationModeFor(gpuCuda = false, gpuDml = true))
    }
}
