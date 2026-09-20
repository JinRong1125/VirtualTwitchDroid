package com.example.virtualtwitchdroid.core.designsystem.component

import kotlin.test.assertTrue
import org.junit.Test

class EmotesTest {

    @Test
    fun globalEmotes_areNonEmptyAndUnique() {
        assertTrue(GlobalEmotes.size >= 14)
        assertTrue(GlobalEmotes.map { it.name }.toSet().size == GlobalEmotes.size)
    }

    @Test
    fun globalEmotes_useTheTwitchCdnUrl() {
        assertTrue(
            GlobalEmotes.all {
                it.url.startsWith("https://static-cdn.jtvnw.net/emoticons/v2/") &&
                    it.name.isNotBlank()
            },
        )
    }
}
