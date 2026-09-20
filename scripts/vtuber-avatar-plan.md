# VTuber avatar (face-tracked VRM) — design & phased plan

> **Historical design log** — this records how the feature was planned and built. For the **current**
> shipping state — the Zundamon VRM and the MediaPipe face model **bundled** in the APK via Git LFS, no
> downloads — see [`../feature/avatar/README.md`](../feature/avatar/README.md) and
> [`../README.md`](../README.md).

**Status:** Phases 0, 0b, 1, 2 and 3 **done** (2026-09-19) — the whole face-only feature is in: tracking HUD
(verified on the Pixel 8a), Filament/VRM render, and **VTuber mode on Go Live** (avatar as RootEncoder's video
source, verified on the emulator; see §9). Open follow-ups are listed at the end of §9.
**Goal:** stream a face-tracked 3D VRM humanoid avatar instead of the camera, from the existing
**Go Live** screen. Pure Android, no Unity/web.

---

## 1. Verdict

Feasible on pure Android. The originally-proposed chain
`CameraX → MediaPipe → Kalidokit(Kotlin port) → VRM runtime(port VRMKit) → Filament` works, but two
links change after research:

1. **Skip the Kalidokit port for a face-only v1.** MediaPipe **Face Landmarker** (Tasks Vision) already
   emits, per frame, **52 ARKit-style blendshape coefficients** *and* a **4×4 facial transformation
   matrix** (head pose). Kalidokit exists to derive rig params from *raw* landmarks — MediaPipe hands
   us blink/jaw/mouth/brow/eye directly, and head rotation from the matrix. We only need a small
   **ARKit-52 → VRM-expression** map. Kalidokit becomes relevant only if we later add **full-body pose**
   (from MediaPipe Pose landmarks); defer it.
2. **Do not port VRMKit (iOS/SceneKit).** VRM is glTF 2.0 + extensions; **Filament `gltfio`** already
   loads the mesh, skeleton and **morph targets**. We write only a thin VRM-semantics layer (humanoid
   bone map + expression presets) on top and drive it per frame. Far less than a renderer port.

Biggest real risk is **sustained real-time performance** (tracking + skinned render at 30 fps), which
Phase 0 must measure on the physical Pixel 8a — the emulator's GL is unreliable for this.

---

## 2. How it plugs into THIS app (the load-bearing constraint)

Today the publish path is RootEncoder `GenericStream` with a `Camera2Source` (video) + `MicrophoneSource`
(audio), previewed to a `TextureView` via `getGlInterface()` (see `feature/publish/PublishController.kt`,
`CameraPreview.kt`). For a VTuber stream the **avatar must become the video source**, not an overlay:

```
        ┌─────────────────────────── offscreen (viewers never see this) ──────────────────────────┐
CameraX ImageAnalysis ─► MediaPipe FaceLandmarker ─► FaceTrackingResult                            │
  (YUV frames, back-        (blendshapes[52] +          (blendshapes + headMatrix)                 │
   ground executor)          transform matrix)                    │                                │
                                                                   ▼                                │
                                              ARKit52→VRM expression + head-bone mapper (pure Kotlin)│
                                                                   │                                │
                                                                   ▼                                │
                                     Filament: setMorphWeights(...) + head/neck bone rotation        │
                                                                   │                                │
                                                    Filament renders VRM ► offscreen Surface         │
        └──────────────────────────────────────────────────────────────────────────────────────────┘
                                                                   │
                              ┌────────────────────────────────────┴───────────────────┐
                              ▼                                                          ▼
              RootEncoder custom VideoSource                             Compose preview (same Surface/
              (replaces Camera2Source) ─► H.264 ─► RTMP                   TextureView) on Go Live
```

- **Camera is for tracking only** (CameraX `ImageAnalysis`, offscreen). Viewers see the avatar.
- **Filament renders to an offscreen `Surface`** (SwapChain from a `SurfaceTexture`), and that texture
  is fed to RootEncoder as a **custom `VideoSource`** in place of `Camera2Source`. This is the single
  clean seam and preserves the existing flash-free preview-swap design (see
  [[virtualtwitchdroid-publish-mini-flash]] memory — do not disturb `PublishController`/`CameraPreview`
  transitions; add a *parallel* source that Go Live can switch to via a "VTuber mode" toggle).
- Audio (`MicrophoneSource`) is unchanged.

**Open question to confirm during Phase 3:** RootEncoder 2.8.1's exact API for a custom `VideoSource`
that consumes an app-owned `Surface`/OES texture (candidates: a `VideoSource` subclass, or feeding the
GL interface). Must be validated against the pinned 2.8.1, not docs for a newer version.

---

## 3. Module & dependency layout (proposed, not yet added)

New module **`:feature:avatar`** (com.android.library, Hilt, Compose) — depends on `:core:designsystem`,
`:core:common`. It must **not** depend on `:feature:publish` (architecture guard forbids feature→feature,
enforced by `ArchitectureGuardTest`). The publish↔avatar hand-off (the shared `VideoSource`/`Surface`)
therefore lives behind an interface owned by a shared layer:

- Option A (preferred): a small **`:core:media`** (or extend `:core:common`) exposing an
  `AvatarVideoSource`/surface-provider abstraction that `:feature:publish` consumes and `:feature:avatar`
  implements — composed at `:app`. Keeps both features → core only.
- The pure mapping logic (ARKit→VRM, head pose) goes in `:feature:avatar` (or `:core:model` if shared),
  device-free and unit-tested.

**Dependencies to add later (record here; add via `gradle/libs.versions.toml`):**

| Purpose | Artifact | Notes |
|---|---|---|
| Face tracking | `com.google.mediapipe:tasks-vision` | AAR (not a Gradle plugin) → OK on AGP 9.3.2/JDK 25. Needs the `face_landmarker.task` model asset. Enable `outputFaceBlendshapes` + `outputFacialTransformationMatrixes`. Prefer the **GPU delegate**. |
| Camera frames | CameraX `camera-core`/`camera-camera2`/`camera-lifecycle` | `ImageAnalysis` only (no PreviewView for the avatar path). |
| Render | `com.google.android.filament:filament-android` + `gltfio-android` + `filament-utils-android` | GLES 3.0 (minSdk 24 OK; gate on availability). |
| VRM | none — parse `VRMC_vrm`/`VRM` JSON ourselves | glTF geometry via `gltfio`; only the extension semantics are hand-written. |

**Toolchain caveats:** all the above are AARs → expected to resolve (only Gradle *plugins* like Detekt
broke on this toolchain — see [[virtualtwitchdroid-build-toolchain]]). Verify resolution in Phase 0 with
`--rerun-tasks` once. Expect a **notable APK-size increase** (MediaPipe runtime + `.task` model +
Filament + a VRM asset) — measure and decide on `apk`-split / on-demand model download.

