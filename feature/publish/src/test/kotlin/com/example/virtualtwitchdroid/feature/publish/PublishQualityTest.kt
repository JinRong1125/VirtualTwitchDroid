package com.example.virtualtwitchdroid.feature.publish

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The publish quality options are PORTRAIT encode resolutions (width = the short "p" side < height),
 * with even dimensions (H.264 requires even width/height).
 */
class PublishQualityTest {

    @Test
    fun options_arePortrait_withEvenDimensions() {
        val entries = PublishController.VideoQuality.entries
        assertTrue(entries.isNotEmpty())
        entries.forEach { q ->
            assertTrue(q.width < q.height, "${q.name} should be portrait (w<h), was ${q.width}x${q.height}")
            assertEquals(0, q.width % 2, "${q.name} width must be even")
            assertEquals(0, q.height % 2, "${q.name} height must be even")
            assertTrue(q.labelRes != 0, "${q.name} should have a label string resource")
        }
    }

    @Test
    fun width_matchesTheEnumPValue() {
        // e.g. P720 ⇒ width 720 (the enum name's number is the short "p" side, now that the visible
        // label is a string resource resolved on-device rather than an inline "720p" String).
        PublishController.VideoQuality.entries.forEach { q ->
            assertEquals(q.name.removePrefix("P").toInt(), q.width, "${q.name} width should equal its p-value")
        }
    }
}
