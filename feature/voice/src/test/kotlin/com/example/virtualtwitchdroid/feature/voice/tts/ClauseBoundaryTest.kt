package com.example.virtualtwitchdroid.feature.voice.tts

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ClauseBoundaryTest {
    @Test
    fun pauses_andClauseFinalParticles_areSplittable() {
        assertTrue(ClauseBoundary.isSplittable(listOf("キョ", "ウ", "ワ"), hasPause = true))
        assertTrue(ClauseBoundary.isSplittable(listOf("フッ", "テ", "イ", "マ", "シ", "タ", "ガ"), hasPause = false)) // …ましたが
        assertTrue(ClauseBoundary.isSplittable(listOf("ナ", "ッ", "テ"), hasPause = false)) // …なって
        assertTrue(ClauseBoundary.isSplittable(listOf("ア", "ル", "ケ", "ド"), hasPause = false)) // …けど (two moras)
    }

    @Test
    fun ordinaryPhraseEnds_andSingleMoras_areNot() {
        assertFalse(ClauseBoundary.isSplittable(listOf("テ", "ン", "キ"), hasPause = false))
        assertFalse(ClauseBoundary.isSplittable(listOf("ガ"), hasPause = false))
        assertFalse(ClauseBoundary.isSplittable(emptyList(), hasPause = false))
    }
}