---

## 4. The tracking → rig mapping (the reusable core)

MediaPipe blendshape category names are **ARKit's 52** (`eyeBlinkLeft`, `jawOpen`, `mouthSmileLeft`,
`browInnerUp`, …). VRM 1.0 (`VRMC_vrm`) has fewer **expression presets**. The v1 mapping we care about:

| VRM preset | Driven from ARKit blendshape(s) |
|---|---|
| `aa` (mouth open) | `jawOpen` |
| `ih` | `mouthStretchLeft`/`Right` (approx) |
| `ou` | `mouthPucker` |
| `ee` | `mouthSmileLeft`+`mouthSmileRight` (approx) |
| `oh` | `mouthFunnel` |
| `blink` / `blinkLeft` / `blinkRight` | `eyeBlinkLeft`, `eyeBlinkRight` |
| `lookUp/Down/Left/Right` | `eyeLookUp/Down*`, `eyeLookIn/Out*` (or use VRM `lookAt` + gaze) |
| `happy` | `mouthSmile*` + `cheekSquint*` |
| `angry` | `browDown*` |
| `sad` | `browInnerUp` + `mouthFrown*` |

Notes:
- Many VRoid/VSeeFace VRMs ship **ARKit-named custom expressions** in `VRMC_vrm`; if present, drive
  those near-1:1 and skip the preset approximations. Detect and prefer them.
- Apply **smoothing** (one-euro or EMA) + clamping; MediaPipe output is jittery. Config the low-pass
  cutoff so fast mouth movement isn't over-smoothed.
- **Head pose:** decompose the 4×4 facial transform matrix to a rotation; convert MediaPipe's
  right-handed/camera space to VRM's (glTF) space (Y-up, -Z forward) — **document the axis flips**;
  getting this wrong = mirrored/upside-down head. Feed to head/neck humanoid bones (optionally split
  head vs. neck for a natural look). Mirror horizontally for a "selfie" feel (front camera).

This mapper is **pure Kotlin, device-free, and fully unit-testable** — it's the piece worth landing
first regardless of the rest, and the natural target of a guard/spec test.

---

## 5. VRM specifics (Phase 2)

- Support **VRM 1.0** (`VRMC_vrm`) first; optionally **VRM 0.x** (`VRM`, different node-index humanoid
  map + `blendShapeMaster`). Parse from the GLB's JSON chunk.
- Needed from the extension: **humanoid bone → glTF node** map (at least head, neck, spine, eyes),
  **expression presets → morphTarget-bind / material-bind**, `lookAt`, `firstPerson`.
- **Spring bones** (hair/cloth physics, `VRMC_springBone`) — **defer**; static is fine for v1.
- Licensing: VRM models carry usage/redistribution meta (`VRMC_vrm.meta`). Ship only a model licensed
  for this use (e.g. a permissively-licensed VRoid sample), or let the user import their own. **Do not
  bundle a model of unclear license.**

---

## 6. Performance & threading budget (must hold on the 8a)

- CameraX `ImageAnalysis` on a dedicated executor; `STRATEGY_KEEP_ONLY_LATEST` to drop frames under load.
- MediaPipe in **LIVE_STREAM** mode with the **GPU delegate**; target ≤ ~20 ms/frame inference.
- Downscale the analysis resolution (e.g. 480–640 px) — landmarker doesn't need full res.
- Filament renders on its own thread; decouple render fps from tracking fps (interpolate the rig toward
  the latest target so render stays smooth if tracking stutters).
- Budget: **30 fps end-to-end** on the 8a with headroom for the H.264 encoder. Phase 0 must print a
  measured tracking-fps/inference-ms HUD; if it can't hold ~30, revisit (CPU delegate, lower res, or a
  lighter model).

---

## 7. Phasing — each phase independently gate-able (`scripts/ai-dev-loop.sh`)

Every phase: smallest change → gate → verify independently (unit test or ARTEMIS/device observation,
never "looks right") → adversarial review for non-trivial diffs → report. New rules get a
zero-dependency **guard test** in the house style (like `NoHardcodedUiStringTest`,
`NoHardcodedFontSizeTest`, `ArchitectureGuardTest`).

- **Phase 0 — Tracking spike + mapper.** New `:feature:avatar`. CameraX `ImageAnalysis` → MediaPipe
  FaceLandmarker; on-device HUD showing top blendshapes, head yaw/pitch/roll, inference-ms, fps. Ship the
  **unit-tested ARKit-52→VRM mapper + matrix→euler head pose** (pure Kotlin).
  *Acceptance:* mapper unit tests (fail-before/pass-after) green; on the **8a**, moving your face moves
  the HUD values and it sustains ~30 fps (ARTEMIS/manual + `runtime-scan` clean). Proves deps resolve on
  the toolchain and perf is viable **before any Filament work.**
- **Phase 1 — Avatar render.** Filament + `gltfio` load a GLB; drive morph weights + head bone from the
  Phase-0 stream onto a Compose surface (not yet the encoder).
  *Acceptance:* instrumented/host check that the Filament view initializes + a screenshot on the 8a shows
  the avatar mimicking blink/mouth/head; `runtime-scan` clean (watch for GL leaks).
- **Phase 2 — VRM semantics.** Parse `VRMC_vrm` (bone map + expression presets, prefer custom ARKit
  expressions); correct rigging + lookAt.
  *Acceptance:* unit tests on the VRM parser (bone/expression resolution from a fixture GLB); device visual.
- **Phase 3 — Publish integration.** Filament output → RootEncoder custom `VideoSource`; a "VTuber mode"
  toggle on Go Live switches source camera↔avatar without disturbing the existing preview-swap.
  *Acceptance:* device — go live in VTuber mode, confirm the avatar reaches the RTMP output (record/ingest
  check); `crash-scan`/`leak-scan` clean across camera↔avatar↔mini lifecycle churn.

---

## 8. Open questions to resolve before / during build

1. **Model source & license** for a bundled default VRM (or import-only?). : https://github.com/madjin/vrm-samples downalod one model for test bundle in our app
2. RootEncoder **2.8.1** custom-`VideoSource`-from-`Surface` API shape (validate against the pinned version). yeah custom it even needed in this feature
3. Ship the MediaPipe `.task` model **in-APK vs. on-demand download** (size trade-off). in-apk
4. Front-camera **mirroring** convention for the streamed avatar. yes
5. Minimum device policy (GLES3 + perf floor) — feature-flag/hide VTuber mode on devices that can't hold fps. yes add flag
6. Full-body pose (and thus a real **Kalidokit** need) — in scope later, or face-only indefinitely? face-only

---

## 9. Progress log

