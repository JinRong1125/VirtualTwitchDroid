package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ArmSide { LEFT, RIGHT }

/** The VRM humanoid arm bones the idle pose touches (hands and fingers are left at rest). */
enum class ArmBone(val vrmName: String, val side: ArmSide, val childVrmName: String) {
    LEFT_SHOULDER("leftShoulder", ArmSide.LEFT, "leftUpperArm"),
    LEFT_UPPER_ARM("leftUpperArm", ArmSide.LEFT, "leftLowerArm"),
    LEFT_LOWER_ARM("leftLowerArm", ArmSide.LEFT, "leftHand"),
    RIGHT_SHOULDER("rightShoulder", ArmSide.RIGHT, "rightUpperArm"),
    RIGHT_UPPER_ARM("rightUpperArm", ArmSide.RIGHT, "rightLowerArm"),
    RIGHT_LOWER_ARM("rightLowerArm", ArmSide.RIGHT, "rightHand"),
}

/**
 * The relaxed **A-pose** the arms are put into at load: VRM files store the humanoid in a T-pose (arms
 * straight out), which no streamer holds. Targets are **world-space unit directions** for each bone (from
 * its joint toward its child), so they apply to any rig whatever its rest rotations or facing — the renderer
 * aims each bone at them with the same math the spring bones use. `outward` is +1 when the arm's own side
 * points along world +X, −1 otherwise (measured from the rig, so left/right conventions never matter).
 */
object ArmRestPose {
    /** Upper arm: hanging down, [UPPER_ARM_OUT_DEG] away from the body. */
    fun upperArmDirection(outward: Float): FloatArray = direction(outward, UPPER_ARM_OUT_DEG, forwardDeg = 0f)

    /** Lower arm: down and slightly forward — the elbow bends a little instead of locking straight. */
    fun lowerArmDirection(outward: Float): FloatArray = direction(outward, LOWER_ARM_OUT_DEG, ELBOW_FORWARD_DEG)

    private fun direction(outward: Float, outDeg: Float, forwardDeg: Float): FloatArray {
        val out = outDeg * DEG_TO_RAD
        val fwd = forwardDeg * DEG_TO_RAD
        // Start from straight down (0, −1, 0), tilt outward about Z, then forward (+Z) about X.
        val x = outward * sin(out) * cos(fwd)
        val y = -cos(out) * cos(fwd)
        val z = sin(fwd)
        return floatArrayOf(x, y, z)
    }

    const val UPPER_ARM_OUT_DEG = 12f
    const val LOWER_ARM_OUT_DEG = 8f
    const val ELBOW_FORWARD_DEG = 15f
    private const val DEG_TO_RAD = (PI / 180.0).toFloat()
}

/**
 * Procedural idle motion, a pure function of time, layered on top of tracking so the avatar never stands
 * dead still: slow **breathing** (chest pitches, shoulders rise and fall) and a slower **arm sway**. All
 * components are zero-mean sinusoids with small amplitudes (degrees), like VMagicMirror / Animaze idle layers.
 */
object IdleMotion {
    data class Offsets(
        /** 0 = exhaled, 1 = inhaled. */
        val breath: Float,
        /** Extra chest pitch (degrees; negative = chest lifts/opens on the inhale). */
        val chestPitchDeg: Float,
        /** Shoulder lift (degrees, positive = up) — the same for both sides. */
        val shoulderLiftDeg: Float,
        /** Upper-arm swing away from the body (degrees, positive = outward), the same for both sides. */
        val armSwayDeg: Float,
    )

    fun at(timeSeconds: Float): Offsets {
        val breath = 0.5f - 0.5f * cos(TWO_PI * timeSeconds / BREATH_PERIOD_S)
        val centred = (breath - 0.5f) * 2f // −1..1, zero-mean
        return Offsets(
            breath = breath,
            chestPitchDeg = -BREATH_CHEST_PITCH_DEG * centred,
            shoulderLiftDeg = BREATH_SHOULDER_LIFT_DEG * centred,
            armSwayDeg = ARM_SWAY_DEG * sin(TWO_PI * timeSeconds / ARM_SWAY_PERIOD_S),
        )
    }

    const val BREATH_PERIOD_S = 4f
    const val BREATH_CHEST_PITCH_DEG = 0.8f
    const val BREATH_SHOULDER_LIFT_DEG = 1.5f
    const val ARM_SWAY_PERIOD_S = 7f
    const val ARM_SWAY_DEG = 1.2f

    /** How much a head tilt lifts the shoulder on that side (degrees per degree of roll). */
    const val SHOULDER_PER_HEAD_ROLL = 0.3f

    private const val TWO_PI = (2.0 * PI).toFloat()
}
