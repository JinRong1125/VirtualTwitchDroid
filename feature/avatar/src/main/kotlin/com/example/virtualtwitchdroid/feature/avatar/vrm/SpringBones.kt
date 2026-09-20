package com.example.virtualtwitchdroid.feature.avatar.vrm

/**
 * A `VRMC_springBone` collider: a sphere at [offset] (node-local, metres) of [radius], or — when [tail]
 * is set — a capsule between [offset] and [tail]. Hair/cloth joints are kept outside it.
 */
@Suppress("ArrayInDataClass") // small fixed vectors; identity semantics are fine
data class SpringCollider(val node: Int, val offset: FloatArray, val radius: Float, val tail: FloatArray? = null)

/**
 * One joint of a spring chain (`VRMC_springBone` semantics, VRM 0.x mapped onto them):
 * - [stiffness] pulls the tail back toward the bone's rest direction — in metres of tail travel per second
 *   along that unit direction (`restDir · stiffness · dt`), before the length constraint;
 * - [gravityPower] along [gravityDir] (unit vector) pulls it (per second);
 * - [dragForce] `0..1` removes that fraction of the previous frame's velocity (1 = no inertia);
 * - [hitRadius] is the joint's own collision sphere (metres).
 */
@Suppress("ArrayInDataClass")
data class SpringJoint(
    val node: Int,
    val hitRadius: Float = 0f,
    val stiffness: Float = 1f,
    val gravityPower: Float = 0f,
    val gravityDir: FloatArray = floatArrayOf(0f, -1f, 0f),
    val dragForce: Float = 0.5f,
)

/**
 * A chain of [joints] from root to tip (each a child of the previous), simulated against [colliders]
 * (indices into [SpringBoneSet.colliders]). With a [center] node the simulation runs in that node's
 * space, so moving the whole model does not swing the chain — only motion relative to the center does.
 *
 * In VRM 1.0 the last joint is only the **tip**: it marks where the previous segment ends and is not
 * simulated itself. VRM 0.x (UniVRM's `VRMSpringBone`) simulates every bone including the leaf, giving
 * it a **virtual tail** [VIRTUAL_TAIL_M] beyond it along its parent→leaf direction — [leafTail] asks
 * the simulator for that extra segment (so a single-bone chain such as an ear still animates).
 */
data class SpringChain(
    val name: String?,
    val joints: List<SpringJoint>,
    val colliders: List<Int>,
    val center: Int?,
    val leafTail: Boolean = false,
) {
    companion object {
        /** UniVRM 0.x: `childPosition = parent.position + delta.normalized * 0.07f` for a leaf bone. */
        const val VIRTUAL_TAIL_M = 0.07f
    }
}

/** Everything the spring-bone simulation needs from the VRM file. */
data class SpringBoneSet(val colliders: List<SpringCollider>, val springs: List<SpringChain>) {
    /** Every glTF node the simulation touches (joints, colliders, centers). */
    val nodes: Set<Int>
        get() = buildSet {
            springs.forEach { s ->
                s.joints.forEach { add(it.node) }
                s.center?.let { add(it) }
            }
            colliders.forEach { add(it.node) }
        }
}
