package com.example.virtualtwitchdroid.feature.avatar.vrm

/**
 * Turns a `.vrm` file into what the renderer needs: its parsed semantics and a container gltfio can
 * consume — the JSON chunk rewritten with escaped MIME slashes normalized ([GlbReader.withUnescapedSlashes])
 * and every MToon material flagged unlit ([MToonMaterials]). Pure, so it is unit-tested against the
 * bundled assets; the Android side ([loadVrm]) only reads the file and logs.
 */
object VrmContainer {
    fun prepareForGltfio(glb: ByteArray): Pair<ByteArray, VrmModel> {
        val json = GlbReader.readJson(glb)
        val parsed = VrmParser.parse(json)
        val (unlitJson, mtoon) = MToonMaterials.ensureUnlit(json)
        val model = parsed.copy(mtoon = mtoon)
        // A re-serialized document is already unescaped; an untouched one still needs the slash fix.
        val out = if (unlitJson === json) GlbReader.withUnescapedSlashes(glb) else GlbReader.withJson(glb, unlitJson)
        return out to model
    }
}
