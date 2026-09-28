package com.example.virtualtwitchdroid.feature.avatar.render

import com.example.virtualtwitchdroid.feature.avatar.rig.FaceRig
import com.example.virtualtwitchdroid.feature.avatar.rig.LookAtType
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import com.example.virtualtwitchdroid.feature.avatar.vrm.VrmModel

/**
 * Turns a [FaceRig] into per-node morph-target weight arrays using the model's expression binds:
 * `weights[node][bind.index] += rigWeight * bind.weight`, clamped to `0..1`. Nodes without a driven
 * target are omitted. On a model whose gaze is **bone**-driven (`lookAt.type = bone`) the `look*` presets
 * are skipped even if the file also carries look morphs — VRM attaches one gaze applier, never both.
 * Pure, so the binding math is unit-tested without Filament.
 */
object MorphWeights {
    private val gaze =
        setOf(VrmExpression.LOOK_UP, VrmExpression.LOOK_DOWN, VrmExpression.LOOK_LEFT, VrmExpression.LOOK_RIGHT)

    fun compose(rig: FaceRig, model: VrmModel): Map<Int, FloatArray> {
        val boneGaze = model.lookAt?.type == LookAtType.BONE
        val out = HashMap<Int, FloatArray>()
        for ((expression, binds) in model.expressions) {
            if (boneGaze && expression in gaze) continue
            val w = rig[expression]
            if (w <= 0f) continue
            for (bind in binds) {
                val count = model.morphTargetCounts[bind.node] ?: continue
                if (bind.index !in 0 until count) continue
                val arr = out.getOrPut(bind.node) { FloatArray(count) }
                arr[bind.index] = (arr[bind.index] + w * bind.weight).coerceIn(0f, 1f)
            }
        }
        return out
    }

    /**
     * Like [compose] but fills caller-owned buffers in [out] in place — no per-frame allocation, for the
     * render loop. Every buffer in [out] is zeroed first, so a node not driven this frame ends up all
     * zeros (e.g. a released blink re-opens) — matching `compose(rig, model)[node] ?: zeros`. [out] must
     * hold a correctly-sized buffer for every node that carries morph weights this frame (a bind whose
     * node is absent from [out] is skipped, exactly as [compose] skips a node without a morph-target
     * count). The result for every present node is identical to the [compose]+reset path.
     */
    fun composeInto(rig: FaceRig, model: VrmModel, out: Map<Int, FloatArray>) {
        for (arr in out.values) arr.fill(0f)
        val boneGaze = model.lookAt?.type == LookAtType.BONE
        for ((expression, binds) in model.expressions) {
            if (boneGaze && expression in gaze) continue
            val w = rig[expression]
            if (w <= 0f) continue
            for (bind in binds) {
                val arr = out[bind.node] ?: continue
                if (bind.index !in arr.indices) continue
                arr[bind.index] = (arr[bind.index] + w * bind.weight).coerceIn(0f, 1f)
            }
        }
    }
}