**Phase 0 — rig core (done).** `feature/avatar/…/rig/`: `ArkitBlendshape` (MediaPipe's 52), `VrmExpression`
(VRM 1.0 presets), `FaceRigMapper` (the §4 table; `mirror` swaps Left/Right), `RotationMath` +
`HeadPoseSolver` (column-major 4×4 → yaw/pitch/roll with `R = Rz·Ry·Rx` in the face frame Y-up, +Z toward
the camera, so **+X is the subject's LEFT**; **positive yaw = face toward the subject's left, positive
pitch = face down, positive roll = crown toward the subject's right**; the selfie mirror and a VRM-0.x
model each negate yaw+roll), `FaceRigSmoother` (EMA). Unit tests include matrix round-trips **and a
hand-written column-major literal** (a transposed layout cannot pass by symmetry).

**Phase 0b — tracking spike (done).** `MediaPipeFaceTracker` (tasks-vision **0.10.35**, LIVE_STREAM,
blendshapes + transform matrix, GPU→CPU fallback), `CameraTrackingEffect` (CameraX **1.6.2**
`ImageAnalysis`, RGBA_8888, 640×480, KEEP_ONLY_LATEST, front/back lens toggle; frames are rotated upright
but **not** mirrored — mirroring happens in rig space, front lens only), `AvatarViewModel`, the HUD
(`AvatarScreen`/`AvatarHud`), an **Avatar** bottom tab. Model `face_landmarker.task` (3.76 MB) bundled
in-APK. Both AARs resolved fine on AGP 9.3.2 / Gradle 9.5 / JDK 25.
*Measured on the Pixel 8a (real face, back lens):* **face detected, 29.3–30.0 fps, 24–25 ms inference,
GPU delegate**; head yaw/pitch/roll and expression weights live; crash-scan + leak-scan clean. The
front lens ran at ~24 fps / 9–13 ms with no face present. Budget (§6, 30 fps) holds; inference is at
the ~20 ms target's edge — headroom for the encoder to be re-measured in Phase 3.
*Gotcha (found on device):* when the raw sensor frame is handed to MediaPipe with
`ImageProcessingOptions.rotationDegrees` (cheaper than rotating the bitmap), the blendshapes are fine
but the **facial transformation matrix is reported in the un-rotated sensor frame** — a 90° sensor
rotation showed up as head roll 94°. The tracker now un-rotates the matrix per frame
(`RotationMath.unrotateAboutZ`, keyed by the submitted timestamp).
*Toolchain notes:* tasks-vision **1.0.0** exists (2026-07) but is unverified here — bump deliberately.
A transient `mergeExtDexDebugAndroidTest` failure appeared once when all five modules' androidTests
built concurrently; it passes in isolation (D8 worker contention, not a dependency clash).

**Phase 1 — Filament render (done, verified on the emulator) + Phase 2 — VRM semantics (done).** `render/AvatarRenderer`
(filament-utils `ModelViewer` on a `TextureView`; per frame: `MorphWeights` → `setMorphWeights`, head bone
`rest · R(pose)` → `setTransform`, `updateBoneMatrices`; camera framed on a model normalized to 1.6 m),
`render/AvatarSurface` (Compose `AndroidView`, Choreographer loop, asset load off-main), and the
**Phase-2 parser core** landed early because driving morphs needs the binds: `vrm/GlbReader`,
`vrm/VrmParser` (VRM 1.0 + 0.x → `VrmModel`: humanoid bones, expression morph binds, morph counts, node
names), `render/MorphWeights`. Filament **1.76.1** resolves on the toolchain. Seed-san bundled with its
licence notice + in-app credit. Unit tests: GLB container (incl. the rewrite below), both parser
fixtures, morph-weight composition, matrix multiply.
*Verified on the emulator via logs:* surface presents (skybox visible), asset loads (147 entities, 5
renderables, all 18 preset binds + head bone resolved by name), textures load, scene populated, camera
eye (0, 1.42, 0.9) with the head bone at (−0.31, 1.35, 0.13) in world — i.e. correctly framed.
*Verified visually on the emulator (back-camera face feed):* neutral pose framed on the head; with a face
in view the avatar's mouth opens to **ee 0.88**, eyes narrow for **happy 0.44**, brows lower (**angry
0.27**), gaze drops (**lookDown 0.25**) — the preset binds drive the correct morph targets; head pose
follows yaw/pitch. Tracking 20–25 fps on the CPU delegate (the emulator lacks GLES 3.1 for MediaPipe GPU
inference). The camera frames the **head bone** (not the bounding box — Seed-san's robot arm skews it).
*Backend:* Filament's **OpenGL** backend on the emulator's Metal-backed GLES translator presents the
skybox but never rasterizes the skinned/morphed meshes (culling off, sample framing — nothing); the
**Vulkan** backend (gfxstream) renders everything. The renderer now prefers Vulkan and falls back to
OpenGL; re-check both on the physical 8a.
*Gotchas found:* (1) UniVRM exports MIME types JSON-escaped as `image\/png`; Filament's cgltf does not
unescape, so no texture provider matches and every texture silently fails ("Missing texture provider for
image\/png") → `GlbReader.withUnescapedSlashes` rewrites the JSON chunk (re-padded, headers fixed).
(2) `ModelViewer.resourceLoader` is private in 1.76.1 and there is no Java `addTextureProvider` — the
providers are registered natively, which is why (1) was the only fix needed. (3) Owning a Filament
engine from a Compose state key is a trap: when the key flips null→instance the old `DisposableEffect`
reads the *new* state and destroys the fresh engine (Filament aborts the process on the next
`flushAndWait`). The renderer's lifetime is now tied to the `TextureView` via `AndroidView.onRelease`.

**Phase 1 fixture decision.** Bundle **Seed-san.vrm** (VirtualCast, **VRM Public License 1.0**, 10.7 MB;
redistribution + modification allowed, **credit required** → ship a credit notice). It is **VRM 1.0**
(`VRMC_vrm` spec 1.0-beta) with all 18 preset expressions bound to the 43-target `head` mesh (node 2),
`head` = node 45, `neck` = node 44, `lookAt.type = expression` (gaze via lookUp/Down/Left/Right — matches
the mapper), 5 skins, PNG/JPEG textures, one embedded buffer, **no `extensionsRequired`** → Filament
`gltfio` will load it (VRMC_* ignored; 10 MToon materials also carry `KHR_materials_unlit`, so they render
unlit). Rejected: `cryptovoxels.vrm` (no morph targets), `meebit_*.vrm` (redistribution prohibited),
`AvatarSample_*` (13–15 MB, VRM 0.x).

**Phase 3 — Publish integration (done, verified on the emulator).** The avatar is now a RootEncoder
**`VideoSource`**: `feature/publish/…/AvatarVideoSource` sizes the pipeline's `SurfaceTexture` to the encoder
(720×1280 portrait), wraps it in a `Surface` and hands it to the app-scoped avatar through the
**`:core:common` `AvatarStreamSource`** interface (implemented by `feature/avatar/…/stream/AvatarStreamController`,
bound in Hilt's `SingletonComponent`, consumed by `PublishController` — no feature→feature dependency).
`PublishController.setVtuberMode()` swaps camera↔avatar with `StreamBase.changeVideoSource` (off the main
thread, live or off-air; RootEncoder stops the old source, starts the new one on the same GL texture, so the
preview-swap, mini-player, encoder and RTMP path are untouched) and `switchCamera()` flips the **tracking**
lens in VTuber mode. The Go Live screen gained a Face toggle (top-right, brand-tinted when on; the voice toggle stacks under it) and the
Avatar tab / toggle are hidden by the **min-device flag** `AvatarSupport` (`isAvatarSupported`: GLES ≥ 3.0 or
any Vulkan hardware level; the emulator reports GLES 3.0 + Vulkan level 1).
*Renderer refactor:* `AvatarRenderer` no longer wraps filament-utils' `ModelViewer` (whose swap chain and
`ResourceLoader` are private) — it owns the Filament scene (engine, renderer, view, camera, 6500 K key light,
gltfio `AssetLoader`/`ResourceLoader`/`UbershaderProvider`, progressive `popRenderables`) and draws into
**either** a `TextureView` (`attachTo`, via `UiHelper`) or any `Surface` (`attachSurface` →
`engine.createSwapChain`). The frames→rig logic moved out of the ViewModel into the shared
`rig/FaceRigPipeline` (staleness + lens-generation guards, selfie mirror) and the CameraX binding into
`tracking/bindFaceTracking(context, lifecycleOwner, tracker, front)`, so the broadcast session and the Avatar
tab drive the avatar identically. The session runs on the **main thread** (Filament + CameraX + a private
`LifecycleRegistry` that is RESUMED for the session and DESTROYED at teardown, which makes CameraX release
the camera before RootEncoder's `Camera2Source` reopens it); `detachOutput()` **blocks** the RootEncoder worker
until the swap chain is destroyed and the `Surface` released, because two producers on one buffer queue is
an error. Rendering is capped at 30 fps on the Choreographer.
*Verified on the emulator (adb, screenshots):* Go Live shows the back-camera face → tap the Face toggle →
the preview shows Seed-san (Vulkan swap chain `720x1280` on RootEncoder's texture, head framed at
(0, 1.42, 0)); switch the tracking lens to the back camera → the avatar mimics the smiling face (mouth open,
eyes narrowed); minimize → the **mini-player shows the avatar**; expand → toggle off → the camera returns;
toggle on → avatar; Avatar tab → its own renderer still works (the broadcast session is released on entry);
back to Go Live → the session is re-created in VTuber mode. `crash-scan`: 0 crashes / 0 ANRs;
`leak-scan`: no leaks. StrictMode advisories: the known RootEncoder-internal `startPreview` `Surface`
(documented in `PublishController`) and a one-time ~130 ms `DiskReadViolation` on main from
`Filament.<clinit>` (`System.loadLibrary`, first Filament use in the process).
*Not verified here:* the RTMP ingest itself — going live from the emulator would publish to the real Twitch
channel whose key is on the screen, so the acceptance stopped at the preview/mini (which show the same GL
render the encoder consumes). Confirm the encoder path with a local recording (`StreamBase.startRecord`) or
a test ingest before relying on it. Also re-check `AvatarRenderer`'s OpenGL fallback on the physical 8a.
*Follow-ups:* (1) the bundled model's licence credit is shown on the Avatar tab only — add it to the Go
Live/VTuber UI if the avatar is streamed publicly; (2) `FaceRigPipeline` starts from the initial no-face
sample, so the first detected frame is already half-blended (fine visually; `smoothing_blendsToward…` test
documents it); (3) `BrowseViewModelTest.loadMore_dedupes…` flaked once in the full unit run (30 s Turbine
timeout; passes in isolation) — a pre-existing race between the test's `loadMore()` and the ViewModel's
private `loading` flag, not avatar-related.
*Adversarial review (Phase 3):* BLOCK → fixed → re-gated green and re-verified on the emulator. (1) Filament's
`Engine.create(backend)` **throws** (never returns null) when Vulkan is unavailable, so the OpenGL fallback was
dead code — now `runCatching`; (2) all off-main `GenericStream` mutations (source swap, camera flip, quality
re-prepare, teardown) are serialized behind one `Mutex` (`changeVideoSource` is a multi-step sequence);
(3) `detachOutput()` also waits (≤1.5 s) for CameraX to report the camera **CLOSED** before RootEncoder
reopens a possibly identical lens; (4) a queued attach is dropped by a later detach (generation counter);
(5) session start-up failures are caught/logged instead of killing the process; (6) the fixed portrait
orientation is re-asserted after a swap (a swap resets the GL orientation config). Consciously left: a fresh
`Camera2Source` after VTuber-off opens the back camera (front-lens choice not restored); no rollback of the
swap when the avatar session fails to start (RootEncoder's attach is fire-and-forget by contract).
The Phase 1/2 review run was stopped before it reported — Phase 1/2 code was only covered by this Phase 3
review where it was touched (`AvatarRenderer`, `AvatarViewModel`, `CameraFrameSource`).

**Upper-body follow-through (done, 2026-09-19).** Face-only tracking has no torso signal, so the upper body
is **head-driven** (the standard VTuber approximation): `rig/UpperBodyRig` distributes the tracked head
rotation down the humanoid chain — `spine` 8/5/8 %, `chest` 12/8/12 %, `upperChest` 10/7/10 %, `neck`
25/25/20 % of (yaw/pitch/roll), the `head` bone the remainder — using a **lagged** head pose for the torso
(`UpperBodyFollow`, time-constant EMA τ = 150 ms, frame-rate independent) so the chest/shoulders (children of
the chest) trail the face by a beat. A bone the model lacks or the renderer can't resolve passes its share
to its nearest present ancestor (Seed-san has no `upperChest` → `chest`); the head remainder is computed
only over driven bones so the face never undershoots. The split is additive in Euler angles: ≤1–2° error
at typical (≲30°) poses, ~15–20° at the 60° clamp with heavy roll — accepted. The head bone is clamped
too, so a fast reversal can't snap it past the neck limit while the torso lags. No hips/arms (that would
need MediaPipe Pose — see §8 Q6).
*Verified:* 8 unit tests (`UpperBodyRigTest`: sum = tracked with/without `upperChest`/`neck`, head-only
model, lag leaves the face on the tracked pose, share-table invariants, time-based lag); emulator VTuber
session logs `driven bones: [spine, chest, neck, head]` and a periodic health line `24–25 fps · face=true ·
chest=(−1.7, 0.85, 0.34) neck=(−1.9, 1.4, 0.31)` — ratios match the table (chest/neck yaw 0.88 = 0.22/0.25).
The emulator's frontal test photo turns the head only ~6°, so the torso motion is numerically confirmed
but visually subtle there; check on the 8a with a real turning head. `crash-scan`/`leak-scan` clean.
*Gotcha:* with a physical device attached the gate's `pick_device` takes it first (it ran the instrumented
phase on the Pixel 8a); two `StreamScreenTest`s failed once there with "No compose hierarchies found" (the
device lost the test activity's focus) and passed on re-run — device flake, not code.

**Camera face-tracking test (2026-09-19).** Avatar face-tracking was originally exercised on the emulator via a
virtual-scene camera fed with Pexels-derived posters/video — an `emulator-face-video.sh` builder, a
`fixtures/emulator-face/` set and an `emulator-camera.md` how-to. That emulator-camera harness was **removed on
2026-09-20**; face-tracking is now verified on a real device's camera — journeys **J6/J7** in
`scripts/artemis-journeys.md`.

**ARTEMIS verification (2026-09-19, emulator, back camera = a `poster-*` still).** **J6**: 7/7 checkpoints — front lens "No face in
view" (18 fps); back lens "Face detected" 14.5 fps · 21 ms, head yaw −12° · pitch −7° · roll 8°, ee 0.70 /
blinkLeft 0.45 / lookDown 0.45 / happy 0.35, avatar visibly turned + winking; back to front → all values 0.
**J7**: 7/7 — camera preview → VTuber toggle switches to the
avatar in ~1 s (icon purple), tracking-lens flip makes the avatar mirror the photo (tilted head, open smile,
squinting eye), the floating mini shows the avatar, expand keeps it, toggle back restores the camera in ~1 s;
"Go Live" never tapped. Renderer health line during J7: 22–23 fps, face=true, torso follow-through active.
`crash-scan` 0/0, `leak-scan` clean.

---

## 10. How the industry animates a VRM's upper body (survey, 2026-09-19) — and where we stand

**Is face-only enough?** For the *mainstream webcam VTuber* look — yes, that is what most apps do. VSeeFace
(the de-facto standard VRM puppeteer) tracks eye gaze, blink, brows, mouth and full head rotation +
position from a webcam and does **no body/arm tracking of its own**: the body "responds passively to head
movement through the avatar's standard animations and idle states"; arms only come from a Leap Motion or from
another app over the **VMC protocol** (webcam hand tracking "doesn't work well enough" per its author).
Kalidokit's reference three-vrm rig does the same trick in code — the solved head rotation drives the neck
at ×1 and the chest/spine at a small fixed ratio, lerped in per frame so the torso eases behind the head.
Our `UpperBodyRig` (spine/chest/upperChest/neck shares of a **lagged** head pose, head keeps the remainder) is
exactly this family, with a stricter invariant (the face lands on the tracked pose).

**What makes it look *natural* is not more tracking — it is the layers on top of face tracking:**

| Layer | Who does it | Signal | Effort here |
|---|---|---|---|
| Head → torso follow-through (turn/lean) | VSeeFace, Kalidokit, Animaze, VMagicMirror | head rotation | **done** (`UpperBodyRig`) |
| Body sway / lean from **head position** | VSeeFace (head position tracking), Kalidokit `Face.position` | translation column of the face matrix | small — the matrix already carries it (planned: roll from lateral offset, pitch from depth, root shift) |
| **Spring bones** (hair, clothes, tails swing with the motion) | every VRM runtime (UniVRM, three-vrm, VRM4U); spec `VRMC_springBone` | none — physics driven by the bones we already move | medium — Seed-san carries **9 springs / 28 joints / 8 colliders** that we currently ignore, so its hair is rigid; a Verlet chain per joint (stiffness, gravity, drag, hit radius, sphere/capsule colliders per the spec) is ~150 lines |
| Idle / breathing / micro-sway | Animaze (scale 1→1.025 breathing on spine…head), VMagicMirror, VSeeFace idle animations | none — procedural noise | small — low-amplitude Perlin/sine on spine pitch + shoulders, gated off while the face moves |
| Auto-blink, lip-sync fallback, eye look-at | VSeeFace, VMagicMirror | timer / mic / `VRMC_vrm.lookAt` | small — Seed-san has `lookAt.type = expression` (we already drive lookUp/Down/Left/Right from gaze) |
| **Arms + hips from pose** | XR Animator (MediaPipe Pose + Holistic, 30 fps body / 60 fps render in a browser; streams VMC), Kalidokit `Pose.solve` (Hips position/rotation, Spine, Upper/LowerArm rotations), ThreeDPoseTracker → VSeeFace | MediaPipe **Pose Landmarker**: 33 landmarks with **world coordinates in metres** (hips origin), lite/full/heavy models | large — second model (~5–30 MB) + tracker, arm retargeting in bone-local space (rest rotations are non-identity on Seed-san), visibility gating, and a second CPU inference on low-end devices |
| Hands / fingers | Leap Motion, MediaPipe Hands | dedicated tracker | large; webcam hands are still considered unreliable for streaming |

**Recommendation (value ÷ effort):** 1) head-position lean + root shift (already scoped); 2) **spring bones** —
the single biggest "alive" factor for a VRM and needs zero extra tracking; 3) idle breathing + auto-blink
when no face; 4) only then MediaPipe Pose for arms/hips (verify on a real device with a person in frame — the
emulator's chest-cropped stills can't exercise elbows/wrists), streaming-quality hands stay out of scope.

