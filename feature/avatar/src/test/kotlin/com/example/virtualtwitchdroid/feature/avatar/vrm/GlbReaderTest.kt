package com.example.virtualtwitchdroid.feature.avatar.vrm

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class GlbReaderTest {

    /** A minimal valid GLB: header + a space-padded JSON chunk (+ an optional BIN chunk). */
    private fun glb(json: String, version: Int = 2, chunkType: Int = 0x4E4F534A): ByteArray {
        val jsonBytes = json.toByteArray()
        val padded = jsonBytes + ByteArray((4 - jsonBytes.size % 4) % 4) { ' '.code.toByte() }
        val bin = ByteArray(8) // pretend binary payload
        val total = 12 + 8 + padded.size + 8 + bin.size
        return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546C67).putInt(version).putInt(total)
            putInt(padded.size).putInt(chunkType).put(padded)
            putInt(bin.size).putInt(0x004E4942).put(bin) // "BIN\0"
        }.array()
    }

    @Test
    fun readsTheJsonChunk_andStripsThePadding() {
        assertEquals("""{"asset":{"version":"2.0"}}""", GlbReader.readJson(glb("""{"asset":{"version":"2.0"}}""")))
    }

    @Test
    fun withUnescapedSlashes_rewritesTheJsonChunk_keepsAlignmentAndTheBinChunk() {
        val original = glb("""{"images":[{"mimeType":"image\/png"},{"mimeType":"image\/jpeg"}]}""")
        val fixed = GlbReader.withUnescapedSlashes(original)

        // The parser-facing text is what cgltf will see: plain slashes.
        assertEquals("""{"images":[{"mimeType":"image/png"},{"mimeType":"image/jpeg"}]}""", GlbReader.readJson(fixed))
        // Headers are consistent: total length == bytes, JSON chunk 4-byte aligned, BIN chunk intact.
        val buf = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(fixed.size, buf.getInt(8))
        val jsonLen = buf.getInt(12)
        assertEquals(0, jsonLen % 4)
        val binHeader = 12 + 8 + jsonLen
        assertEquals(8, buf.getInt(binHeader)) // our 8-byte pretend BIN payload
        assertEquals(0x004E4942, buf.getInt(binHeader + 4))
    }

    @Test
    fun withUnescapedSlashes_isIdentity_whenNothingIsEscaped() {
        val original = glb("""{"images":[{"mimeType":"image/png"}]}""")
        assertTrue(GlbReader.withUnescapedSlashes(original) === original)
    }

    @Test
    fun withJson_growsTheJsonChunk_rewritesBothLengths_andKeepsTheBinChunk() {
        val original = glb("""{"a":1}""")
        val longer =
            """{"a":1,"materials":[{"extensions":{"KHR_materials_unlit":{}}}],"extensionsUsed":["KHR_materials_unlit"]}"""
        val grown = GlbReader.withJson(original, longer)
        assertTrue(grown.size > original.size)
        assertEquals(longer, GlbReader.readJson(grown))
        val buf = ByteBuffer.wrap(grown).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(grown.size, buf.getInt(8))
        val jsonLen = buf.getInt(12)
        assertEquals(0, jsonLen % 4)
        assertTrue(jsonLen >= longer.toByteArray().size)
        val binHeader = 12 + 8 + jsonLen
        assertEquals(8, buf.getInt(binHeader))
        assertEquals(0x004E4942, buf.getInt(binHeader + 4))
        assertEquals(grown.size, binHeader + 8 + 8)
    }

    @Test
    fun rejectsNonGlbInput() {
        assertFailsWith<IllegalArgumentException> { GlbReader.readJson("not a glb at all!!".toByteArray()) }
        assertFailsWith<IllegalArgumentException> { GlbReader.readJson(glb("{}", version = 1)) }
        assertFailsWith<IllegalArgumentException> { GlbReader.readJson(glb("{}", chunkType = 0x004E4942)) }
    }
}
