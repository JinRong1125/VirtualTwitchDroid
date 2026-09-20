package com.example.virtualtwitchdroid.feature.avatar.spring

import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.add
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.affineInverse
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.compose
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.fromToRotation
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.length
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.multiply
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.normalize
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.rotationOnly
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.scale
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.scaleOf
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.sub
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.transformDir
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.transformPoint
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.translation
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.transposeRotation
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringBoneSet
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringChain
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringCollider
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringJoint
import kotlin.math.max

/**
 * The scene's bone transforms as the simulation sees them — implemented over Filament's
 * `TransformManager` in the renderer and over a tiny tree in tests. Matrices are column-major 4×4.
 * **Ownership:** an array returned by [world] / [parentWorld] may be a scratch buffer that the next call
 * of the same method overwrites — copy it if it must survive another query.
 */
interface BoneTransforms {
    /** The node's current world transform, or null if the node is not in the scene. */
    fun world(node: Int): FloatArray?

    /** The current world transform of the node's parent (identity for a root), or null if unknown. */
    fun parentWorld(node: Int): FloatArray?

    /** The node's rest (bind-pose) local transform. */
    fun restLocal(node: Int): FloatArray?

    fun setLocal(node: Int, local: FloatArray)
}

/**
 * `VRMC_springBone` secondary motion (UniVRM's Verlet formulation): every chain segment keeps a **tail**
 * point that carries inertia, is pulled back toward the bone's rest direction by `stiffness`, pulled by
 * gravity, damped by `dragForce`, kept at the bone's length and outside the colliders — and the bone is then
 * rotated (about its head) to point at the tail. Run once per rendered frame *after* the driven bones (torso,
 * head) have been posed, root-most joint first, so each segment sees its parent's new world transform.
 *
 * With a chain `center`, tails live in the center node's space, so moving the whole model does not swing the
 * hair — only motion relative to the center does (the spec's intent). Segments whose rest length is ~0 are
 * skipped; chains with nodes missing from the scene are dropped at construction (logged by the caller).
 */
class SpringBoneSimulator(set: SpringBoneSet, transforms: BoneTransforms) {

    private class Segment(
        val joint: SpringJoint,
        /** Unit direction from this joint to its child in the joint's local rest frame. */
        val boneAxis: FloatArray,
        /** World-space length of the segment at rest. */
        val length: Float,
        val restLocal: FloatArray,
        /** Tail positions in simulation space (the center's space, or world). */
        var currentTail: FloatArray,
        var prevTail: FloatArray,
    )

    private class Chain(
        val name: String?,
        val segments: List<Segment>,
        val colliders: List<SpringCollider>,
        val center: Int?,
    )

    private val chains: List<Chain>

    /** Number of simulated segments (chains × (joints − 1), plus virtual-tail leaves, minus degenerate ones). */
    val segmentCount: Int

    /** Names of chains that could not be simulated (a node missing from the scene, or no segment with length). */
    val droppedChains: List<String?>

    /** Largest tail displacement (metres) seen in the last [step] — a cheap "is it moving" health metric. */
    var lastMaxTailTravel = 0f
        private set

    /** One line per chain: per-segment `|tail − head| − length` error (metres), for logging when numbers look wrong. */
    fun debugSnapshot(transforms: BoneTransforms): String = chains.joinToString(" | ") { chain ->
        val centerWorld = chain.center?.let { transforms.world(it) }?.copyOf()
        val errors = chain.segments.map { seg ->
            val current = centerWorld?.let { transformPoint(it, seg.currentTail) } ?: seg.currentTail
            val head = transforms.parentWorld(seg.joint.node)?.let { translation(multiply(it, seg.restLocal)) }
            head?.let { length(sub(current, it)) - seg.length }
        }
        "${chain.name}: lenErr=${errors.map { it?.let { e -> "%.3f".format(e) } }} " +
            "center=${centerWorld?.map { "%.2f".format(it) }}"
    }

    init {
        val dropped = ArrayList<String?>()
        chains = set.springs.mapNotNull { spring ->
            buildChain(spring, set, transforms) ?: run {
                dropped += spring.name
                null
            }
        }
        droppedChains = dropped
        segmentCount = chains.sumOf { it.segments.size }
    }

    private fun buildChain(spring: SpringChain, set: SpringBoneSet, transforms: BoneTransforms): Chain? {
        val centerInverse = spring.center?.let { transforms.world(it) }?.let(::affineInverse)
        val toSim: (FloatArray) -> FloatArray = { p -> centerInverse?.let { transformPoint(it, p) } ?: p }
        val segments = ArrayList<Segment>()
        for (i in 0 until spring.joints.size - 1) {
            val joint = spring.joints[i]
            val child = spring.joints[i + 1]
            val restLocal = transforms.restLocal(joint.node)?.copyOf() ?: return null
            val childRest = transforms.restLocal(child.node) ?: return null
            val jointPosition = transforms.world(joint.node)?.let(::translation) ?: return null // copy: scratch
            val childPosition = transforms.world(child.node)?.let(::translation) ?: return null
            val length = length(sub(childPosition, jointPosition))
            if (length < MIN_SEGMENT_LENGTH_M) continue // e.g. a zero-length tip helper
            val tail = toSim(childPosition)
            segments += Segment(
                joint = joint,
                boneAxis = normalize(translation(childRest)),
                length = length,
                restLocal = restLocal,
                currentTail = tail,
                prevTail = tail.copyOf(),
            )
        }
        if (spring.leafTail) {
            spring.joints.lastOrNull()?.let { leaf ->
                virtualTailSegment(leaf, transforms, toSim)?.let(segments::add)
            }
        }
        if (segments.isEmpty()) return null
        return Chain(spring.name, segments, spring.colliders.mapNotNull { set.colliders.getOrNull(it) }, spring.center)
    }