Sources: [VSeeFace](https://www.vseeface.icu/) · [Kalidokit](https://github.com/yeemachine/kalidokit) ·
[XR Animator / SystemAnimatorOnline](https://github.com/ButzYung/SystemAnimatorOnline) · [VRMC_springBone 1.0
spec](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_springBone-1.0/README.md) ·
[vrm.dev springbone](https://vrm.dev/en/vrm1/springbone/) · [Animaze VRM animations
(idle/breathing)](https://www.animaze.us/manual/vrmavatar/vrmanimations) · [VMagicMirror](https://malaybaku.github.io/VMagicMirror/en/) ·
[MediaPipe Pose Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker).

**Body lean from head position + spring bones (done, 2026-09-19 — §10 items #1 and #2).**
*Lean:* `HeadPoseSolver.solveOffset` reads the face matrix's translation column (cm; mirror flips X; null when
there is no matrix), `OffsetBaseline` turns it into an offset from the streamer's **neutral** spot (captured on
first detection or after ≥ 2 s of face loss, drifting back with τ = 20 s so a sustained shift stops being a
lean; reset on lens switch), `FaceRigSmoother` smooths it, and `BodyLeanMapper` maps it to a torso lean (roll
−1.2°/cm lateral, pitch 0.6°/cm depth, clamped 15°/12°) plus a root shift (1 cm → 1 cm, clamped 15 cm, bob
damped ×0.5). `UpperBodyRig.distribute(…, lean)` gives the lean to spine/chest/upperChest (0.5/0.3/0.2, folded
to the nearest present bone, dropped with no torso) and the head counter-rotates so the face stays on the
tracked pose; the renderer lags the offset (τ = 250 ms) and adds the shift to the framing root transform.
*Spring bones:* `VrmParser` reads `VRMC_springBone` (colliders sphere/capsule, collider groups, joints, center)
and VRM 0.x `secondaryAnimation` (first-child chains, lossy vs UniVRM's branching + 7 cm virtual tail — noted,
the asset is 1.0); `spring/SpringBoneSimulator` is UniVRM's Verlet form (inertia·(1−drag) + restDir·stiffness·dt
+ gravity·dt → length constraint → sphere/capsule push-out → rotate the bone onto its tail; tails kept in the
`center` node's space so whole-model motion does not swing them; dt clamped to 50 ms) over a `BoneTransforms`
seam; the renderer runs it after the driven bones and before `updateBoneMatrices`. Seed-san: 9 chains →
**17 segments**, 8 colliders; `FrontHairF/G` are dropped (zero-length tips — UniVRM can't swing them either)
and logged.
*Verified:* 108 unit tests (pipeline/baseline/lean/chain split, `SpringMathTest` with hand-computed rotations
and an inverse round-trip with rotation + scale, simulator: gravity hang, stiffness settle, sphere + capsule
colliders, zero-length skip, center vs no-center, and a scaled + rotated two-segment chain with rotated rests
asserted against hand-computed world coordinates); emulator health line `springs=17`, every segment's
`|tail − head| − length` = 0.000, tail travel ≈ 0.1–0.4 mm/frame at rest, `offset≈(−1.0, −0.5, 2.4) cm →
lean pitch 1.4° roll 1.2°, shift −1 cm`; 23–25 fps; crash/leak scans clean.
*Gotcha (found on device, confirmed by the adversarial review):* Filament's `TransformManager.getParent(instance)`
returns the parent **entity**, not an instance — passing it straight to `getWorldTransform` reads an unrelated
node's matrix (segment 1 of every chain looked fine, later segments exploded to tens of metres of tail travel).
`FilamentBoneTransforms` now maps it through `getInstance`. The 108 JVM tests cannot see that class; the
`lenErr` debug line (first 3 spring steps + every 300 frames) is what catches it.
*Review outcome:* BLOCK on that one line (already fixed on device) → PASS otherwise: math verified with an
independent probe; follow-ups applied — regression tests for the rotation composition, nullable head offset so
a matrix-less frame can't become neutral, dropped-chain logging, scratch buffers for the Filament reads,
`SpringMath.multiply` delegating to `RotationMath`, named thresholds. Declined for now: full scratch-buffer
vector math (~700 small allocations/frame, ART copes; revisit if the broadcast frame janks), UniVRM 0.x
branching/virtual tails (no 0.x asset), removing the `modelFacesNegativeZ` solver option (kept, test-covered).

**TEMPORARY asset swap → `sayo.vrm` (2026-09-19, local test only).** `BUNDLED_AVATAR_ASSET` and
`avatar_model_credit` point at 小夜SAYO (OmOti, **VRM 0.x**, CC BY-NC-SA, explicitly-licensed persons only,
commercial disallowed — see `assets/avatar/sayo.LICENSE.txt`; **revert to Seed-san before any distributed
build**). Pulled from a connected Android device (`/sdcard/Download/sayo.vrm`, 26.9 MB). First real exercise of the 0.x paths,
which found two things:
1. **gltfio refuses skins with > 256 bones** (`PreconditionPanic … reason: bone count > 256` in
   `AssetLoader::createAsset`, a native abort). VRoid/UniVRM 0.x exports bind every mesh to the whole skeleton
   (101 skins × 262 joints) although each mesh uses 2–120. New tool `scripts/vrm-prune-skins.py <in> <out>`
   prunes each skin's joints to the used subset (remaps `JOINTS_0` in place, trims the inverse-bind matrices,
   re-serializes the JSON chunk, no buffer growth) — lossless; the bundled `sayo.vrm` is the pruned file (max
   120 joints/skin). Expect this for any VRoid model.
2. **The VRM 0.x head/torso conversion was wrong** (never exercised before): a −Z-facing rig is our frame
   turned 180° about Y, so **pitch and roll flip, yaw does not** (`HeadPose.forModelFacingNegativeZ`) — the
   old code negated yaw + roll (the *mirror* rule) and would have nodded a 0.x model upward. The renderer now
   also turns the root 180° in `frameModel` so a 0.x model faces the camera, converts `tracked` and the lean's
   torso angles to the model frame, and leaves world-frame quantities (root shift) alone.
`BundledAvatarAssetTest` now parses whatever asset is bundled with the production parser (torso bones named
uniquely, `aa`/blink drivable, morph indices in range, ≥ 1 spring segment, GLB rewrite well-formed).
*Verified on the emulator:* 364 entities / 101 renderables, all five torso bones (incl. `upperChest`), 68
spring chains / 117 segments / 28 colliders from `secondaryAnimation` (bust, cat ears, skirt…), every
segment at zero length error; Sayo faces the camera and mirrors the poster face (`ee 0.68`, `blinkLeft 0.45`,
head −12°/−7°/8°). Not yet driven: **eye-bone look-at** (`lookAt = Bone`; the model has no look* morphs) and
0.x branching spring chains. *Perf caveat:* on the emulator (software Vulkan) this 101-renderable model drops
the CPU face tracker to ~4 fps and rendering well below 30 — expect a real GPU to be fine, but it is also a
hint that a model-complexity budget (or merging meshes) matters for low-end devices. Crash/leak scans clean.
*Review of the swap (PASS, fixes applied):* `sayo.vrm` moved to **`src/debug/assets`** so no release APK
carries the non-redistributable model (verified: release APK has only Seed-san; debug has both) and
`loadVrm` falls back to `FALLBACK_AVATAR_ASSET` (Seed-san) when the configured asset is absent from a build
variant; `BundledAvatarAssetTest` now **fails** (not skips) on a missing asset, checks both the configured and
the fallback asset, and enforces gltfio's **≤ 256 joints per skin** + inverse-bind-matrix counts; the
`modelFacesNegativeZ` flag was removed from `HeadPoseSolver` (the renderer owns model facing — one conversion,
no double flip); VRM 0.x collider offsets and gravity directions are **Z-negated** (Unity left-handed → glTF,
as UniVRM's migration and three-vrm do); `vrm-prune-skins.py` asserts accessor uniqueness / single buffer / no
sparse / in-range joints and strips stale `min`/`max`. Its output for Sayo is byte-identical to the bundled file.

**Relaxed arm pose + idle motion (done, 2026-09-19 — §10 item 3).** VRM files store the humanoid in a
**T-pose**; at load the renderer now aims each upper/lower arm at a world-space direction (`rig/ArmRestPose`:
hanging down 12° out, forearm 8° out and 15° forward) with `spring/BoneAim.aimLocal` — the spring bones'
"rotate the bone from its rest direction onto a target, express it locally" step, so it is rig-agnostic (works
for Sayo's identity rests and Seed-san's Blender-style rotated rests, and for either facing; the "outward"
side is measured from the rig, not assumed). Per frame `rig/IdleMotion` (pure function of time) adds
breathing (chest pitch ±0.8°, shoulders ±1.5°, 4 s), a slow arm sway (±1.2°, 7 s) and a shoulder lift with
the head tilt (0.3°/°), applied about **world Z** via `BoneAim.rotateInWorld` so left/right and facing never
matter; the lower arm rides on the upper arm. Springs are set up *after* the arms are posed so sleeves start
in the posed position. Tests: `BoneAimTest` (aim under a rotated + scaled parent with rotated rests keeps
translation/length and lands on the target; chain top-down; world-axis rotation), `ArmPoseTest` (A-pose
targets mirrored per side, elbows forward; idle motion periodic, bounded, zero-mean, shoulders up on the
inhale). Verified on the emulator with Sayo: `arms posed: [shoulders, upper, lower ×2] outward={LEFT=1, RIGHT=−1}`,
arms hang along the body in the screenshot, face still tracked, 68 spring chains intact; scans clean.
*Gotcha:* instrumented Compose tests and an adb-driven journey must not share the emulator at the same time —
an `am force-stop` during `connectedDebugAndroidTest` fails the test with "No compose hierarchies found".
*Review of the arm pose (PASS, fixes applied):* the math (world-axis rotation `Pᵀ·R·P·base`, minimal-rotation
twist — palms end up facing the thighs, forearm target is flexion not hyper-extension, "+Z = forward" holds for
0.x after the root turn) was verified independently. Applied: idle motion now runs on a **load-relative clock**
(Long→Float of absolute uptime nanos loses frame resolution after ~39 h); the head-tilt shoulder lift is **capped
at ±4° and counter-rotated on the upper arm** so the clavicle rises while the arm keeps hanging (an uncapped
±18° swung the far arm into the torso); the arm "outward" side falls back to the joint's world X when a rig is
exported already A-posed (near-vertical arm → x sign is noise). Added the −X-side `rotateInWorld` test. Noted,
not done: an `ArmPoser` extracted over `BoneTransforms` would let the renderer's sign conventions be unit-tested.

**Asset swap → `Zundamon_2025_VRM09A.vrm` (2026-09-19, local test only; replaces sayo in `src/debug/assets`).**
ずんだもん（人型）© SSS LLC., VRM 0.x from the Blender VRM add-on 3.9.1, licence "Other" → zunko.jp guideline
(everyone may use, commercial disallowed) — debug-only, never in a release APK. 129 nodes / 3 meshes / 3 skins
(max 126 joints — no pruning needed), 6 PNG textures, full humanoid without `upperChest`, expressions
a/i/u/e/o + blink/blink_l/_r + joy/angry/sorrow/fun (plus many custom ones we don't drive), `lookAt = Bone`
(no look morphs → gaze not driven). Loads and tracks on the emulator (129 entities, arms posed A-pose,
outward LEFT=+1/RIGHT=−1); springs: 3 bone groups → 5 chains / 13 segments / 40 colliders; the two `耳`
(ear) groups are dropped because they are single leaf bones with no child (UniVRM gives such leaves a
7 cm virtual tail — still a known gap of our 0.x import). Scans clean.

**Rig fidelity #7–#9 (done, 2026-09-19): eye-bone look-at, VRM 0.x spring fidelity, MToon flat look.**
1. **Eye-bone look-at.** `VrmParser` now reads the gaze setup into `vrm/LookAt.kt`: 1.0 `VRMC_vrm.lookAt`
   (`type`, `rangeMapHorizontalInner/Outer`, `rangeMapVerticalDown/Up` `{inputMaxValue, outputScale}`) and
   0.x `firstPerson` (`lookAtTypeName` Bone|BlendShape, `lookAt*` curves whose `xRange`/`yRange` are the 1.0
   input max / output scale — UniVRM's migration mapping; the 0.x `curve` is always the identity ramp and is
   ignored); UniVRM defaults (90 / 10° bone, 90 / 1 expression) fill missing maps. `VrmModel.leftEyeNode/
   rightEyeNode` expose the optional humanoid eye bones. `rig/EyeLookAt` turns the tracked `lookLeft/Right/
   Up/Down` weights into per-eye `HeadPose`s the way `VRMLookAtBoneApplyer` does: a saturated coefficient is
   read as a 90° gaze (= the spec's default `inputMaxValue`, so a full glance rotates the eye by the author's
   `outputScale` and never past it); the eye turning toward the nose reads the *inner* map, the other the
   *outer*; down/up read their own. The renderer resolves `leftEye`/`rightEye` for `lookAt = bone` models and
   sets `rest · R(gaze)` after the torso, with the same 0.x pitch/roll flip as the head; expression-driven
   models (Seed-san) keep their morph gaze. *Verified on the emulator (Zundamon, back-camera poster):*
   `lookAt: BONE eye bones=[leftEye, rightEye]`, health line `gaze L/R = yaw 2.6° pitch 4.27°` for
   `lookDown 0.44` (= 0.427 × outputScale 10 — the map, not a coincidence).
2. **VRM 0.x spring fidelity.** `parseSpringsV0` now walks the **whole subtree** under each root bone like
   UniVRM's `VRMSpringBone.SetupRecursive`: every bone points at its *first* child, each non-first child starts
   its own branch (`VrmParser.subtreeChains`, cycle-safe), and every 0.x chain is marked `leafTail` so the
   simulator gives the leaf bone UniVRM's **7 cm virtual tail** (`parent.position + delta.normalized * 0.07`;
   `SpringBoneSimulator.virtualTailSegment`, bone axis = that world direction in the leaf's rest frame). 1.0
   chains are unchanged (the spec's last joint is a tip). Z-flip provenance recorded in the parser doc:
   UniVRM `MigrationVrmSpringBone` applies `ReverseZ` to collider `offset` and `gravityDir`; three-vrm's 0.x
   `VRMSpringBoneImporter` negates the same two. *Verified:* Zundamon 5 chains / **18 segments** (was 13 with
   both `耳` ear groups dropped) / 0 dropped, every segment incl. the ears at `lenErr 0.000`, 35 fps.
3. **MToon flat look.** gltfio has no MToon; what it has is `KHR_materials_unlit`, which draws the base
   colour as-is — the flat half of the toon look and how UniVRM/VRoid already export MToon. `vrm/MToonMaterials.
   ensureUnlit` injects the flag into any MToon material lacking it (0.x `materialProperties[i].shader ==
   "VRM/MToon"`, 1.0 `VRMC_materials_mtoon`), registers `extensionsUsed`, and reports an `MToonSummary`
   (`mtoonMaterials / unlitInjected / outlineRequested`) logged at load; `VrmAssets.prepareForGltfio` applies
   it together with the slash fix through the new `GlbReader.withJson`. Zundamon: 3/3 already unlit, 2 request
   outlines; Seed-san: 10/10. **Not done — needs a design decision + toolchain:** MToon outlines
   (inverted hull), the two-tone shade colour, rim/matcap require a compiled Filament material (`matc` →
   `.filamat` MToon port behind a custom gltfio `MaterialProvider`); the summary keeps the gap visible.
Item #10 (auto-blink + mic lip-sync fallback) was implemented and then **removed on request** — not part of
the codebase. Gate: 135 avatar unit tests + publish green, Spotless/Lint/APK green, instrumented 46/46 on the
emulator; crash/leak scans clean.
*Adversarial review (PASS, fixes applied):* `MorphWeights` now **skips the `look*` morphs on a bone-gaze
model** (a 0.x file with `lookAtTypeName: Bone` *and* authored look blendshapes would otherwise get both
appliers — UniVRM attaches one); the virtual-tail axis math (`affineInverse(jointWorld)`) got a test under a
rotated + scaled parent with rotated rests; the lookAt types moved to `rig/` (the `vrm → rig` dependency stays
one-way) and the pure container rewrite to `vrm/VrmContainer` (the Android `loadVrm` only reads + logs);
`GlbReaderTest` covers a JSON chunk that *grows*. Documented, not fixed: `rest · R` for the eyes (like the head)
assumes an identity rest rotation — true for every 0.x rig and our 1.0 asset, not guaranteed for an arbitrary
1.0 bone-gaze model. Declined: double JSON parse at load (once, off-main), `.float` strictness on malformed
files (consistent with the parser), `FULL_GAZE_DEG = 90` saturation on models with a small `inputMaxValue`.

---

## Asset decision (2026-09-20): Zundamon only, no fallback

`Zundamon_2025_VRM09A.vrm` moved from `src/debug/assets` to **`src/main/assets`** and `Seed-san.vrm` (the VRM
Public License fallback) was deleted; `FALLBACK_AVATAR_ASSET` and the fallback branch in `loadVrm` are gone, and
`BundledAvatarAssetTest` now asserts the one configured asset ships in `main`. The Avatar tab credit reads
「ずんだもん © SSS LLC. · zunko.jp guideline」. **Licence note:** the Zundamon model's zunko.jp terms are
credit-required and **non-commercial / not redistributable**; with it in `main`, every build variant carries it —
acceptable for this local sample, not for a distributed or commercial build (see
`assets/avatar/Zundamon_2025_VRM09A.LICENSE.txt`). The MToon/spring/look-at notes above that mention Seed-san
describe fixtures and history, not a shipped model.

## Refinement pass (2026-09-20): performance and accuracy without library changes

From a read-only review of the live and avatar paths, applied: VOICEVOX back to **4** native threads (8 had crept
in; 4 is the measured optimum); the Avatar tab renders at most **60 fps** (a 120 Hz panel ran rig + springs +
Filament at 4× the tracker rate); `MediaPipeFaceTracker.process/close` are serialised (closing a landmarker
mid-detect is a native crash); the stream session's rig pipeline (mapper, smoother, baseline) runs on
`Dispatchers.Default` and `ArkitBlendshape.mirrored` uses a precomputed key map (52 string concatenations per
frame gone); `populateScene` adds the light entities once; Filament's job system is capped at **2 workers**
(`Engine.Builder().config(jobSystemThreadCount = 2)` — default was cores − 1 = 8, contending with MediaPipe,
the encoder, the Zipformer and VOICEVOX); the avatar session's teardown is split so RootEncoder only blocks on
`releaseOutput()` (frame callback off + swap chain destroyed + surface released, tens of ms) while camera,
tracker and scope close afterwards on main — the 2 s blocking window that could time out and leave a Filament
Engine alive next to a camera-owned BufferQueue (the prime suspect for the one `JNISurfaceTexture` SIGSEGV) is
gone; the session constructor releases the Engine if `attachSurface` throws and creates the tracker first so a
provider failure leaks nothing; lips are sampled one encoder frame (33 ms) ahead of vsync on the stream path and
vowel-only moras reach their shape at the onset rather than 40 ms late.

Deferred (larger refactors, listed in the review): per-frame Bitmap reuse in `CameraFrameSource` (~36 MB/s
churn), scratch buffers in `SpringBoneSimulator`/`SpringMath` and the renderer's per-bone mat4 temporaries,
folding `MouthOverride` into `MorphWeights`, and closing the Avatar tab's tracker on tab exit instead of
`onCleared`.

---

## Avatar model swap → Zunko (2026-09-20)

`BUNDLED_AVATAR_ASSET` now points at **`Zunko_VRM09K.vrm` (東北ずん子)**, pulled from a connected Android device
(`adb pull /sdcard/Download/Zunko_VRM09K.vrm`) into `feature/avatar/src/main/assets/avatar/`, replacing
`Zundamon_2025_VRM09A.vrm`. The renderer is model-agnostic (VRM 0.x/1.0 via `VrmContainer`/`loadVrm`), so this
needed **no renderer/logic code** — only the asset, the `BUNDLED_AVATAR_ASSET` path, and the
`avatar_model_credit` string; `BundledAvatarAssetTest` confirms every driven bone, expression and spring bone
resolves on the new model. The Zundamon voice (VOICEVOX `0.vvm`) is unchanged, so the avatar is now Zunko while
the TTS voice stays Zundamon — both are zunko.jp characters. Licensing is unchanged in posture: zunko.jp
guideline, credit required, non-commercial, not redistributable — bundling it in the APK is redistribution, so
swap in a properly-licensed model before any distributed/commercial build.

**Reverted 2026-09-20:** the Zunko swap was undone — `BUNDLED_AVATAR_ASSET` and the credit are back to `Zundamon_2025_VRM09A.vrm` (ずんだもん). Zunko was verified drop-in compatible but is not bundled; the renderer stays model-agnostic so either is a one-line swap.
