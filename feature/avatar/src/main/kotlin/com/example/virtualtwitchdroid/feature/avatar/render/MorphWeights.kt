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
}
