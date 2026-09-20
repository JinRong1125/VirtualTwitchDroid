package com.example.virtualtwitchdroid.feature.avatar.rig

import kotlin.math.exp

/**
 * The VRM humanoid bones the face-driven upper body animates, root-most first. `hips` and the arms are
 * left alone: face-only tracking carries no body signal, so this is torso follow-through, not pose.
 */
enum class UpperBodyBone(val vrmName: String) {
    SPINE("spine"),
    CHEST("chest"),
    UPPER_CHEST("upperChest"),
    NECK("neck"),
    HEAD("head"),
}

/** Per-bone local rotations for one frame (bones a model lacks are simply not applied). */
data class UpperBodyPose(val rotations: Map<UpperBodyBone, HeadPose>) {
    operator fun get(bone: UpperBodyBone): HeadPose = rotations[bone] ?: HeadPose.IDENTITY

    companion object {
        val IDENTITY = UpperBodyPose(emptyMap())
    }
}

/**
 * Head-driven **upper-body follow-through**: the tracked head rotation is distributed down the spine
 * chain so the chest and shoulders (children of the chest) turn and lean with the face — the standard
 * VTuber approximation when only the face is tracked. Each torso bone takes a fixed fraction of a
 * **lagged** copy of the head pose ([UpperBodyFollow]), plus its share of the body [BodyLean] (from the
 * head's displacement), and the head bone takes whatever is left of the *tracked* pose, so the face lands
 * on the tracked orientation while the body catches up and leans underneath it.
 *
 * The split is additive in Euler angles, which is exact only for small angles: at typical streaming
 * poses (≲ 30°) the rendered face is within a degree or two of the tracked one; at the 60° clamp with a
 * strong roll the error grows to ~15–20° — accepted for follow-through (the tracker loses the face
 * before that anyway).
 *
 * Fractions are per axis (yaw, pitch, roll): the torso turns readily (yaw), leans a little (roll) and
 * nods least (pitch). A bone the model lacks (or that the renderer could not resolve) passes its share to
 * its nearest **present ancestor** (e.g. `upperChest` → `chest`); with no ancestor the head keeps it (a
 * lean share with no torso bone is dropped — the head alone must not lean). The head remainder is
 * therefore always computed over the bones that are actually driven.
 */
object UpperBodyRig {
    /** Fraction of the head pose taken by each torso bone, as (yaw, pitch, roll); root-most first. */
    private val shares: List<Pair<UpperBodyBone, HeadPose>> = listOf(
        UpperBodyBone.SPINE to HeadPose(yaw = 0.08f, pitch = 0.05f, roll = 0.08f),
        UpperBodyBone.CHEST to HeadPose(yaw = 0.12f, pitch = 0.08f, roll = 0.12f),
        UpperBodyBone.UPPER_CHEST to HeadPose(yaw = 0.10f, pitch = 0.07f, roll = 0.10f),
        UpperBodyBone.NECK to HeadPose(yaw = 0.25f, pitch = 0.25f, roll = 0.20f),
    )

    /** How the body lean is split over the torso (sums to 1; the neck does not lean). */
    private val leanShares: List<Pair<UpperBodyBone, HeadPose>> = listOf(
        UpperBodyBone.SPINE to HeadPose(0.5f, 0.5f, 0.5f),
        UpperBodyBone.CHEST to HeadPose(0.3f, 0.3f, 0.3f),
        UpperBodyBone.UPPER_CHEST to HeadPose(0.2f, 0.2f, 0.2f),
    )

    /** The torso's total share per axis — the head keeps the rest (yaw 0.45, pitch 0.55, roll 0.50). */
    val torsoShare: HeadPose = shares.fold(HeadPose.IDENTITY) { acc, (_, s) -> acc + s }

    init {
        // The head must keep a positive share on every axis, or it would counter-rotate against the body.
        check(
            torsoShare.yaw < MAX_TORSO_SHARE && torsoShare.pitch < MAX_TORSO_SHARE && torsoShare.roll < MAX_TORSO_SHARE,
        ) {
            "torso share must stay below $MAX_TORSO_SHARE: $torsoShare"
        }
    }

