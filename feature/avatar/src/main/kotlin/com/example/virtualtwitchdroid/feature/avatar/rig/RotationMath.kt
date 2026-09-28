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
    fun rotationMatrix(pose: HeadPose): FloatArray = rotationMatrix(pose, FloatArray(16))

    /**
     * As [rotationMatrix], but writes into the caller's [out] and returns it — no allocation, for the
     * per-frame render loop. ALL 16 elements are written (the six not part of the rotation block are set
     * to their identity values), so a reused scratch buffer never carries stale values from a prior call.
     */
    fun rotationMatrix(pose: HeadPose, out: FloatArray): FloatArray {
        require(out.size == 16) { "expected a 4x4 column-major matrix (16 floats), got ${out.size}" }
        val cy = cos(pose.yaw * DEG_TO_RAD)
        val sy = sin(pose.yaw * DEG_TO_RAD)
        val cp = cos(pose.pitch * DEG_TO_RAD)
        val sp = sin(pose.pitch * DEG_TO_RAD)
        val cr = cos(pose.roll * DEG_TO_RAD)
        val sr = sin(pose.roll * DEG_TO_RAD)
        // R = Rz · Ry · Rx, written out per element (row, col).
        out[0] = cr * cy // r00
        out[1] = sr * cy // r10
        out[2] = -sy // r20
        out[3] = 0f // column 0
        out[4] = cr * sy * sp - sr * cp // r01
        out[5] = sr * sy * sp + cr * cp // r11
        out[6] = cy * sp // r21
        out[7] = 0f // column 1
        out[8] = cr * sy * cp + sr * sp // r02
        out[9] = sr * sy * cp - cr * sp // r12
        out[10] = cy * cp // r22
        out[11] = 0f // column 2
        out[12] = 0f
        out[13] = 0f
        out[14] = 0f
        out[15] = 1f // column 3 (no translation)
        return out
    }

    /** Column-major 4×4 product `a · b` (apply [b] first, then [a]). */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray = multiply(a, b, FloatArray(16))

    /**
     * As [multiply], but writes into the caller's [out] and returns it — no allocation, for the per-frame
     * render loop. [out] must not alias [a] or [b] (the product is accumulated while still reading both).
     */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray): FloatArray {
        require(a.size == 16 && b.size == 16 && out.size == 16) { "expected three 4x4 column-major matrices" }
        require(out !== a && out !== b) { "out must not alias a or b" }
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
