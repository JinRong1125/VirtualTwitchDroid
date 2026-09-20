package com.example.virtualtwitchdroid.feature.avatar.render

import android.content.Context
import android.util.Log
import com.example.virtualtwitchdroid.feature.avatar.vrm.VrmContainer
import com.example.virtualtwitchdroid.feature.avatar.vrm.VrmModel
import java.nio.ByteBuffer

/**
 * The one avatar the app loads: `Zundamon_2025_VRM09A.vrm` (ずんだもん, VRM 0.x). Its terms are the zunko.jp
 * guideline — credit required, **non-commercial**, not redistributable — see
 * `assets/avatar/Zundamon_2025_VRM09A.LICENSE.txt`; the Avatar tab shows the credit. The renderer is
 * model-agnostic (VRM 0.x/1.0 via [VrmContainer]/[loadVrm]), so swapping this to any compatible VRM needs no
 * other code change; `BundledAvatarAssetTest` proves the configured model's bones, expressions and spring
 * bones all resolve. There is no fallback model (Seed-san was removed on 2026-09-20).
 */
const val BUNDLED_AVATAR_ASSET = "avatar/Zundamon_2025_VRM09A.vrm"

private const val TAG = "VrmAssets"

/**
 * Reads a VRM asset, parses its VRM semantics, and returns a **direct** buffer of the container prepared
 * for gltfio ([VrmContainer.prepareForGltfio]). Blocking I/O — call off the main thread.
 */
internal fun loadVrm(context: Context, assetPath: String): Pair<ByteBuffer, VrmModel> {
    val bytes = context.assets.open(assetPath).use { it.readBytes() }
    val (forGltfio, model) = VrmContainer.prepareForGltfio(bytes)
    val mtoon = model.mtoon
    if (mtoon.mtoonMaterials > 0) {
        Log.i(
            TAG,
            "MToon: ${mtoon.mtoonMaterials} materials rendered flat (unlit; ${mtoon.unlitInjected} flagged here), " +
                "${mtoon.outlineRequested} request outlines (not drawn: needs a custom Filament material)",
        )
    }
    val direct = ByteBuffer.allocateDirect(forGltfio.size).put(forGltfio).also { it.rewind() }
    return direct to model
}