    /**
     * @param tracked the current tracked head pose (what the face must end up at).
     * @param followed the lagged head pose the torso follows (see [UpperBodyFollow]); pass [tracked] for no lag.
     * @param present the bones the model actually has (and the renderer resolved); the head is always driven.
     * @param lean the body lean from the head's displacement ([BodyLeanMapper]); the torso takes it, the head
     *   counter-rotates so the face stays on [tracked].
     */
    fun distribute(
        tracked: HeadPose,
        followed: HeadPose = tracked,
        present: Set<UpperBodyBone> = UpperBodyBone.entries.toSet(),
        lean: HeadPose = HeadPose.IDENTITY,
    ): UpperBodyPose {
        val torso = LinkedHashMap<UpperBodyBone, HeadPose>()
        for ((bone, share) in allocate(shares, present)) torso[bone] = followed * share
        for ((bone, share) in allocate(leanShares, present)) torso[bone] = torso.getValue(bone) + lean * share
        val torsoTotal = torso.values.fold(HeadPose.IDENTITY) { acc, p -> acc + p }
        return UpperBodyPose(torso + (UpperBodyBone.HEAD to tracked - torsoTotal))
    }

    /** [table] restricted to [present] bones, a missing bone's share folded into its nearest present ancestor. */
    private fun allocate(
        table: List<Pair<UpperBodyBone, HeadPose>>,
        present: Set<UpperBodyBone>,
    ): Map<UpperBodyBone, HeadPose> {
        val effective = LinkedHashMap<UpperBodyBone, HeadPose>()
        var lastPresent: UpperBodyBone? = null
        for ((bone, share) in table) {
            if (bone in present) {
                effective[bone] = share
                lastPresent = bone
            } else {
                val heir = lastPresent ?: continue // no ancestor to inherit
                effective[heir] = effective.getValue(heir) + share
            }
        }
        return effective
    }

    private const val MAX_TORSO_SHARE = 1f

    private operator fun HeadPose.plus(o: HeadPose) = HeadPose(yaw + o.yaw, pitch + o.pitch, roll + o.roll)
    private operator fun HeadPose.minus(o: HeadPose) = HeadPose(yaw - o.yaw, pitch - o.pitch, roll - o.roll)
    private operator fun HeadPose.times(share: HeadPose) = HeadPose(
        yaw * share.yaw,
        pitch * share.pitch,
        roll * share.roll,
    )
}

/**
 * A time-constant exponential lag on a small vector (`out = prev + a · (in − prev)`, `a = 1 − e^(−dt/τ)`):
 * the same lag at 120 Hz and at 30 fps. Stateful — one per animated quantity.
 *
 * @param timeConstantNanos τ: after this much time ~63 % of a step has been covered.
 * @param size number of components.
 */
class TimeLag(private val timeConstantNanos: Long, private val size: Int) {
    init {
        require(timeConstantNanos > 0L) { "time constant must be positive, got $timeConstantNanos" }
    }

    private var previous: FloatArray? = null
    private var previousNanos = 0L

    /** The lagged value at [nowNanos]; the very first sample passes through unchanged. */
    fun next(values: FloatArray, nowNanos: Long): FloatArray {
        require(values.size == size) { "expected $size components, got ${values.size}" }
        val prev = previous
        if (prev == null) {
            previous = values.copyOf()
            previousNanos = nowNanos
            return values
        }
        val dt = (nowNanos - previousNanos).coerceAtLeast(0L)
        previousNanos = nowNanos
        val alpha = (1.0 - exp(-dt.toDouble() / timeConstantNanos)).toFloat()
        val out = FloatArray(size) { i -> prev[i] + alpha * (values[i] - prev[i]) }
        previous = out
        return out
    }

    fun reset() {
        previous = null
    }
}

/**
 * The torso's lag behind the head (a [TimeLag] on a [HeadPose]), so the body visibly follows a beat
 * later instead of moving as one rigid block with the face — ~150 ms reads as natural follow-through.
 */
class UpperBodyFollow(timeConstantNanos: Long) {
    private val lag = TimeLag(timeConstantNanos, size = 3)

    /** The lagged pose at [nowNanos]; the very first sample passes through unchanged. */
    fun next(head: HeadPose, nowNanos: Long): HeadPose =
        lag.next(floatArrayOf(head.yaw, head.pitch, head.roll), nowNanos).let { HeadPose(it[0], it[1], it[2]) }

    fun reset() = lag.reset()
}
