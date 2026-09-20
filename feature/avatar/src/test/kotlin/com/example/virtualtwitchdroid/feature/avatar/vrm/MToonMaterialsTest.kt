package com.example.virtualtwitchdroid.feature.avatar.vrm

import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class MToonMaterialsTest {

    private val v0 = """
        {
          "extensionsUsed": ["KHR_materials_unlit", "VRM"],
          "materials": [
            {"name": "skin", "pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 1]}},
            {"name": "hair", "extensions": {"KHR_materials_unlit": {}}},
            {"name": "eye_pbr", "pbrMetallicRoughness": {"metallicFactor": 0.5}}
          ],
          "extensions": {"VRM": {"materialProperties": [
            {"name": "skin", "shader": "VRM/MToon", "floatProperties": {"_OutlineWidthMode": 1.0}},
            {"name": "hair", "shader": "VRM/MToon", "floatProperties": {"_OutlineWidthMode": 0.0}},
            {"name": "eye_pbr", "shader": "Standard"}
          ]}}
        }
    """.trimIndent()

    private val v1 = """
        {
          "materials": [
            {"name": "face", "extensions": {"VRMC_materials_mtoon": {"outlineWidthMode": "worldCoordinates"}}},
            {"name": "cloth", "extensions": {"VRMC_materials_mtoon": {}, "KHR_materials_unlit": {}}}
          ],
          "extensions": {"VRMC_vrm": {}}
        }
    """.trimIndent()

    @Test
    fun vrm0_flagsMToonMaterialsUnlit_leavesPbrAlone_countsOutlines() {
        val (json, summary) = MToonMaterials.ensureUnlit(v0)
        assertEquals(MToonSummary(mtoonMaterials = 2, unlitInjected = 1, outlineRequested = 1), summary)
        val materials = Json.parseToJsonElement(json).jsonObject.getValue("materials").jsonArray
        assertTrue("KHR_materials_unlit" in materials[0].jsonObject.getValue("extensions").jsonObject) // injected
        assertTrue("KHR_materials_unlit" in materials[1].jsonObject.getValue("extensions").jsonObject) // kept
        assertEquals(null, materials[2].jsonObject["extensions"]) // Standard shader: still lit
        // Untouched data survives the re-serialization.
        val pbr = materials[2].jsonObject.getValue("pbrMetallicRoughness").jsonObject
        assertEquals("0.5", pbr.getValue("metallicFactor").jsonPrimitive.content)
        val used = Json.parseToJsonElement(json).jsonObject.getValue("extensionsUsed").jsonArray
        assertEquals(2, used.size) // not duplicated
    }

    @Test
    fun vrm1_injectsTheFlag_andRegistersTheExtension() {
        val (json, summary) = MToonMaterials.ensureUnlit(v1)
        assertEquals(MToonSummary(mtoonMaterials = 2, unlitInjected = 1, outlineRequested = 1), summary)
        val root = Json.parseToJsonElement(json).jsonObject
        val face = root.getValue("materials").jsonArray[0].jsonObject
        assertTrue("KHR_materials_unlit" in face.getValue("extensions").jsonObject)
        val used = root.getValue("extensionsUsed").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("KHR_materials_unlit"), used)
    }

    @Test
    fun nothingToFix_returnsTheSameDocument() {
        val already = """{"materials": [{"extensions": {"VRMC_materials_mtoon": {}, "KHR_materials_unlit": {}}}]}"""
        val (json, summary) = MToonMaterials.ensureUnlit(already)
        assertSame(already, json)
        assertEquals(MToonSummary(1, 0, 0), summary)
        val plain = """{"asset": {"version": "2.0"}}"""
        assertSame(plain, MToonMaterials.ensureUnlit(plain).first)
    }
}
