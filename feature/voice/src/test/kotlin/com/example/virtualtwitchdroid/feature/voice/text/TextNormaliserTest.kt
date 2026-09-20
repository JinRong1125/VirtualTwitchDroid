package com.example.virtualtwitchdroid.feature.voice.text

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class TextNormaliserTest {
    @Test
    fun appendsATerminalMark_whenTheRecogniserGaveNone() {
        assertEquals("こんにちは。", TextNormaliser.normalise("こんにちは"))
        assertEquals("こんにちは。", TextNormaliser.normalise("こんにちは。"))
        assertEquals("元気？", TextNormaliser.normalise("元気？"))
    }

    @Test
    fun dropsTooShortSegments_andCollapsesWhitespaceAndFillers() {
        assertNull(TextNormaliser.normalise("あ"))
        assertNull(TextNormaliser.normalise("   "))
        assertEquals("今日はいい天気。", TextNormaliser.normalise("今日は　いい 天気"))
        assertEquals("あのそれで。", TextNormaliser.normalise("あのあのあのそれで"))
    }

    @Test
    fun homophonesOfTheStreamsNames_areCorrected() {
        // The kanji before んだ and the もん-sound tail both vary with what the lexicon-less recogniser guesses
        // (Pixel 8a produced もん, 門 and 紋); all three tails map back to the name.
        assertEquals("この配信ではずんだもんの声で話します。", TextNormaliser.normalise("この配信では詰んだもんの声で話します"))
        assertEquals("ずんだもんです。", TextNormaliser.normalise("済んだもんです"))
        assertEquals("この配信ではずんだもんの声で話します。", TextNormaliser.normalise("この配信では澄んだ門の声で話します"))
        assertEquals("この配信ではずんだもんの声で話します。", TextNormaliser.normalise("この配信では済んだ紋の声で話します"))
        assertEquals("この配信でずんだもんの声で話します。", TextNormaliser.normalise("この配信で弾んだモンの声で話します"))
        assertEquals("ずんだもんと四国めたん。", TextNormaliser.normalise("澄んだもんと四国メタン"))
        // Left alone: no んだ+もん-tail (a bare verb), and a real もん-homophone kanji we deliberately exclude.
        assertEquals("もう済んだ。", TextNormaliser.normalise("もう済んだ"))
        assertEquals("読んだ文はどれ？", TextNormaliser.normalise("読んだ文はどれ？"))
    }

    @Test
    fun knownLatinTokens_becomeKatakana_unknownOnesAreKept() {
        assertEquals("ユーチューブを見た。", TextNormaliser.normalise("YouTubeを見た"))
        assertEquals("エーアイとアンドロイド。", TextNormaliser.normalise("AIとAndroid"))
        assertEquals("NHKのニュース。", TextNormaliser.normalise("NHKのニュース")) // left for Open JTalk to spell
    }
}
