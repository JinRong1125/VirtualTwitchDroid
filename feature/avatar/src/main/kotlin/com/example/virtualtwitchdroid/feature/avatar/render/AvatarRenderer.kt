package com.example.virtualtwitchdroid.feature.avatar.render

import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.example.virtualtwitchdroid.core.common.media.MouthTrackSource
import com.example.virtualtwitchdroid.feature.avatar.rig.ArmBone
import com.example.virtualtwitchdroid.feature.avatar.rig.ArmRestPose
import com.example.virtualtwitchdroid.feature.avatar.rig.ArmSide
import com.example.virtualtwitchdroid.feature.avatar.rig.BodyLean
import com.example.virtualtwitchdroid.feature.avatar.rig.BodyLeanMapper
import com.example.virtualtwitchdroid.feature.avatar.rig.Eye
import com.example.virtualtwitchdroid.feature.avatar.rig.EyeLookAt
import com.example.virtualtwitchdroid.feature.avatar.rig.FaceRig
import com.example.virtualtwitchdroid.feature.avatar.rig.HeadOffset
import com.example.virtualtwitchdroid.feature.avatar.rig.HeadPose
import com.example.virtualtwitchdroid.feature.avatar.rig.IdleMotion
import com.example.virtualtwitchdroid.feature.avatar.rig.LookAtType
import com.example.virtualtwitchdroid.feature.avatar.rig.MouthOverride
import com.example.virtualtwitchdroid.feature.avatar.rig.RotationMath
import com.example.virtualtwitchdroid.feature.avatar.rig.TimeLag
import com.example.virtualtwitchdroid.feature.avatar.rig.UpperBodyBone
import com.example.virtualtwitchdroid.feature.avatar.rig.UpperBodyFollow
import com.example.virtualtwitchdroid.feature.avatar.rig.UpperBodyPose
import com.example.virtualtwitchdroid.feature.avatar.rig.UpperBodyRig
import com.example.virtualtwitchdroid.feature.avatar.spring.BoneAim
import com.example.virtualtwitchdroid.feature.avatar.spring.BoneTransforms
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringBoneSimulator
import com.example.virtualtwitchdroid.feature.avatar.spring.SpringMath
import com.example.virtualtwitchdroid.feature.avatar.vrm.SpringBoneSet
import com.example.virtualtwitchdroid.feature.avatar.vrm.VrmModel
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.TransformManager
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.util.EnumMap

/**
 * Renders a VRM avatar with Filament + gltfio and drives it from a [FaceRig] every frame:
 * - **expressions** → morph-target weights on the bound mesh nodes ([MorphWeights]);
 * - **head pose** → the humanoid `head` bone's local transform (`rest · R(yaw, pitch, roll)`), then the
 *   skinning matrices are refreshed;
 * - **gaze** → the eye bones, when the model's `lookAt` is bone-driven ([EyeLookAt]).
 *
 * It owns the whole Filament scene (engine, renderer, view, camera, key light, gltfio loaders — the
 * same setup filament-utils' `ModelViewer` performs) so that it can draw into **either** output:
 * - a [TextureView] on screen ([attachTo], swap chain managed by Filament's [UiHelper]), or
 * - an arbitrary [Surface] such as the broadcast encoder's input ([attachSurface]) — the VTuber path.
 *
 * glTF nodes are located in the scene by **name** (gltfio's Java API exposes `getFirstEntityByName`,
 * not node indices), so a model whose driven nodes lack unique names will not animate — logged, not
 * fatal. Camera framing targets the head of a humanoid normalized to [MODEL_HEIGHT_M] facing +Z.
 *
 * Must be created and used on one thread (the main thread); [release] before dropping it.
 */
class AvatarRenderer {

