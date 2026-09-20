package com.example.virtualtwitchdroid.feature.avatar.tracking

import com.example.virtualtwitchdroid.feature.avatar.rig.HeadPose
import com.example.virtualtwitchdroid.feature.avatar.rig.RotationMath
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** The pure result→frame conversion (the MediaPipe listener itself needs a device). */
class MediaPipeFaceTrackerTest {

    @Test
    fun noBlendshapes_meansNoFace() {
        val frame = MediaPipeFaceTracker.toFrame(blendshapes = null, headMatrix = FloatArray(16), timestampMs = 7)
        assertFalse(frame.faceDetected)
        assertTrue(frame.blendshapes.isEmpty())
        assertNull(frame.headMatrixColumnMajor)
        assertEquals(7, frame.timestampMs)
    }

    @Test
    fun blendshapes_meanAFace_withAnUnrotatedMatrixPassedThroughUnchanged() {
        val matrix = RotationMath.rotationMatrix(HeadPose(10f, 5f, 3f))
        val frame = MediaPipeFaceTracker.toFrame(mapOf("jawOpen" to 0.4f), matrix, timestampMs = 9, rotationDegrees = 0)
        assertTrue(frame.faceDetected)
        assertEquals(0.4f, frame.blendshapes["jawOpen"])
        assertTrue(matrix contentEquals frame.headMatrixColumnMajor!!)
    }

    @Test
    fun sensorRotation_isRemovedFromTheHeadMatrix() {
        // The back lens reports a 90°-rotated sensor frame: a true roll of 3° arrives as 93°.
        val reported = RotationMath.rotationMatrix(HeadPose(0f, 0f, 93f))
        val frame = MediaPipeFaceTracker.toFrame(
            mapOf("jawOpen" to 0f),
            reported,
            timestampMs = 9,
            rotationDegrees = 90,
        )
        val pose = RotationMath.toHeadPose(frame.headMatrixColumnMajor!!)
        assertEquals(3f, pose.roll, 0.01f)
        assertEquals(0f, pose.yaw, 0.01f)
    }

    @Test
    fun faceWithoutAMatrix_isStillAFace() {
        val frame = MediaPipeFaceTracker.toFrame(mapOf("jawOpen" to 0.4f), headMatrix = null, timestampMs = 1)
        assertTrue(frame.faceDetected)
        assertNull(frame.headMatrixColumnMajor) // the rig keeps the head straight
    }
}
