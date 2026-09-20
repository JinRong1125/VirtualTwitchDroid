# :feature:publish

The **Go Live** screen: broadcast the camera (or the VTuber avatar) to Twitch **RTMP**.

- Streams to `rtmp://live.twitch.tv/app/<stream-key>` using **RootEncoder 2.8.1** (`StreamBase`), fed by a
  live CameraX preview that doubles as the encoder surface.
- `PublishController` (app-scoped, Hilt `@Singleton`) owns the stream lifecycle, camera, mic mute, video
  quality, reconnect policy, and the source swaps: camera ↔ **VTuber avatar** video and mic ↔ **Zundamon
  voice** audio (`ZundamonAudioSource` over RootEncoder's `BufferAudioSource`).
- Camera-pane overlay: quality gear + mic (bottom-left), avatar + avatar-voice toggles (top-right), the
  centred Go Live button, and Picture-in-Picture (bottom-right); received chat overlays while live.
- Portrait-locked; system PiP shows only the camera.

Consumes the avatar/voice through seams in `:core:common` (no feature→feature dependency). Never auto-press
"Go Live" from automation — it publishes to a real key.
