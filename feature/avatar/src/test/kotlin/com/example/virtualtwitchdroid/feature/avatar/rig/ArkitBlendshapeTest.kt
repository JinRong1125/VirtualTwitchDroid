package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ArkitBlendshapeTest {

    @Test
    fun hasExactlyMediaPipesFiftyTwoCategories_withUniqueKeys() {
        assertEquals(52, ArkitBlendshape.entries.size)
        assertEquals(52, ArkitBlendshape.entries.map { it.key }.toSet().size)
    }

    @Test
    fun fromKey_resolvesMediaPipeCategoryNames() {
        assertEquals(ArkitBlendshape.JAW_OPEN, ArkitBlendshape.fromKey("jawOpen"))
        assertEquals(ArkitBlendshape.NEUTRAL, ArkitBlendshape.fromKey("_neutral"))
        assertNull(ArkitBlendshape.fromKey("tongueOut")) // ARKit-only, MediaPipe never emits it
    }

    @Test
    fun mirrored_swapsEverySidedPair_andKeepsUnsidedKeys() {
        val mirrored = ArkitBlendshape.mirrored(
            mapOf("eyeBlinkLeft" to 0.9f, "eyeBlinkRight" to 0.1f, "jawOpen" to 0.5f),
        )
        assertEquals(0.1f, mirrored["eyeBlinkLeft"])
        assertEquals(0.9f, mirrored["eyeBlinkRight"])
        assertEquals(0.5f, mirrored["jawOpen"])
        assertEquals(3, mirrored.size)
    }
}