    // One process-wide Filament Engine is shared by every AvatarRenderer (the on-screen Avatar tab and the
    // Go Live VTuber session). Creating a separate Engine per renderer meant two Vulkan Engines were built
    // and destroyed as tabs switched, which raced in Filament's native backend and crashed (SIGSEGV on the
    // SurfaceTexture thread). One Engine, only ever used on the main thread by both, is fully serialized.
    private val engine: Engine = SharedFilamentEngine.acquire()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val camera = engine.createCamera(engine.entityManager.create())
    private val light = engine.entityManager.create()
    private val skybox = Skybox.Builder().color(BG_R, BG_G, BG_B, 1f).build(engine)
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true) // normalize skinning weights (VRM rigs)

    private var asset: FilamentAsset? = null
    private var animator: Animator? = null
    private val readyRenderables = IntArray(READY_RENDERABLES_BATCH)

    private var model: VrmModel? = null
    private val morphEntities = HashMap<Int, Int>() // node → entity

    /** A driven humanoid bone: its scene entity and its rest (bind-pose) local transform. */
    private class BoneHandle(val entity: Int, val rest: FloatArray)

    /** The spine → head chain the face drives (only the bones this model has). */
    private val bones = EnumMap<UpperBodyBone, BoneHandle>(UpperBodyBone::class.java)
    private val headEntity: Int get() = bones[UpperBodyBone.HEAD]?.entity ?: 0

    /** Eye bones of a `lookAt.type = bone` model (see [EyeLookAt]); empty for expression-driven gaze. */
    private val eyeBones = EnumMap<Eye, BoneHandle>(Eye::class.java)
    private var lastGaze = EyeLookAt.Gaze.STRAIGHT

    /** Arm bones posed into the relaxed A-pose at load ([armBase] = that local transform) and idle-animated. */
    private val armBones = EnumMap<ArmBone, BoneHandle>(ArmBone::class.java)
    private val armBase = EnumMap<ArmBone, FloatArray>(ArmBone::class.java)
    private val armOutward = EnumMap<ArmSide, Float>(ArmSide::class.java) // +1: this side's arm points world +X
    private val armNodes = EnumMap<ArmBone, Int>(ArmBone::class.java) // glTF node per arm bone (BoneAim keys)
    private var armTransforms: BoneTransforms? = null

    /** The torso trails the head a little (see [UpperBodyRig]). */
    private val bodyFollow = UpperBodyFollow(BODY_FOLLOW_TAU_NANOS)
    private var lastBodyPose = UpperBodyPose.IDENTITY

    /** The body lean is heavier than the head: the head offset is lagged before it is mapped. */
    private val offsetLag = TimeLag(LEAN_TAU_NANOS, size = 3)
    private var lastLean = BodyLean.NONE

    /** The asset root's framing transform (see [frameModel]); the lean's shift is added on top per frame. */
    private var rootEntity = 0
    private var rootBase: FloatArray? = null

    /** Secondary motion (hair, clothes) — null when the model has no spring bones or they failed to resolve. */
    private var springs: SpringBoneSimulator? = null
    private var springTransforms: BoneTransforms? = null
    private var lastFrameNanos = 0L
    private var loadedNanos = 0L // idle motion runs on a load-relative clock (Float-safe, starts exhaled)
    private var springSteps = 0

    // Per-frame scratch for the driven bone / eye / arm / root transforms, so applyRig allocates no
    // throwaway 4x4 matrices at 30 fps (it is the publish VTuber video source; the CPU is shared with the
    // encoder, tracker and voice). Main-thread only; each is consumed by tm.setTransform / BoneAim — a
    // synchronous read — before the next reuse, so sharing them across the bone, eye and arm loops is safe.
    private val rotScratch = FloatArray(16)
    private val mulScratch = FloatArray(16)
    private val rootScratch = FloatArray(16)

    // Reused per-node morph-weight buffers (one per driven morph node), filled in place by
    // MorphWeights.composeInto each frame instead of allocating a fresh map + arrays. Built at [load].
    private val morphScratch = HashMap<Int, FloatArray>()

    // Once the model's textures are fully loaded AND every renderable is in the scene, the per-frame
    // resourceLoader.asyncUpdateLoad() + populateScene() become no-ops — this latches so they stop being
    // called every frame for the rest of the session (two JNI round-trips per frame otherwise). Re-armed
    // by [load]/[destroyModel].
    private var loadFinalized = false

    // The single output: exactly one of these is in use.
    private var uiHelper: UiHelper? = null
    private var ownedSurface: Surface? = null // attachSurface(): released by us after the swap chain goes
    private var swapChain: SwapChain? = null

    /** The latest rig; applied on the next [render]. */
    @Volatile var rig: FaceRig = FaceRig.NEUTRAL

    /** While its `active` flag is set, the Zundamon voice owns the mouth presets (sampled at frame time). */
    @Volatile var mouthTrack: MouthTrackSource? = null

    /** Once released, every entry point is a no-op — Filament aborts the process on use-after-shutdown. */
    private var released = false

    init {
        Log.i(TAG, "Filament backend: ${engine.backend}")
        view.scene = scene
        view.camera = camera
        scene.skybox = skybox
        // Exposure + a 6500 K key light from above — filament-utils' ModelViewer defaults, which the
        // Phase-1 look was tuned against.
        camera.setExposure(APERTURE, SHUTTER_SPEED_S, SENSITIVITY_ISO)
        val (r, g, b) = Colors.cct(LIGHT_CCT_K)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(LIGHT_INTENSITY_LUX)
            .direction(0f, -1f, 0f)
            .castShadows(true)
            .build(engine, light)
        scene.addEntity(light)
        camera.lookAt(
            0.0, HEAD_HEIGHT_M.toDouble(), CAMERA_DISTANCE_M.toDouble(),
            0.0, HEAD_HEIGHT_M.toDouble(), 0.0,
            0.0, 1.0, 0.0,
        )
    }

    // ---- outputs -------------------------------------------------------------------------------

    /** Draw into [textureView]; Filament's [UiHelper] follows the view's surface lifecycle and size. */
    fun attachTo(textureView: TextureView) {
        if (released) return
        check(uiHelper == null && ownedSurface == null) { "renderer already has an output" }
        val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        helper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) = setSwapChain(surface)

            override fun onDetachedFromSurface() = clearSwapChain()

            override fun onResized(width: Int, height: Int) = resize(width, height)
        }
        helper.attachTo(textureView)
        uiHelper = helper
    }

    /**
     * Draw into [surface] at [width]×[height] (e.g. the encoder's input). The renderer takes ownership of
     * [surface] and releases it in [detachSurface] / [release], after its swap chain is gone.
     */
    fun attachSurface(surface: Surface, width: Int, height: Int) {
        if (released) return
        check(uiHelper == null) { "renderer is attached to a view" }
        detachSurface()
        ownedSurface = surface
        setSwapChain(surface)
        resize(width, height)
        Log.i(TAG, "attached output surface ${width}x$height")
    }

    /** Stop drawing into the surface from [attachSurface] and release it; no-op otherwise. */
    fun detachSurface() {
        val surface = ownedSurface ?: return
        clearSwapChain()
        ownedSurface = null
        surface.release()
        Log.i(TAG, "detached output surface")
    }

    private fun setSwapChain(surface: Surface) {
        clearSwapChain()
        swapChain = engine.createSwapChain(surface)
    }

    private fun clearSwapChain() {
        swapChain?.let {
            engine.destroySwapChain(it)
            engine.flushAndWait() // the GPU must be done with the surface before anyone reuses it
        }
        swapChain = null
    }

    private fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        view.viewport = Viewport(0, 0, width, height)
        camera.setLensProjection(FOCAL_LENGTH_MM, width.toDouble() / height, NEAR_M, FAR_M)
    }

    // ---- model ---------------------------------------------------------------------------------

    /** Loads a VRM: [glb] is the whole file (a **direct** buffer), [model] its parsed VRM semantics. */
    fun load(glb: ByteBuffer, model: VrmModel) {
        lightsAdded = false
        loadFinalized = false // re-arm the per-frame populate/asyncUpdateLoad until this model finishes loading
        if (released) {
            Log.w(TAG, "load()@${hashCode()} ignored: already released")
            return
        }
        Log.i(TAG, "load()@${hashCode()}: creating gltfio asset")
        loadedNanos = 0L // re-armed on the first rendered frame after this load
        destroyModel()
        val asset = assetLoader.createAsset(glb) ?: run {
            Log.w(TAG, "gltfio could not create the asset")
            return
        }
        this.asset = asset
        resourceLoader.asyncBeginLoad(asset) // textures stream in over the next frames (asyncUpdateLoad)
        animator = asset.instance.animator
        asset.releaseSourceData()
        this.model = model
        Log.i(
            TAG,
            "loaded '${model.title}' (${model.version}): ${asset.entities.size} entities, ${asset.renderableEntities.size} renderables",
        )
        for (node in model.expressions.values.flatten().map { it.node }.toSet()) {
            val entity = model.nodeNames.getOrNull(node)?.let { asset.getFirstEntityByName(it) } ?: 0
            if (entity != 0) morphEntities[node] = entity else Log.w(TAG, "no scene entity for morph node $node")
        }
        // Reused morph-weight buffers, one per driven node that has a morph-target count (composeInto fills
        // these in place each frame; applyRig applies them directly, so no per-frame map/array allocation).
        for ((node, _) in morphEntities) {
            model.morphTargetCounts[node]?.let { morphScratch[node] = FloatArray(it) }
        }
        val tm = engine.transformManager
        for (bone in UpperBodyBone.entries) {
            val node = model.humanoidBones[bone.vrmName] ?: continue // optional bone (e.g. upperChest)
            val entity = model.nodeNames.getOrNull(node)?.let { asset.getFirstEntityByName(it) } ?: 0
            if (entity == 0) {
                Log.w(TAG, "no scene entity for ${bone.vrmName} node $node")
                continue
            }
            bones[bone] = BoneHandle(entity, FloatArray(16).also { tm.getTransform(tm.getInstance(entity), it) })
        }
        Log.i(TAG, "driven bones: ${bones.keys.map { it.vrmName }}")
        if (model.lookAt?.type == LookAtType.BONE) {
            for (eye in Eye.entries) {
                val node = model.humanoidBones[eye.vrmName] ?: continue
                val entity = model.nodeNames.getOrNull(node)?.let { asset.getFirstEntityByName(it) } ?: 0
                if (entity == 0) {
                    Log.w(TAG, "no scene entity for ${eye.vrmName} node $node")
                    continue
                }
                eyeBones[eye] = BoneHandle(entity, FloatArray(16).also { tm.getTransform(tm.getInstance(entity), it) })
            }
        }
        Log.i(TAG, "lookAt: ${model.lookAt?.type} eye bones=${eyeBones.keys.map { it.vrmName }} ranges=${model.lookAt}")
        frameModel(asset)
        poseArmsRelaxed(asset, model)
        model.springBones?.let { setUpSprings(asset, model, it) } // after the arms: sleeves start posed
    }

    /**
     * VRM rest pose is a T-pose; put the arms into a relaxed A-pose by aiming each arm bone at a world
     * direction ([ArmRestPose]) — rig-agnostic, so it works for 1.0 and 0.x rigs with any rest rotations.
     * The posed locals become the base the per-frame idle motion wiggles around.
     */
    private fun poseArmsRelaxed(asset: FilamentAsset, model: VrmModel) {
        val tm = engine.transformManager
        val entities = HashMap<Int, Int>()
        val rests = HashMap<Int, FloatArray>()
        val nodeOf = HashMap<String, Int>() // vrm bone name → node
        for (bone in ArmBone.entries) {
            for (vrmName in listOf(bone.vrmName, bone.childVrmName)) {
                val node = model.humanoidBones[vrmName] ?: continue
                val entity = model.nodeNames.getOrNull(node)?.let { asset.getFirstEntityByName(it) } ?: 0
                if (entity == 0) continue
                nodeOf[vrmName] = node
                entities[node] = entity
                rests.getOrPut(node) { FloatArray(16).also { tm.getTransform(tm.getInstance(entity), it) } }
            }
        }
        val transforms = FilamentBoneTransforms(tm, entities, rests)
        armTransforms = transforms
        for (bone in ArmBone.entries) {
            val node = nodeOf[bone.vrmName] ?: continue
            val child = nodeOf[bone.childVrmName] ?: continue
            armBones[bone] = BoneHandle(entities.getValue(node), rests.getValue(node))
            armNodes[bone] = node
            val outward = armOutward.getOrPut(bone.side) {
                // Measured, not assumed: in the T-pose the arm points straight out to its own side.
                // A rig exported already A-posed has near-vertical arms: then fall back to which side of
                // the body the joint sits on (world X of the joint; the model is centred at load).
                val dir = BoneAim.currentDirection(transforms, node, child) ?: floatArrayOf(1f, 0f, 0f)
                val x = if (kotlin.math.abs(dir[0]) >= OUTWARD_MIN_X) dir[0] else transforms.world(node)?.get(12) ?: 1f
                if (x >= 0f) 1f else -1f
            }
            val target = when (bone) {
                ArmBone.LEFT_UPPER_ARM, ArmBone.RIGHT_UPPER_ARM -> ArmRestPose.upperArmDirection(outward)
                ArmBone.LEFT_LOWER_ARM, ArmBone.RIGHT_LOWER_ARM -> ArmRestPose.lowerArmDirection(outward)
                ArmBone.LEFT_SHOULDER, ArmBone.RIGHT_SHOULDER -> null // shoulders keep their rest pose
            }
            val local = target?.let { BoneAim.aimLocal(transforms, node, child, it) } ?: rests.getValue(node)
            armBase[bone] = local
            transforms.setLocal(node, local) // top-down: the lower arm is aimed under the posed upper arm
        }
        Log.i(TAG, "arms posed: ${armBones.keys.map { it.vrmName }} outward=$armOutward")
    }

    /**
     * Resolves every node the spring chains touch and builds the simulator on the posed, framed model (so
     * rest lengths are in world units). A node the scene lacks disables the chains that use it, never the load.
     */
    private fun setUpSprings(asset: FilamentAsset, model: VrmModel, set: SpringBoneSet) {
        val tm = engine.transformManager
        val entities = HashMap<Int, Int>()
        val rests = HashMap<Int, FloatArray>()
        for (node in set.nodes) {
            val entity = model.nodeNames.getOrNull(node)?.let { asset.getFirstEntityByName(it) } ?: 0
            if (entity == 0) {
                Log.w(TAG, "spring bones: no scene entity for node $node")
                continue
            }
            entities[node] = entity
            rests[node] = FloatArray(16).also { tm.getTransform(tm.getInstance(entity), it) }
        }
        val transforms = FilamentBoneTransforms(tm, entities, rests)
        val simulator = runCatching { SpringBoneSimulator(set, transforms) }
            .onFailure { Log.w(TAG, "spring bones disabled", it) }
            .getOrNull() ?: return
        springs = simulator
        springTransforms = transforms
        Log.i(
            TAG,
            "spring bones: ${set.springs.size} chains, ${simulator.segmentCount} segments, ${set.colliders.size} colliders",
        )
        if (simulator.droppedChains.isNotEmpty()) {
            Log.w(TAG, "spring bones: chains dropped (unresolved node or zero-length): ${simulator.droppedChains}")
        }
    }

    private fun destroyModel() {
        resourceLoader.asyncCancelLoad()
        resourceLoader.evictResourceData()
        asset?.let {
            scene.removeEntities(it.entities)
            assetLoader.destroyAsset(it)
        }
        asset = null
        animator = null
        model = null
        morphEntities.clear()
        morphScratch.clear()
        bones.clear()
        eyeBones.clear()
        lastGaze = EyeLookAt.Gaze.STRAIGHT
        armBones.clear()
        armBase.clear()
        armOutward.clear()
        armNodes.clear()
        armTransforms = null
        bodyFollow.reset()
        lastBodyPose = UpperBodyPose.IDENTITY
        offsetLag.reset()
        lastLean = BodyLean.NONE
        rootEntity = 0
        rootBase = null
        springs = null
        springTransforms = null
    }

    /**
     * Scales the asset to [MODEL_HEIGHT_M] and places it so the **head bone** sits exactly at the camera's
     * target (0, [HEAD_HEIGHT_M], 0) — an asymmetric model (Seed-san's robot arm) would otherwise be
     * framed off-centre if we centred its bounding box. Falls back to bounding-box centring when the
     * head bone is unknown. Only the asset root is touched; bone-local transforms (the head we drive)
     * are unaffected.
     */
    private fun frameModel(asset: FilamentAsset) {
        val box = asset.boundingBox
        val center = box.center
        val half = box.halfExtent
        val height = half[1] * 2f
        Log.i(TAG, "bounding box centre=${center.toList()} halfExtent=${half.toList()} (height ${height}m)")
        if (height <= 0f) return
        val scale = MODEL_HEIGHT_M / height
        val tm = engine.transformManager
        // Anchor: the head bone in model space (the root is still identity here), else the box centre.
        val anchor = if (headEntity != 0) {
            FloatArray(16).also {
                tm.getWorldTransform(tm.getInstance(headEntity), it)
            }.let { floatArrayOf(it[12], it[13], it[14]) }
        } else {
            floatArrayOf(center[0], center[1] + half[1] * HEAD_FRACTION_OF_HEIGHT, center[2])
        }
        // A VRM 0.x model faces −Z; turn it 180° about Y (x, z → −x, −z) so it faces the camera at +Z
        // like a 1.0 model. Root = T · Ry(0° | 180°) · S, with T chosen so the anchor lands on the target.
        val facing = if (model?.facesNegativeZ == true) -1f else 1f
        val m = FloatArray(16)
        m[0] = facing * scale
        m[5] = scale
        m[10] = facing * scale
        m[15] = 1f
        m[12] = -facing * anchor[0] * scale
        m[13] = HEAD_HEIGHT_M - anchor[1] * scale
        m[14] = -facing * anchor[2] * scale
        tm.setTransform(tm.getInstance(asset.root), m)
        rootEntity = asset.root
        rootBase = m
    }

    /** Adds renderables to the scene as gltfio finishes preparing them (progressive, like ModelViewer). */

    /** Returns true when nothing was popped this call (all renderables are already in the scene). */
    private fun populateScene(asset: FilamentAsset): Boolean {
        val rm = engine.renderableManager
        var poppedAny = false
        while (true) {
            val count = asset.popRenderables(readyRenderables)
            if (count == 0) break
            poppedAny = true
            for (i in 0 until count) rm.setScreenSpaceContactShadows(rm.getInstance(readyRenderables[i]), true)
            scene.addEntities(readyRenderables.copyOf(count))
        }
        if (!lightsAdded) { // idempotent in Filament but a JNI call + int[] per frame otherwise
            scene.addEntities(asset.lightEntities)
            lightsAdded = true
        }
        return !poppedAny
    }

    private var lightsAdded = false

    /** The mouth is sampled this far ahead of vsync: the frame is seen/encoded about one frame later. */
    @Volatile var mouthLookaheadNanos = 0L

    // ---- per frame -----------------------------------------------------------------------------

    /** One frame: apply the current [rig], refresh skinning, draw. Call from a Choreographer callback. */
    fun render(frameTimeNanos: Long) {
        if (released) return
        val target = swapChain ?: return
        if (uiHelper?.isReadyToRender == false) return
        if (!loggedReady) {
            loggedReady = true
            Log.i(TAG, "output ready — first frame")
        }
        // Until the model is fully loaded, finalize streamed textures and add renderables as they become
        // ready. Once textures report complete AND every renderable is in the scene, latch off — otherwise
        // these are two JNI round-trips per frame for the rest of the session doing nothing.
        if (!loadFinalized) {
            resourceLoader.asyncUpdateLoad() // finalize textures that became ready
            val drained = asset?.let(::populateScene) ?: false
            if (drained && lightsAdded && resourceLoader.asyncGetLoadProgress() >= 1f) {
                loadFinalized = true
                Log.i(TAG, "resource load finalized after $frames frames — pausing per-frame populate")
            }
        }
        if (++frames == SCENE_LOG_FRAME) {
            // One-time framing check: how many entities made it into the scene, where the camera is and
            // where the head bone ended up in world space (both should be ~HEAD_HEIGHT_M high, 0.9 m apart).
            val eye = camera.getPosition(FloatArray(3))
            val head = if (headEntity != 0) {
                val tm = engine.transformManager
                FloatArray(16).also {
                    tm.getWorldTransform(tm.getInstance(headEntity), it)
                }.let { listOf(it[12], it[13], it[14]) }
            } else {
                null
            }
            Log.i(TAG, "after $frames frames: ${scene.entityCount} scene entities, eye=${eye.toList()}, head=$head")
        }
        if (frames % PERIODIC_LOG_FRAMES == 0) {
            // Periodic health line: achieved render rate and the torso follow-through applied this frame.
            val fps = PERIODIC_LOG_FRAMES * NANOS_PER_SECOND / (frameTimeNanos - periodicLogNanos).coerceAtLeast(1L)
            periodicLogNanos = frameTimeNanos
            Log.i(
                TAG,
                "$fps fps · face=${rig.faceDetected} · chest=${lastBodyPose[UpperBodyBone.CHEST]} " +
                    "neck=${lastBodyPose[UpperBodyBone.NECK]} head=${lastBodyPose[UpperBodyBone.HEAD]} · " +
                    "offset=${rig.headOffset} lean=${lastLean.torso} " +
                    "shift=(${lastLean.shiftX}, ${lastLean.shiftY}) · " +
                    "springs=${springs?.segmentCount ?: 0} maxTailTravel=${springs?.lastMaxTailTravel ?: 0f}m · " +
                    "gaze L=${lastGaze.left} R=${lastGaze.right}",
            )
        }
        val voice = mouthTrack?.takeIf { it.active.value }
        val mouth = voice?.sample(frameTimeNanos + mouthLookaheadNanos)
        if (mouth != null &&
            maxOf(mouth.aa, mouth.ih, mouth.ou, mouth.ee, mouth.oh) > MOUTH_LOG_THRESHOLD &&
            frameTimeNanos - lastMouthLogNanos > NANOS_PER_SECOND
        ) {
            // Objective lip-sync evidence: the voice's viseme track is driving the mouth this frame.
            lastMouthLogNanos = frameTimeNanos
            Log.i(TAG, "voice mouth: $mouth")
        }
        applyRig(mouth?.let { MouthOverride.apply(rig, it) } ?: rig, frameTimeNanos)
        // Secondary motion after the driven bones: each segment reads its parent's fresh world transform.
        springs?.let { sim ->
            val elapsedNanos = frameTimeNanos - lastFrameNanos
            val dt = if (lastFrameNanos == 0L) DEFAULT_FRAME_S else elapsedNanos / NANOS_PER_SECOND.toFloat()
            springTransforms?.let {
                sim.step(dt, it)
                if (springSteps++ < SPRING_DEBUG_FRAMES || frames % PERIODIC_LOG_FRAMES == 0) {
                    Log.d(TAG, "springs step=$springSteps travel=${sim.lastMaxTailTravel} ${sim.debugSnapshot(it)}")
                }
            }
        }
        lastFrameNanos = frameTimeNanos
        animator?.updateBoneMatrices()
        if (renderer.beginFrame(target, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private var loggedReady = false
    private var frames = 0
    private var periodicLogNanos = 0L
    private var lastMouthLogNanos = 0L

    fun release() {
        if (released) return
        released = true
        Log.i(TAG, "release()@${hashCode()}")
        uiHelper?.detach() // → onDetachedFromSurface → clearSwapChain, while the engine is still alive
        uiHelper = null
        detachSurface()
        destroyModel()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        resourceLoader.destroy()
        engine.destroySkybox(skybox)
        engine.destroyEntity(light)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        EntityManager.get().destroy(camera.entity)
        EntityManager.get().destroy(light)
        // The shared Engine is deliberately NOT destroyed here: it is process-wide (SharedFilamentEngine),
        // outlives every renderer, and is only touched on the main thread — so switching tabs never tears a
        // Filament Engine down, which is what raced and crashed natively.
    }

    private fun applyRig(rig: FaceRig, frameTimeNanos: Long) {
        val model = model ?: return
        val rm = engine.renderableManager
        MorphWeights.composeInto(rig, model, morphScratch)
        for ((node, entity) in morphEntities) {
            val instance = rm.getInstance(entity)
            if (instance == 0) continue
            // morphScratch[node] was zeroed then filled by composeInto, so an undriven node applies zeros
            // (e.g. a released blink re-opens) — same as the old compose()+reset path, without allocating.
            val target = morphScratch[node] ?: continue
            rm.setMorphWeights(instance, target, 0)
        }
        if (bones.isEmpty()) return
        // Head-driven upper body: the torso takes fractions of a lagged head pose, the head bone the
        // remainder, so the face lands on the tracked pose while the chest/shoulders follow through.
        // Bone rotations live in the model's frame: a VRM 0.x rig faces −Z, so face-frame angles are
        // converted (pitch/roll flip). The root is already turned to face the camera (see frameModel), so
        // world-frame quantities (the lean's root shift) need no conversion.
        val toModel: (HeadPose) -> HeadPose = { if (model.facesNegativeZ) it.forModelFacingNegativeZ() else it }
        val tracked = toModel(clamp(rig.head))
        // Body lean + shift from the head's displacement (lagged: the torso is heavier than the head).
        val offset = rig.headOffset ?: HeadOffset.ZERO
        val lagged = offsetLag.next(floatArrayOf(offset.x, offset.y, offset.z), frameTimeNanos)
        val lean = BodyLeanMapper.map(HeadOffset(lagged[0], lagged[1], lagged[2]))
        lastLean = lean
        // Idle layer: breathing opens the chest a little (face-frame pitch, converted like the lean).
        if (loadedNanos == 0L) loadedNanos = frameTimeNanos
        val idle = IdleMotion.at(((frameTimeNanos - loadedNanos).toDouble() / NANOS_PER_SECOND).toFloat())
        val leanWithBreath = lean.torso.copy(pitch = lean.torso.pitch + idle.chestPitchDeg)
        val body = UpperBodyRig.distribute(
            tracked = tracked,
            followed = bodyFollow.next(tracked, frameTimeNanos),
            present = bones.keys,
            lean = toModel(leanWithBreath),
        )
        lastBodyPose = body
        val tm = engine.transformManager
        rootBase?.let { base ->
            if (rootEntity != 0) {
                base.copyInto(rootScratch)
                rootScratch[12] += lean.shiftX
                rootScratch[13] += lean.shiftY
                tm.setTransform(tm.getInstance(rootEntity), rootScratch)
            }
        }
        for ((bone, handle) in bones) {
            // While the torso still lags a fast reversal, the head remainder can briefly exceed what a
            // neck can do; bound the head bone itself so the face never snaps past the clamp.
            val local = if (bone == UpperBodyBone.HEAD) clamp(body[bone]) else body[bone]
            tm.setTransform(
                tm.getInstance(handle.entity),
                RotationMath.multiply(handle.rest, RotationMath.rotationMatrix(local, rotScratch), mulScratch),
            )
        }
        animateEyes(rig, model, toModel)
        animateArms(idle, clamp(rig.head).roll)
    }

    /**
     * Bone-driven gaze: each eye bone is rotated `rest · R(gaze)` in the head's frame, the gaze coming from
     * the tracked `look*` weights through the model's range maps ([EyeLookAt]) — face-frame angles, so the
     * same 0.x conversion as the head applies. Expression-driven models get their gaze via morphs instead.
     *
     * Like the head bone, `rest · R` assumes the eye's **rest rotation is identity** (angles are applied in
     * the bone's own rest axes): true for every VRM 0.x rig (normalized hierarchy) and for the VRM 1.0 files
     * we ship, but a 1.0 rig exported with rotated eye rests would need `T · R · Rot(rest) · S` instead.
     */
    private fun animateEyes(rig: FaceRig, model: VrmModel, toModel: (HeadPose) -> HeadPose) {
        if (eyeBones.isEmpty()) return
        val lookAt = model.lookAt ?: return
        val gaze = EyeLookAt.solve(rig, lookAt)
        lastGaze = gaze
        val tm = engine.transformManager
        for ((eye, handle) in eyeBones) {
            val pose = toModel(if (eye == Eye.LEFT) gaze.left else gaze.right)
            tm.setTransform(
                tm.getInstance(handle.entity),
                RotationMath.multiply(handle.rest, RotationMath.rotationMatrix(pose, rotScratch), mulScratch),
            )
        }
    }

    /**
     * Shoulders and upper arms wiggle about the world forward axis (Z) around their posed base: shoulders
     * rise with the breath and with a head tilt (the shoulder under the tilted head comes up), upper arms
     * swing slowly out and back. Rotations are applied in world axes so left/right and rig facing don't matter.
     */
    private fun animateArms(idle: IdleMotion.Offsets, headRollDeg: Float) {
        val transforms = armTransforms ?: return
        val tm = engine.transformManager
        for ((bone, handle) in armBones) {
            val outward = armOutward[bone.side] ?: continue
            // Positive head roll tilts the crown toward the subject's right (world −X): that side's shoulder lifts.
            // Capped so a strong tilt can't swing the hanging arm into (or away from) the torso.
            val rollLift = (headRollDeg * IdleMotion.SHOULDER_PER_HEAD_ROLL * (if (outward < 0f) 1f else -1f))
                .coerceIn(-MAX_ROLL_LIFT_DEG, MAX_ROLL_LIFT_DEG)
            val liftDeg = when (bone) {
                ArmBone.LEFT_SHOULDER, ArmBone.RIGHT_SHOULDER -> idle.shoulderLiftDeg + rollLift
                // The upper arm counter-rotates the roll lift: the clavicle rises, the arm stays hanging.
                ArmBone.LEFT_UPPER_ARM, ArmBone.RIGHT_UPPER_ARM -> idle.armSwayDeg - rollLift
                ArmBone.LEFT_LOWER_ARM, ArmBone.RIGHT_LOWER_ARM -> continue // rides along on the upper arm
            }
            // About world +Z, a bone pointing out to +X rises with a positive angle, one pointing to −X with a negative.
            // rotScratch is reused (BoneAim.rotateInWorld reads it synchronously and returns a fresh matrix).
            val worldRotation =
                RotationMath.rotationMatrix(HeadPose(yaw = 0f, pitch = 0f, roll = outward * liftDeg), rotScratch)
            val base = armBase[bone] ?: handle.rest
            val node = armNodes[bone] ?: continue
            BoneAim.rotateInWorld(transforms, node, base, worldRotation)?.let {
                tm.setTransform(tm.getInstance(handle.entity), it)
            }
        }
    }

    private fun clamp(pose: HeadPose) = HeadPose(
        yaw = pose.yaw.coerceIn(-MAX_HEAD_DEG, MAX_HEAD_DEG),
        pitch = pose.pitch.coerceIn(-MAX_HEAD_DEG, MAX_HEAD_DEG),
        roll = pose.roll.coerceIn(-MAX_HEAD_DEG, MAX_HEAD_DEG),
    )

    companion object {
        private const val TAG = "AvatarRenderer"

        /** Every model is scaled to this standing height — see [frameModel]. */
        private const val MODEL_HEIGHT_M = 1.6f

        /** The head bone is placed here and the camera looks at it from just in front (+Z). */
        private const val HEAD_HEIGHT_M = 1.42f
        private const val CAMERA_DISTANCE_M = 0.9f

        /** Fallback head estimate (fraction of half-height above the box centre) when no head bone. */
        private const val HEAD_FRACTION_OF_HEIGHT = 0.8f

        // Camera lens + exposure (ModelViewer's defaults: a 28 mm lens, f/16, 1/125 s, ISO 100).
        private const val FOCAL_LENGTH_MM = 28.0
        private const val NEAR_M = 0.05
        private const val FAR_M = 1000.0
        private const val APERTURE = 16f
        private const val SHUTTER_SPEED_S = 1f / 125f
        private const val SENSITIVITY_ISO = 100f

        // Key light: daylight-white, from above.
        private const val LIGHT_CCT_K = 6_500f
        private const val LIGHT_INTENSITY_LUX = 100_000f

        /** How many just-ready renderables to move into the scene per pop (ModelViewer uses 128). */
        private const val READY_RENDERABLES_BATCH = 128

        /** Log the scene population once, well after textures should have finished loading. */
        private const val SCENE_LOG_FRAME = 240

        /** A real neck can't do more; also protects against tracker glitches. */
        private const val MAX_HEAD_DEG = 60f

        /** Torso lag behind the head (time constant), independent of the render rate. */
        private const val BODY_FOLLOW_TAU_NANOS = 150_000_000L

        /** The body lean/shift trails the head's displacement by a bit more (a whole torso is heavy). */
        private const val LEAN_TAU_NANOS = 250_000_000L

        /** A head tilt lifts the near shoulder at most this much (degrees). */
        private const val MAX_ROLL_LIFT_DEG = 4f

        /** Below this |x| a measured arm direction is too vertical to tell the side from. */
        private const val OUTWARD_MIN_X = 0.2f

        /** First spring step (no previous frame time yet). */
        private const val DEFAULT_FRAME_S = 1f / 30f

        /** Spring state is logged for the first few frames after load (diagnostics). */
        private const val SPRING_DEBUG_FRAMES = 3
        private const val PERIODIC_LOG_FRAMES = 300
        private const val NANOS_PER_SECOND = 1_000_000_000L

        /** A voice-driven mouth more open than this is logged (at most once a second). */
        private const val MOUTH_LOG_THRESHOLD = 0.5f

        private const val BG_R = 0.07f
        private const val BG_G = 0.06f
        private const val BG_B = 0.10f

        init {
            // Loads libfilament-jni + libgltfio-jni once per process, before any Filament call.
            Filament.init()
            Gltfio.init()
        }
    }
}

/** [BoneTransforms] over Filament's [TransformManager] for the nodes resolved at load. */
private class FilamentBoneTransforms(
    private val tm: TransformManager,
    private val entities: Map<Int, Int>,
    private val rests: Map<Int, FloatArray>,
) : BoneTransforms {
    // Matrices returned by world()/parentWorld() are SCRATCH buffers reused on the next call of the same
    // kind — the simulator consumes each before asking again; never hold on to them.
    private val worldScratch = FloatArray(16)
    private val parentScratch = FloatArray(16)

    override fun world(node: Int): FloatArray? =
        entities[node]?.let { tm.getWorldTransform(tm.getInstance(it), worldScratch) }

    override fun parentWorld(node: Int): FloatArray? {
        val entity = entities[node] ?: return null
        // getParent() returns the parent ENTITY (0 = none), which must be mapped to its transform instance.
        val parentEntity = tm.getParent(tm.getInstance(entity))
        if (parentEntity == 0) return SpringMath.identity()
        val parentInstance = tm.getInstance(parentEntity)
        return if (parentInstance == 0) SpringMath.identity() else tm.getWorldTransform(parentInstance, parentScratch)
    }

    override fun restLocal(node: Int): FloatArray? = rests[node]

    override fun setLocal(node: Int, local: FloatArray) {
        entities[node]?.let { tm.setTransform(tm.getInstance(it), local) }
    }
}

/**
 * One skinned character has little parallel work: two job-system workers instead of Filament's default
 * (cores − 1 = 8 on a Pixel 8a) leave the CPU to MediaPipe, the encoder, the Zipformer and VOICEVOX.
 */
private fun engineConfig(): Engine.Config = Engine.Config().apply { jobSystemThreadCount = 2 }

/**
 * The one process-wide Filament [Engine], shared by every [AvatarRenderer]. Created lazily on first use —
 * always the main thread, which is Filament's single-thread contract — and intentionally never destroyed:
 * it lives for the process (the standard Filament pattern). This is what makes switching tabs safe; before,
 * each renderer built and tore down its own Vulkan Engine and two of them alternating crashed natively.
 *
 * Prefer Vulkan: on the Android emulator the Metal-backed GLES translator presents the skybox but never
 * rasterizes gltfio's skinned + morphed meshes, while gfxstream Vulkan is complete; real devices are fine
 * either way. Engine.Builder.build() THROWS when the backend is unavailable, so fall back to OpenGL.
 */
internal object SharedFilamentEngine {
    private var engine: Engine? = null

    /** Main thread only. */
    fun acquire(): Engine = engine ?: build().also { engine = it }

    private fun build(): Engine = runCatching {
        Engine.Builder().backend(Engine.Backend.VULKAN).config(engineConfig()).build()
    }.getOrElse {
        Log.w("AvatarRenderer", "Vulkan engine unavailable, falling back to OpenGL", it)
        Engine.Builder().config(engineConfig()).build()
    }
}
