package com.example.virtualtwitchdroid.feature.publish

import kotlin.test.assertEquals
import org.junit.Test

class TwitchIngestTest {

    @Test
    fun rtmpUrl_appendsKeyToTwitchIngestBase() {
        assertEquals(
            "rtmp://live.twitch.tv/app/live_123_abc",
            TwitchIngest.rtmpUrl("live_123_abc"),
        )
    }

    @Test
    fun rtmpUrl_trimsSurroundingWhitespace() {
        assertEquals(
            "rtmp://live.twitch.tv/app/key",
            TwitchIngest.rtmpUrl("  key\n"),
        )
    }
}
