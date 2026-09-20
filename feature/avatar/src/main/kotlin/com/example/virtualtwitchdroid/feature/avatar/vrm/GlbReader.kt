package com.example.virtualtwitchdroid.feature.avatar.vrm

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the JSON chunk out of a binary glTF (`.glb` / `.vrm`) container. Layout: a 12-byte header
 * (`glTF` magic, version, total length) followed by chunks of `[length][type][data]`; the first chunk is
 * the `JSON` chunk. Pure, so it is unit-tested against a synthetic container.
 */
object GlbReader {
    private const val MAGIC = 0x46546C67 // "glTF" little-endian
    private const val CHUNK_JSON = 0x4E4F534A // "JSON"
    private const val HEADER_BYTES = 12
    private const val CHUNK_HEADER_BYTES = 8

    /** The glTF JSON document embedded in [glb], as text. */
    fun readJson(glb: ByteArray): String {
        val chunkLength = jsonChunkLength(glb)
        val start = HEADER_BYTES + CHUNK_HEADER_BYTES
        return String(glb, start, chunkLength, Charsets.UTF_8).trimEnd() // JSON chunks are space-padded to 4 bytes
    }

    /**
     * The same container with JSON-escaped slashes (`\/`) in the JSON chunk unescaped to `/`.
     *
     * UniVRM-exported VRMs write MIME types as `"image\/png"` — valid JSON, but Filament's glTF parser
     * (cgltf) copies string bytes verbatim, so `image\/png` never matches its `image/png` texture
     * provider and every texture silently fails to load ("Missing texture provider for image\/png").
     * Returns [glb] itself when there is nothing to rewrite.
     */
    fun withUnescapedSlashes(glb: ByteArray): ByteArray {
        val json = readJson(glb)
        if ("\\/" !in json) return glb
        return withJson(glb, unescapeSlashes(json))
    }

    /** [json] with JSON-escaped slashes unescaped (see [withUnescapedSlashes]). */
    fun unescapeSlashes(json: String): String = json.replace("\\/", "/")

    /**
     * [glb] with its JSON chunk replaced by [json]: the chunk is re-padded to 4 bytes with spaces and
     * both length headers are rewritten; the BIN chunk is carried over untouched.
     */
    fun withJson(glb: ByteArray, json: String): ByteArray {
        val chunkLength = jsonChunkLength(glb)
        val start = HEADER_BYTES + CHUNK_HEADER_BYTES
        val fixed = json.trimEnd().toByteArray(Charsets.UTF_8)
        val padded = fixed + ByteArray((4 - fixed.size % 4) % 4) { ' '.code.toByte() }
        val rest = glb.copyOfRange(start + chunkLength, glb.size) // the BIN chunk (and anything after)
        val out = ByteBuffer.allocate(start + padded.size + rest.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put(glb, 0, 8) // magic + version
        out.putInt(start + padded.size + rest.size) // total length
        out.putInt(padded.size).putInt(CHUNK_JSON).put(padded)
        out.put(rest)
        return out.array()
    }

    /** Validates the container header and returns the JSON chunk's byte length. */
    private fun jsonChunkLength(glb: ByteArray): Int {
        require(glb.size >= HEADER_BYTES + CHUNK_HEADER_BYTES) { "not a GLB: only ${glb.size} bytes" }
        val buf = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == MAGIC) { "not a GLB: bad magic" }
        val version = buf.getInt(4)
        require(version == 2) { "unsupported glTF container version $version" }
        val chunkLength = buf.getInt(HEADER_BYTES)
        val chunkType = buf.getInt(HEADER_BYTES + 4)
        require(chunkType == CHUNK_JSON) { "first GLB chunk is not JSON" }
        val start = HEADER_BYTES + CHUNK_HEADER_BYTES
        require(chunkLength >= 0 && start + chunkLength <= glb.size) { "truncated GLB JSON chunk" }
        return chunkLength
    }
}
