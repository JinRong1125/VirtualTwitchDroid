# :feature:avatar

A face-tracked **VTuber avatar**: a VRM character rendered with Google **Filament**, driven live by the
front/back camera.

- **Tracking**: `MediaPipeFaceTracker` (MediaPipe Face Landmarker, GPU delegate with CPU fallback) turns
  CameraX frames into ARKit-style blendshapes + a head transform.
- **Rig**: `FaceRigMapper` → `OffsetBaseline` → `FaceRigSmoother` map those to VRM expression presets and
  bone poses; a VRM 0.x/1.0 parser (`vrm/`), spring-bone simulator (hair/ears/tail), MToon materials, and
  eye-bone look-at.
- **Render**: `AvatarRenderer` (Filament, Vulkan-first) drives morph weights + bones each Choreographer frame
  (capped ~60 fps); the mouth is owned by the voice's viseme track while it speaks.
- **VTuber mode**: `AvatarStreamController` renders the avatar into RootEncoder's surface so it broadcasts
  instead of the camera. The tab also hosts a voice rehearsal card (Listen / Say).
- The Zundamon VRM is **committed** (Git LFS), so a fresh clone runs with no downloads; the renderer is
  model-agnostic — swap `BUNDLED_AVATAR_ASSET` (see `render/VrmAssets.kt`). Its terms permit redistribution
  with credit — see [LICENSES.md](../../LICENSES.md).

Consumes the voice via `:core:common` seams. Filament runs on the GPU (Vulkan); tracking on the GPU delegate.
