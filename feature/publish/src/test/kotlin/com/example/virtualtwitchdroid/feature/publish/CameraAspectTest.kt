package com.example.virtualtwitchdroid.feature.publish

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The floating mini uses the camera's own PORTRAIT aspect (9:16, the 720×1280 encode) — tall, so the
 * upright camera shows undistorted (vs. the watch/stream mini's 16:9).
 */
class CameraAspectTest {

    @Test
    fun cameraViewAspect_isPortrait_9by16() {
        val aspect = cameraViewAspectRatio() // width / height
        assertTrue(aspect < 1f, "camera view aspect (w/h) should be < 1 (tall/portrait), was $aspect")
        assertEquals(9f / 16f, aspect, 1e-4f)
    }
}