    /**
     * UniVRM 0.x simulates a leaf bone too, against a **virtual tail** [SpringChain.VIRTUAL_TAIL_M] beyond
     * the leaf along its parent→leaf direction (`parent.position + delta.normalized * 0.07`). The bone axis
     * is that world direction expressed in the leaf's own rest frame; a leaf sitting on its parent has no
     * direction and gets no segment.
     */
    private fun virtualTailSegment(
        leaf: SpringJoint,
        transforms: BoneTransforms,
        toSim: (FloatArray) -> FloatArray,
    ): Segment? {
        val restLocal = transforms.restLocal(leaf.node)?.copyOf() ?: return null
        val parentWorld = transforms.parentWorld(leaf.node)?.copyOf() ?: return null
        val jointWorld = multiply(parentWorld, restLocal)
        val head = translation(jointWorld)
        val delta = sub(head, translation(parentWorld))
        if (length(delta) < MIN_SEGMENT_LENGTH_M) return null
        val direction = normalize(delta)
        val tail = add(head, scale(direction, SpringChain.VIRTUAL_TAIL_M))
        val boneAxis = normalize(transformDir(affineInverse(jointWorld), direction))
        return Segment(
            joint = leaf,
            boneAxis = boneAxis,
            length = SpringChain.VIRTUAL_TAIL_M,
            restLocal = restLocal,
            currentTail = toSim(tail),
            prevTail = toSim(tail).copyOf(),
        )
    }

    /** Advance every chain by [dtSeconds] (clamped to [MAX_STEP_S]) and pose its joints. */
    fun step(dtSeconds: Float, transforms: BoneTransforms) {
        val dt = dtSeconds.coerceIn(0f, MAX_STEP_S)
        var maxTravel = 0f
        for (chain in chains) {
            val centerWorld = chain.center?.let { transforms.world(it) }?.copyOf() // survives the collider queries
            val centerInverse = centerWorld?.let(::affineInverse)
            for (seg in chain.segments) {
                val parentWorld = transforms.parentWorld(seg.joint.node) ?: continue
                // The joint's world pose at rest orientation: its position is fixed by the parent; only the
                // rotation is ours to set.
                val jointWorld = multiply(parentWorld, seg.restLocal)
                val head = translation(jointWorld)
                val restDir = normalize(transformDir(jointWorld, seg.boneAxis))

                val current = centerWorld?.let { transformPoint(it, seg.currentTail) } ?: seg.currentTail
                val prev = centerWorld?.let { transformPoint(it, seg.prevTail) } ?: seg.prevTail
                val j = seg.joint
                var next = current
                next = add(next, scale(sub(current, prev), 1f - j.dragForce)) // inertia
                next = add(next, scale(restDir, j.stiffness * dt)) // spring back to rest
                next = add(next, scale(j.gravityDir, j.gravityPower * dt)) // gravity
                next = constrainLength(head, next, seg.length)
                for (collider in chain.colliders) {
                    val colliderWorld = transforms.world(collider.node) ?: continue
                    next = collide(head, next, seg.length, j.hitRadius, collider, colliderWorld)
                }
                maxTravel = max(maxTravel, length(sub(next, current)))

                seg.prevTail = centerInverse?.let { transformPoint(it, current) } ?: current
                seg.currentTail = centerInverse?.let { transformPoint(it, next) } ?: next

                // Rotate the bone (world) from its rest direction onto head→tail, then express it locally.
                val newDir = normalize(sub(next, head), fallback = restDir)
                val worldRotation = multiply(fromToRotation(restDir, newDir), rotationOnly(jointWorld))
                val localRotation = multiply(transposeRotation(rotationOnly(parentWorld)), worldRotation)
                transforms.setLocal(
                    seg.joint.node,
                    compose(translation(seg.restLocal), localRotation, scaleOf(seg.restLocal)),
                )
            }
        }
        lastMaxTailTravel = maxTravel
    }

    private fun constrainLength(head: FloatArray, tail: FloatArray, length: Float): FloatArray =
        add(head, scale(normalize(sub(tail, head)), length))

    /** Pushes [tail] out of a sphere/capsule collider (radius + [hitRadius]) and restores the bone length. */
    private fun collide(
        head: FloatArray,
        tail: FloatArray,
        length: Float,
        hitRadius: Float,
        collider: SpringCollider,
        colliderWorld: FloatArray,
    ): FloatArray {
        val a = transformPoint(colliderWorld, collider.offset)
        val closest = collider.tail?.let { closestPointOnSegment(a, transformPoint(colliderWorld, it), tail) } ?: a
        val radius = collider.radius + hitRadius
        val away = sub(tail, closest)
        if (length(away) >= radius) return tail
        return constrainLength(head, add(closest, scale(normalize(away), radius)), length)
    }

    private fun closestPointOnSegment(a: FloatArray, b: FloatArray, p: FloatArray): FloatArray {
        val ab = sub(b, a)
        val len2 = SpringMath.dot(ab, ab)
        if (len2 < DEGENERATE_CAPSULE_LENGTH_SQ) return a
        val t = (SpringMath.dot(sub(p, a), ab) / len2).coerceIn(0f, 1f)
        return add(a, scale(ab, t))
    }

    companion object {
        /** A long stall (app paused) must not fling the hair: never integrate more than this per step. */
        const val MAX_STEP_S = 1f / 20f
        private const val MIN_SEGMENT_LENGTH_M = 1e-4f

        /** A capsule shorter than this (squared metres) is treated as a sphere at its first point. */
        private const val DEGENERATE_CAPSULE_LENGTH_SQ = 1e-12f
    }
}
