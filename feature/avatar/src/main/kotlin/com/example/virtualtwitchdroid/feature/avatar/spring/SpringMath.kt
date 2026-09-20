package com.example.virtualtwitchdroid.feature.avatar.spring

import com.example.virtualtwitchdroid.feature.avatar.rig.RotationMath
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The little vector / affine algebra the spring simulation needs, on `FloatArray(3)` vectors and 4×4
 * **column-major** matrices (`m[col * 4 + row]`, Filament's layout). Pure; every operation returns a
 * fresh array (the simulator is small enough that this is ~40 short-lived arrays per segment per frame).
 */
internal object SpringMath {
    /** A fresh identity matrix (never share one: callers may write into it). */
    fun identity(): FloatArray = FloatArray(16).also {
        it[0] = 1f
        it[5] = 1f
        it[10] = 1f
        it[15] = 1f
    }

    fun vec(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

    fun add(a: FloatArray, b: FloatArray) = vec(a[0] + b[0], a[1] + b[1], a[2] + b[2])

    fun sub(a: FloatArray, b: FloatArray) = vec(a[0] - b[0], a[1] - b[1], a[2] - b[2])

    fun scale(a: FloatArray, s: Float) = vec(a[0] * s, a[1] * s, a[2] * s)

    fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    fun cross(a: FloatArray, b: FloatArray) = vec(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    fun length(a: FloatArray) = sqrt(dot(a, a))

    /** Unit vector, or [fallback] (a copy; default +Y) for a (near-)zero input. */
    fun normalize(a: FloatArray, fallback: FloatArray? = null): FloatArray {
        val len = length(a)
        return if (len < EPSILON) fallback?.copyOf() ?: vec(0f, 1f, 0f) else scale(a, 1f / len)
    }

    fun translation(m: FloatArray) = vec(m[12], m[13], m[14])

    /** `m · (p, 1)` — a point through the full affine transform. */
    fun transformPoint(m: FloatArray, p: FloatArray) = vec(
        m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12],
        m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13],
        m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14],
    )

    /** `m · (d, 0)` — a direction through the linear part (rotation and scale, no translation). */
    fun transformDir(m: FloatArray, d: FloatArray) = vec(
        m[0] * d[0] + m[4] * d[1] + m[8] * d[2],
        m[1] * d[0] + m[5] * d[1] + m[9] * d[2],
        m[2] * d[0] + m[6] * d[1] + m[10] * d[2],
    )

    /** The lengths of the three basis columns (the per-axis scale of an affine matrix). */
    fun scaleOf(m: FloatArray) = vec(
        length(vec(m[0], m[1], m[2])),
        length(vec(m[4], m[5], m[6])),
        length(vec(m[8], m[9], m[10])),
    )

    /** The pure rotation of an affine matrix: basis columns normalized, no translation. */
    fun rotationOnly(m: FloatArray): FloatArray {
        val out = identity()
        for (col in 0 until 3) {
            val c = normalize(vec(m[col * 4], m[col * 4 + 1], m[col * 4 + 2]), fallback = axis(col))
            out[col * 4] = c[0]
            out[col * 4 + 1] = c[1]
            out[col * 4 + 2] = c[2]
        }
        return out
    }

    /** Inverse of a pure rotation (its transpose). */
    fun transposeRotation(r: FloatArray): FloatArray {
        val out = identity()
        for (col in 0 until 3) for (row in 0 until 3) out[col * 4 + row] = r[row * 4 + col]
        return out
    }

    /** Column-major 4×4 product `a · b` (apply [b] first). */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray = RotationMath.multiply(a, b)

    /** `T(translation) · R(rotation) · S(scale)` — a node's local transform from its parts. */
    fun compose(translation: FloatArray, rotation: FloatArray, scale: FloatArray): FloatArray {
        val out = rotation.copyOf()
        for (col in 0 until 3) for (row in 0 until 3) out[col * 4 + row] *= scale[col]
        out[12] = translation[0]
        out[13] = translation[1]
        out[14] = translation[2]
        out[15] = 1f
        return out
    }

    /** Inverse of a general affine matrix (3×3 linear part by adjugate + translation); identity if singular. */
    fun affineInverse(m: FloatArray): FloatArray {
        val a = m[0]
        val b = m[4]
        val c = m[8]
        val d = m[1]
        val e = m[5]
        val f = m[9]
        val g = m[2]
        val h = m[6]
        val i = m[10]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (abs(det) < SINGULAR_DETERMINANT) return identity()
        val inv = 1f / det
        val out = identity()
        // row-major inverse entries, stored column-major
        out[0] = (e * i - f * h) * inv
        out[4] = (c * h - b * i) * inv
        out[8] = (b * f - c * e) * inv
        out[1] = (f * g - d * i) * inv
        out[5] = (a * i - c * g) * inv
        out[9] = (c * d - a * f) * inv
        out[2] = (d * h - e * g) * inv
        out[6] = (b * g - a * h) * inv
        out[10] = (a * e - b * d) * inv
        val t = transformDir(out, translation(m))
        out[12] = -t[0]
        out[13] = -t[1]
        out[14] = -t[2]
        return out
    }

    /** The rotation taking unit vector [from] onto unit vector [to] (Rodrigues); identity when equal. */
    fun fromToRotation(from: FloatArray, to: FloatArray): FloatArray {
        val v = cross(from, to)
        val c = dot(from, to).coerceIn(-1f, 1f)
        if (c > 1f - EPSILON) return identity()
        if (c < -1f + EPSILON) {
            // Opposite directions: 180° about any axis perpendicular to `from` (pick the world axis it is
            // least aligned with to build one).
            val helper = if (abs(from[0]) < HELPER_AXIS_ALIGNMENT) vec(1f, 0f, 0f) else vec(0f, 1f, 0f)
            return rotationAboutAxis(normalize(cross(from, helper)), cos = -1f, sin = 0f)
        }
        val k = 1f / (1f + c)
        val out = identity()
        // R = I + [v]× + [v]×² · k, written per (row, col).
        out[0] = 1f + (-v[2] * v[2] - v[1] * v[1]) * k
        out[4] = -v[2] + v[0] * v[1] * k
        out[8] = v[1] + v[0] * v[2] * k
        out[1] = v[2] + v[0] * v[1] * k
        out[5] = 1f + (-v[2] * v[2] - v[0] * v[0]) * k
        out[9] = -v[0] + v[1] * v[2] * k
        out[2] = -v[1] + v[0] * v[2] * k
        out[6] = v[0] + v[1] * v[2] * k
        out[10] = 1f + (-v[1] * v[1] - v[0] * v[0]) * k
        return out
    }

    /** Rotation about unit [axis] with the given cosine / sine (Rodrigues' formula). */
    private fun rotationAboutAxis(axis: FloatArray, cos: Float, sin: Float): FloatArray {
        val (x, y, z) = axis
        val t = 1f - cos
        val out = identity()
        out[0] = cos + x * x * t
        out[4] = x * y * t - z * sin
        out[8] = x * z * t + y * sin
        out[1] = y * x * t + z * sin
        out[5] = cos + y * y * t
        out[9] = y * z * t - x * sin
        out[2] = z * x * t - y * sin
        out[6] = z * y * t + x * sin
        out[10] = cos + z * z * t
        return out
    }

    private fun axis(i: Int) = when (i) {
        0 -> vec(1f, 0f, 0f)
        1 -> vec(0f, 1f, 0f)
        else -> vec(0f, 0f, 1f)
    }

    /** Below this a vector counts as zero / two unit vectors as (anti)parallel. */
    private const val EPSILON = 1e-6f

    /** A 3×3 determinant below this is treated as singular (identity returned). */
    private const val SINGULAR_DETERMINANT = 1e-12f

    /** |component| under which a unit vector is "not aligned" with that world axis (helper-axis choice). */
    private const val HELPER_AXIS_ALIGNMENT = 0.9f
}
