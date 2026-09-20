package com.example.virtualtwitchdroid.feature.avatar.vrm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What [MToonMaterials] found in a file: how many materials are MToon, how many wanted an outline. */
data class MToonSummary(
    /** Materials that use the MToon shader (0.x `VRM/MToon`, 1.0 `VRMC_materials_mtoon`). */
    val mtoonMaterials: Int,
    /** MToon materials that had to be flagged `KHR_materials_unlit` so gltfio renders them flat. */
    val unlitInjected: Int,
    /** MToon materials whose outline mode is not `none` — drawn without an outline by the ubershader. */
    val outlineRequested: Int,
) {
    companion object {
        val NONE = MToonSummary(0, 0, 0)
    }
}

/**
 * The **toon look** with Filament's stock gltfio ubershaders: MToon is a cel shader (flat lit colour, a
 * hard shade colour, optional inverted-hull outlines, rim/matcap) that gltfio does not know. What it does
 * know is `KHR_materials_unlit`, which draws the base colour texture as-is — exactly the flat part of the
 * toon look, and how UniVRM/VRoid export MToon (they write the unlit flag alongside the MToon data). Some
 * exporters omit the flag, and then gltfio shades the character as glossy PBR plastic; [ensureUnlit] adds
 * it to every MToon material that lacks it, so a VRM always renders flat.
 *
 * Outlines, the two-tone shade colour and rim light need a compiled Filament material (`matc` → a
 * `.filamat` MToon port behind a custom `MaterialProvider`): out of scope here and counted in
 * [MToonSummary.outlineRequested] so the gap is visible in the logs.
 */
object MToonMaterials {
    private const val UNLIT = "KHR_materials_unlit"
    private const val MTOON_V1 = "VRMC_materials_mtoon"
    private const val MTOON_V0_SHADER = "VRM/MToon"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Scans [gltfJson] and returns the (possibly rewritten) document plus the summary. The document is
     * returned unchanged (same instance) when every MToon material already carries the unlit flag.
     */
    fun ensureUnlit(gltfJson: String): Pair<String, MToonSummary> {
        val root = json.parseToJsonElement(gltfJson).jsonObject
        val materials = root["materials"]?.jsonArray ?: return gltfJson to MToonSummary.NONE
        val extensions = root["extensions"]?.jsonObject.orEmpty()
        val v0Props = extensions["VRM"]?.jsonObject?.get("materialProperties")?.jsonArray
        var mtoon = 0
        var injected = 0
        var outlines = 0
        val fixed = materials.mapIndexed { i, element ->
            val material = element.jsonObject
            val v1 = material["extensions"]?.jsonObject?.get(MTOON_V1)?.jsonObject
            val v0 = v0Props?.getOrNull(i)?.jsonObject?.takeIf {
                it["shader"]?.jsonPrimitive?.contentOrNull ==
                    MTOON_V0_SHADER
            }
            if (v1 == null && v0 == null) return@mapIndexed element
            mtoon++
            if (wantsOutline(v1, v0)) outlines++
            if (material["extensions"]?.jsonObject?.containsKey(UNLIT) == true) return@mapIndexed element
            injected++
            val ext = material["extensions"]?.jsonObject.orEmpty() + (UNLIT to JsonObject(emptyMap()))
            JsonObject(material + ("extensions" to JsonObject(ext)))
        }
        val summary = MToonSummary(mtoon, injected, outlines)
        if (injected == 0) return gltfJson to summary
        val used: List<JsonElement> = root["extensionsUsed"]?.jsonArray ?: emptyList()
        val usedWithUnlit = JsonArray(
            if (used.any { it.jsonPrimitive.contentOrNull == UNLIT }) {
                used
            } else {
                used +
                    JsonPrimitive(UNLIT)
            },
        )
        val patched: Map<String, JsonElement> =
            root + mapOf("materials" to JsonArray(fixed), "extensionsUsed" to usedWithUnlit)
        val out = JsonObject(patched)
        return json.encodeToString(JsonElement.serializer(), out) to summary
    }

    private fun wantsOutline(v1: JsonObject?, v0: JsonObject?): Boolean {
        if (v1 != null) return (v1["outlineWidthMode"]?.jsonPrimitive?.contentOrNull ?: "none") != "none"
        val mode = v0?.get("floatProperties")?.jsonObject?.get("_OutlineWidthMode")?.jsonPrimitive?.floatOrNull ?: 0f
        return mode > 0f
    }
}
