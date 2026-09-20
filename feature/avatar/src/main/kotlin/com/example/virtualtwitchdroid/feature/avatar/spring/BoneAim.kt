package com.example.virtualtwitchdroid.feature.avatar.spring

import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.compose
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.fromToRotation
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.multiply
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.normalize
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.rotationOnly
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.scaleOf
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.transformDir
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.translation
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath.transposeRotation

/**
 * Poses bones by **aiming** them in world space — the same "rotate the bone from its rest direction onto a
 * target direction, then express that locally" step the spring simulation performs, exposed for static
 * posing (the relaxed arm pose) and for small world-axis wiggles (idle motion). Rig-agnostic: rest
 * rotations, parent scale and model facing are all read from the transforms, never assumed.
 */
object BoneAim {
    /** World-space unit direction from [node]'s joint toward [childNode] as currently posed, or null. */
    fun currentDirection(transforms: BoneTransforms, node: Int, childNode: Int): FloatArray? {
        val nodeWorld = transforms.world(node)?.copyOf() ?: return null
        val childWorld = transforms.world(childNode) ?: return null
        return normalize(SpringMath.sub(translation(childWorld), translation(nodeWorld)))
    }

    /**
     * The local transform that makes [node]'s bone (toward [childNode], whose rest local translation defines
     * the bone axis) point along world [targetDirection], keeping the rest translation and scale.
     */
    fun aimLocal(transforms: BoneTransforms, node: Int, childNode: Int, targetDirection: FloatArray): FloatArray? {
        val parentWorld = transforms.parentWorld(node)?.copyOf() ?: return null
        val restLocal = transforms.restLocal(node) ?: return null
        val childRest = transforms.restLocal(childNode) ?: return null
        val jointWorld = multiply(parentWorld, restLocal)
        val restDirection = normalize(transformDir(jointWorld, normalize(translation(childRest))))
        val worldRotation =
            multiply(fromToRotation(restDirection, normalize(targetDirection)), rotationOnly(jointWorld))
        val localRotation = multiply(transposeRotation(rotationOnly(parentWorld)), worldRotation)
        return compose(translation(restLocal), localRotation, scaleOf(restLocal))
    }

    /**
     * [baseLocal] rotated by [worldRotation] (a rotation expressed in **world** axes) about the bone's own
     * joint — e.g. "lift this shoulder 2° about world Z" regardless of how the parent chain is oriented.
     */
    fun rotateInWorld(
        transforms: BoneTransforms,
        node: Int,
        baseLocal: FloatArray,
        worldRotation: FloatArray,
    ): FloatArray? {
        val parentRotation = transforms.parentWorld(node)?.let(::rotationOnly) ?: return null
        val baseRotation = rotationOnly(baseLocal)
        // world' = R · world = R · P · base  ⇒  local' = Pᵀ · R · P · base
        val localRotation =
            multiply(transposeRotation(parentRotation), multiply(worldRotation, multiply(parentRotation, baseRotation)))
        return compose(translation(baseLocal), localRotation, scaleOf(baseLocal))
    }
}
