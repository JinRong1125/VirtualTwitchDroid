package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal rotation-matrix math for the head pose, on 4×4 **column-major** matrices
 * (`m[col * 4 + row]`, the layout MediaPipe emits and glTF/Filament consume). Pure functions so the
 * decomposition is unit-testable by round-tripping through [rotationMatrix].
 */
object RotationMath {
    private const val DEG_TO_RAD = (PI / 180.0).toFloat()
    private const val RAD_TO_DEG = (180.0 / PI).toFloat()

    /** Element at [row], [col] of a column-major 4×4. */
    fun at(m: FloatArray, row: Int, col: Int): Float = m[col * 4 + row]

    /**
     * The column-major 4×4 rotation `R = Rz(roll) · Ry(yaw) · Rx(pitch)` for a [HeadPose] (degrees),
     * with no translation. Inverse of [toHeadPose].
     */
    fun rotationMatrix(pose: HeadPose): FloatArray {
        val cy = cos(pose.yaw * DEG_TO_RAD)
        val sy = sin(pose.yaw * DEG_TO_RAD)
        val cp = cos(pose.pitch * DEG_TO_RAD)
        val sp = sin(pose.pitch * DEG_TO_RAD)
        val cr = cos(pose.roll * DEG_TO_RAD)
        val sr = sin(pose.roll * DEG_TO_RAD)
        // R = Rz · Ry · Rx, written out per element (row, col).
        val r00 = cr * cy
        val r01 = cr * sy * sp - sr * cp
        val r02 = cr * sy * cp + sr * sp
        val r10 = sr * cy
        val r11 = sr * sy * sp + cr * cp
        val r12 = sr * sy * cp - cr * sp
        val r20 = -sy
        val r21 = cy * sp
        val r22 = cy * cp
        val m = FloatArray(16)
        m[0] = r00
        m[1] = r10
        m[2] = r20 // column 0
        m[4] = r01
        m[5] = r11
        m[6] = r21 // column 1
        m[8] = r02
        m[9] = r12
        m[10] = r22 // column 2
        m[15] = 1f
        return m
    }

    /** Column-major 4×4 product `a · b` (apply [b] first, then [a]). */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == 16 && b.size == 16) { "expected two 4x4 column-major matrices" }
        val out = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
                out[col * 4 + row] = sum
            }
        }
        return out
    }

    /**
     * Undoes an image rotation that leaked into a face transform: when the tracker is handed the raw
     * sensor image plus "rotate by [degrees] to make it upright", it detects on the rotated image but
     * reports the head matrix in the **un-rotated** sensor frame, so the whole pose carries an extra
     * spin about the view (Z) axis. Pre-multiplying by `Rz(−degrees)` spins the camera frame back:
     * `R_true = Rz(−θ) · R_reported`. Returns a new column-major 4×4; translation is carried along.
     */
    fun unrotateAboutZ(m: FloatArray, degrees: Float): FloatArray {
        require(m.size == 16) { "expected a 4x4 column-major matrix (16 floats), got ${m.size}" }
        if (degrees == 0f) return m.copyOf()
        val c = cos(-degrees * DEG_TO_RAD)
        val s = sin(-degrees * DEG_TO_RAD)
        val out = m.copyOf()
        // Rz(φ) · M only mixes rows 0 and 1 of each column: x' = c·x − s·y, y' = s·x + c·y.
        for (col in 0 until 4) {
            val x = m[col * 4]
            val y = m[col * 4 + 1]
            out[col * 4] = c * x - s * y
            out[col * 4 + 1] = s * x + c * y
        }
        return out
    }

    /**
     * Decomposes the rotation part of a column-major 4×4 into a [HeadPose] (degrees), assuming the
     * `Rz(roll) · Ry(yaw) · Rx(pitch)` order. Any translation/scale in the matrix is ignored; the
     * rotation block must be orthonormal (MediaPipe's is). The singularity of this order is at
     * |yaw| → 90° (the middle axis): there pitch and roll become indistinguishable and `atan2(≈0, ≈0)`
     * is noise — irrelevant for a face the tracker can still see (it loses the face long before 90°).
     */
    fun toHeadPose(m: FloatArray): HeadPose {
        require(m.size == 16) { "expected a 4x4 column-major matrix (16 floats), got ${m.size}" }
        val r00 = at(m, 0, 0)
        val r10 = at(m, 1, 0)
        val r20 = at(m, 2, 0)
        val r21 = at(m, 2, 1)
        val r22 = at(m, 2, 2)
        val yaw = atan2(-r20, sqrt(r21 * r21 + r22 * r22))
        val pitch = atan2(r21, r22)
        val roll = atan2(r10, r00)
        return HeadPose(yaw * RAD_TO_DEG, pitch * RAD_TO_DEG, roll * RAD_TO_DEG)
    }
}
