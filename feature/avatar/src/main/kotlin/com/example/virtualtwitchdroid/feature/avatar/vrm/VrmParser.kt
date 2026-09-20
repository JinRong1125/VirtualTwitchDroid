package com.example.virtualtwitchdroid.feature.avatar.vrm

import com.example.virtualtwitchdroid.feature.avatar.rig.LookAtRangeMap
import com.example.virtualtwitchdroid.feature.avatar.rig.LookAtType
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmExpression
import com.example.virtualtwitchdroid.feature.avatar.rig.VrmLookAt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the VRM semantics out of a glTF JSON document — the thin layer on top of Filament's `gltfio`
 * that the plan (§5) calls for. Supports **VRM 1.0** (`VRMC_vrm`) and **VRM 0.x** (`VRM`); anything
 * else throws [IllegalArgumentException]. Only the presets in [VrmExpression] are kept.
 */
object VrmParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(gltfJson: String): VrmModel {
        val root = json.parseToJsonElement(gltfJson).jsonObject
        val extensions = root["extensions"]?.jsonObject ?: throw IllegalArgumentException("glTF has no extensions")
        val nodeNames =
            root["nodes"]?.jsonArray?.map { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull } ?: emptyList()
        val morphCounts = morphTargetCounts(root)
        return when {
            "VRMC_vrm" in extensions -> {
                val vrm = extensions.getValue("VRMC_vrm").jsonObject
                parseV1(vrm, nodeNames, morphCounts).copy(
                    springBones = extensions["VRMC_springBone"]?.jsonObject?.let(::parseSpringsV1),
                    lookAt = vrm["lookAt"]?.jsonObject?.let(::parseLookAtV1),
                )
            }
            "VRM" in extensions -> {
                val vrm = extensions.getValue("VRM").jsonObject
                parseV0(root, vrm, nodeNames, morphCounts).copy(
                    springBones = vrm["secondaryAnimation"]?.jsonObject?.let {
                        parseSpringsV0(it, nodeChildren(root))
                    },
                    lookAt = vrm["firstPerson"]?.jsonObject?.let(::parseLookAtV0),
                )
            }
            else -> throw IllegalArgumentException("not a VRM: no VRMC_vrm / VRM extension")
        }
    }

    // ---- look-at ---------------------------------------------------------------------------------

    /** `VRMC_vrm.lookAt`: `type` (`bone` | `expression`) + four `rangeMap*` `{inputMaxValue, outputScale}`. */
    private fun parseLookAtV1(lookAt: JsonObject): VrmLookAt {
        val type = if (lookAt["type"]?.jsonPrimitive?.contentOrNull ==
            "expression"
        ) {
            LookAtType.EXPRESSION
        } else {
            LookAtType.BONE
        }
        fun range(key: String): LookAtRangeMap {
            val o = lookAt[key]?.jsonObject ?: return VrmLookAt.defaultRange(type)
            val default = VrmLookAt.defaultRange(type)
            return LookAtRangeMap(
                inputMaxValue = o["inputMaxValue"]?.jsonPrimitive?.float ?: default.inputMaxValue,
                outputScale = o["outputScale"]?.jsonPrimitive?.float ?: default.outputScale,
            )
        }
        return VrmLookAt(
            type,
            horizontalInner = range("rangeMapHorizontalInner"),
            horizontalOuter = range("rangeMapHorizontalOuter"),
            verticalDown = range("rangeMapVerticalDown"),
            verticalUp = range("rangeMapVerticalUp"),
        )
    }

    /**
     * VRM 0.x `firstPerson`: `lookAtTypeName` (`Bone` | `BlendShape`) + four `lookAt*` curves whose
     * `xRange` / `yRange` are the 1.0 `inputMaxValue` / `outputScale` (UniVRM's migration mapping).
     */
    private fun parseLookAtV0(firstPerson: JsonObject): VrmLookAt {
        val type =
            if (firstPerson["lookAtTypeName"]?.jsonPrimitive?.contentOrNull ==
                "BlendShape"
            ) {
                LookAtType.EXPRESSION
            } else {
                LookAtType.BONE
            }
        fun range(key: String): LookAtRangeMap {
            val default = VrmLookAt.defaultRange(type)
            val o = firstPerson[key]?.jsonObject ?: return default
            return LookAtRangeMap(
                inputMaxValue = o["xRange"]?.jsonPrimitive?.float ?: default.inputMaxValue,
                outputScale = o["yRange"]?.jsonPrimitive?.float ?: default.outputScale,
            )
        }
        return VrmLookAt(
            type,
            horizontalInner = range("lookAtHorizontalInner"),
            horizontalOuter = range("lookAtHorizontalOuter"),
            verticalDown = range("lookAtVerticalDown"),
            verticalUp = range("lookAtVerticalUp"),
        )
    }

    // ---- spring bones ------------------------------------------------------------------------------

    /** `VRMC_springBone` 1.0: explicit joint chains, collider groups, optional center. */
    private fun parseSpringsV1(ext: JsonObject): SpringBoneSet {
        val colliders = ext["colliders"]?.jsonArray.orEmpty().map { c ->
            val o = c.jsonObject
            val shape = o.getValue("shape").jsonObject
            val sphere = shape["sphere"]?.jsonObject
            val capsule = shape["capsule"]?.jsonObject
            val s = sphere ?: capsule ?: throw IllegalArgumentException("collider without sphere/capsule")
            SpringCollider(
                node = o.getValue("node").jsonPrimitive.int,
                offset = s["offset"]?.let(::vec3) ?: floatArrayOf(0f, 0f, 0f),
                radius = s["radius"]?.jsonPrimitive?.float ?: 0f,
                tail = capsule?.get("tail")?.let(::vec3),
            )
        }
        val groups = ext["colliderGroups"]?.jsonArray.orEmpty().map { g ->
            g.jsonObject["colliders"]?.jsonArray.orEmpty().map { it.jsonPrimitive.int }
        }
        val springs = ext["springs"]?.jsonArray.orEmpty().map { s ->
            val o = s.jsonObject
            SpringChain(
                name = o["name"]?.jsonPrimitive?.contentOrNull,
                joints = o["joints"]?.jsonArray.orEmpty().map { j ->
                    val jo = j.jsonObject
                    SpringJoint(
                        node = jo.getValue("node").jsonPrimitive.int,
                        hitRadius = jo["hitRadius"]?.jsonPrimitive?.float ?: 0f,
                        stiffness = jo["stiffness"]?.jsonPrimitive?.float ?: 1f,
                        gravityPower = jo["gravityPower"]?.jsonPrimitive?.float ?: 0f,
                        gravityDir = jo["gravityDir"]?.let(::vec3) ?: floatArrayOf(0f, -1f, 0f),
                        dragForce = jo["dragForce"]?.jsonPrimitive?.float ?: 0.5f,
                    )
                },
                colliders = o["colliderGroups"]?.jsonArray.orEmpty().flatMap {
                    groups.getOrElse(it.jsonPrimitive.int) { emptyList() }
                },
                center = o["center"]?.jsonPrimitive?.int,
            )
        }
        return SpringBoneSet(colliders, springs)
    }

    /**
     * VRM 0.x `secondaryAnimation`: each bone group lists root bones and UniVRM's `VRMSpringBone` simulates
     * the **whole subtree** under each root — every bone points at its *first* child, other children start
     * their own branch, and a leaf gets a 7 cm virtual tail. Expressed here as one [SpringChain] per
     * first-child path (from the root and from every non-first child), all with [SpringChain.leafTail].
     * One set of parameters per group (`stiffiness` is the spec's spelling).
     *
     * 0.x vectors (collider offsets, gravity direction) are in Unity's left-handed frame — their **Z is
     * negated** into glTF space, exactly as UniVRM's 0.x→1.0 migration (`MigrationVrmSpringBone`:
     * `ReverseZ` on `offset` and `gravityDir`) and three-vrm's 0.x `VRMSpringBoneImporter` do.
     */
    private fun parseSpringsV0(sa: JsonObject, children: Map<Int, List<Int>>): SpringBoneSet {
        val colliders = ArrayList<SpringCollider>()
        val groups = sa["colliderGroups"]?.jsonArray.orEmpty().map { g ->
            val go = g.jsonObject
            val node = go.getValue("node").jsonPrimitive.int
            go["colliders"]?.jsonArray.orEmpty().map { c ->
                val co = c.jsonObject
                colliders +=
                    SpringCollider(
                        node,
                        co["offset"]?.let(::vec3xyzUnityToGltf) ?: floatArrayOf(0f, 0f, 0f),
                        co["radius"]?.jsonPrimitive?.float ?: 0f,
                    )
                colliders.lastIndex
            }
        }
        val springs = sa["boneGroups"]?.jsonArray.orEmpty().flatMap { g ->
            val go = g.jsonObject
            val params = SpringJoint(
                node = -1,
                hitRadius = go["hitRadius"]?.jsonPrimitive?.float ?: 0f,
                stiffness = go["stiffiness"]?.jsonPrimitive?.float ?: 1f,
                gravityPower = go["gravityPower"]?.jsonPrimitive?.float ?: 0f,
                gravityDir = go["gravityDir"]?.let(::vec3xyzUnityToGltf) ?: floatArrayOf(0f, -1f, 0f),
                dragForce = go["dragForce"]?.jsonPrimitive?.float ?: 0.5f,
            )
            val groupColliders = go["colliderGroups"]?.jsonArray.orEmpty().flatMap {
                groups.getOrElse(it.jsonPrimitive.int) { emptyList() }
            }
            val center = go["center"]?.jsonPrimitive?.int?.takeIf { it >= 0 }
            val comment = go["comment"]?.jsonPrimitive?.contentOrNull
            go["bones"]?.jsonArray.orEmpty().flatMap { rootNode ->
                subtreeChains(rootNode.jsonPrimitive.int, children).map { path ->
                    SpringChain(comment, path.map { params.copy(node = it) }, groupColliders, center, leafTail = true)
                }
            }
        }
        return SpringBoneSet(colliders, springs)
    }

    /**
     * The first-child paths covering the subtree under [root]: the path from [root] itself, then one from
     * every non-first child met along the way (recursively). Together they visit each node exactly once.
     */
    internal fun subtreeChains(root: Int, children: Map<Int, List<Int>>): List<List<Int>> {
        val chains = ArrayList<List<Int>>()
        val pending = ArrayDeque(listOf(root))
        val seen = HashSet<Int>() // a malformed file could make the graph cyclic
        while (pending.isNotEmpty()) {
            val start = pending.removeFirst()
            val path = ArrayList<Int>()
            var node: Int? = start
            while (node != null && seen.add(node)) {
                path += node
                val kids = children[node].orEmpty()
                kids.drop(1).forEach(pending::addLast)
                node = kids.firstOrNull()
            }
            if (path.isNotEmpty()) chains += path
        }
        return chains
    }

    private fun nodeChildren(root: JsonObject): Map<Int, List<Int>> =
        root["nodes"]?.jsonArray.orEmpty().withIndex().associate { (i, n) ->
            i to n.jsonObject["children"]?.jsonArray.orEmpty().map { it.jsonPrimitive.int }
        }

    private fun vec3(e: kotlinx.serialization.json.JsonElement): FloatArray = e.jsonArray.let {
        floatArrayOf(it[0].jsonPrimitive.float, it[1].jsonPrimitive.float, it[2].jsonPrimitive.float)
    }

    /** A VRM 0.x `{x, y, z}` vector (Unity, left-handed) converted to glTF space: Z negated. */
    private fun vec3xyzUnityToGltf(e: kotlinx.serialization.json.JsonElement): FloatArray = e.jsonObject.let { o ->
        floatArrayOf(
            o["x"]?.jsonPrimitive?.float ?: 0f,
            o["y"]?.jsonPrimitive?.float ?: 0f,
            -(o["z"]?.jsonPrimitive?.float ?: 0f),
        )
    }

    // ---- VRM 1.0 ---------------------------------------------------------------------------------

    private fun parseV1(vrm: JsonObject, nodeNames: List<String?>, morphCounts: Map<Int, Int>): VrmModel {
        val bones = vrm["humanoid"]?.jsonObject?.get("humanBones")?.jsonObject.orEmpty()
            .mapValues { (_, v) -> v.jsonObject.getValue("node").jsonPrimitive.int }
        val presets = vrm["expressions"]?.jsonObject?.get("preset")?.jsonObject.orEmpty()
        val expressions = buildMap {
            for (expression in VrmExpression.entries) {
                val preset = presets[expression.key]?.jsonObject ?: continue
                val binds = preset["morphTargetBinds"]?.jsonArray.orEmpty().map { b ->
                    val o = b.jsonObject
                    MorphBind(
                        node = o.getValue("node").jsonPrimitive.int,
                        index = o.getValue("index").jsonPrimitive.int,
                        weight = o["weight"]?.jsonPrimitive?.float ?: 1f,
                    )
                }
                if (binds.isNotEmpty()) put(expression, binds)
            }
        }
        val title = vrm["meta"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        return VrmModel(VrmVersion.V1, title, bones, expressions, morphCounts, nodeNames)
    }

    // ---- VRM 0.x ---------------------------------------------------------------------------------

    /** VRM 0.x preset names → the 1.0 presets we drive. */
    private val v0Presets = mapOf(
        "a" to VrmExpression.AA, "i" to VrmExpression.IH, "u" to VrmExpression.OU, "e" to VrmExpression.EE,
        "o" to VrmExpression.OH, "blink" to VrmExpression.BLINK, "blink_l" to VrmExpression.BLINK_LEFT,
        "blink_r" to VrmExpression.BLINK_RIGHT, "joy" to VrmExpression.HAPPY, "angry" to VrmExpression.ANGRY,
        "sorrow" to VrmExpression.SAD, "fun" to VrmExpression.RELAXED, "lookup" to VrmExpression.LOOK_UP,
        "lookdown" to VrmExpression.LOOK_DOWN, "lookleft" to VrmExpression.LOOK_LEFT,
        "lookright" to VrmExpression.LOOK_RIGHT, "neutral" to VrmExpression.NEUTRAL,
    )

    private fun parseV0(
        root: JsonObject,
        vrm: JsonObject,
        nodeNames: List<String?>,
        morphCounts: Map<Int, Int>,
    ): VrmModel {
        val bones = vrm["humanoid"]?.jsonObject?.get("humanBones")?.jsonArray.orEmpty().associate { b ->
            val o = b.jsonObject
            o.getValue("bone").jsonPrimitive.contentOrNull.orEmpty() to o.getValue("node").jsonPrimitive.int
        }
        // 0.x binds reference MESH indices; map them to the node that carries each mesh.
        val meshToNode = root["nodes"]?.jsonArray.orEmpty().withIndex()
            .mapNotNull { (i, n) -> n.jsonObject["mesh"]?.jsonPrimitive?.int?.let { it to i } }.toMap()
        val groups = vrm["blendShapeMaster"]?.jsonObject?.get("blendShapeGroups")?.jsonArray.orEmpty()
        val expressions = buildMap {
            for (group in groups) {
                val g = group.jsonObject
                val preset = g["presetName"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: continue
                val expression = v0Presets[preset] ?: continue
                val binds = g["binds"]?.jsonArray.orEmpty().mapNotNull { b ->
                    val o = b.jsonObject
                    val node = meshToNode[o.getValue("mesh").jsonPrimitive.int] ?: return@mapNotNull null
                    // 0.x weights are 0..100.
                    MorphBind(
                        node,
                        o.getValue("index").jsonPrimitive.int,
                        (o["weight"]?.jsonPrimitive?.float ?: 100f) / 100f,
                    )
                }
                if (binds.isNotEmpty()) put(expression, binds)
            }
        }
        val title = vrm["meta"]?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
        return VrmModel(VrmVersion.V0, title, bones, expressions, morphCounts, nodeNames)
    }

    // ---- shared ----------------------------------------------------------------------------------

    /** node → morph-target count of its mesh's first primitive (glTF requires all primitives to agree). */
    private fun morphTargetCounts(root: JsonObject): Map<Int, Int> {
        val meshes = root["meshes"]?.jsonArray.orEmpty()
        return root["nodes"]?.jsonArray.orEmpty().withIndex().mapNotNull { (i, n) ->
            val mesh = n.jsonObject["mesh"]?.jsonPrimitive?.int ?: return@mapNotNull null
            val targets = meshes.getOrNull(mesh)?.jsonObject?.get("primitives")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("targets")?.jsonArray?.size ?: 0
            if (targets > 0) i to targets else null
        }.toMap()
    }
}
