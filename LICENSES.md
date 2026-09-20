# Licenses & third-party attributions

VirtualTwitchDroid is a **personal, non-commercial learning project**. It bundles several
third-party models, libraries and character assets, each under its own license. This file lists every
non-trivial third-party dependency and asset with its **license** and **source URL**, including the character
assets whose terms permit redistribution with credit (see below). Keep the project **non-commercial**.

> The project's own source code is licensed under the **MIT License** ([LICENSE](LICENSE)). This file covers
> third-party material only.

## Character assets (bundled — redistribution permitted under their terms, with credit)

Both character assets are **committed** (large ones via Git LFS) because their own terms allow embedding them
in a redistributed application:

| Asset | Path | Source | Terms |
|---|---|---|---|
| Zundamon VRM avatar (`Zundamon_2025_VRM09A.vrm`) | `feature/avatar/src/main/assets/avatar/` | © SSS LLC., a ずんだもん VRM | [zunko.jp guideline](https://zunko.jp/guideline.html): official 3D models may be redistributed, and the 技術紹介／プログラミング解説 exception covers this project. Do **not** sell the model file itself or claim exclusive use (独占利用). |
| VOICEVOX voice model (`0.vvm`) | `feature/voice/src/main/assets/voice/` | <https://github.com/VOICEVOX/voicevox_vvm> (voicevox_vvm 0.16.4 — the voice model, distinct from VOICEVOX CORE 0.17.0) | 「アプリケーションに組み込んで再配布することができます」 — embedding + redistribution is permitted; requires the credit「VOICEVOX:ずんだもん」(shown at runtime). Its four voices (ずんだもん, 四国めたん, 春日部つむぎ, 雨晴はう) are not in VOICEVOX's restricted list. |

The app shows the mandatory VOICEVOX credit at runtime (voice overlay + Say field) and the avatar credit on the
Avatar tab. **Keep the project non-commercial**; this reading is for a personal, non-commercial programming
project — revisit the terms before any commercial or trademark use.

## Bundled models (redistributable — committed, large ones via Git LFS)

| Model | License | Source URL |
|---|---|---|
| ReazonSpeech k2 v2 Zipformer, int8 (`encoder`/`decoder`/`joiner`/`tokens`) — Japanese ASR | Apache-2.0 | <https://huggingface.co/reazon-research/reazonspeech-k2-v2> |
| Silero VAD (`silero_vad.onnx`) | MIT | <https://github.com/snakers4/silero-vad> (via <https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models>) |
| Open JTalk system dictionary (`open_jtalk_dic_utf_8-1.11`) | BSD-3-Clause | <https://open-jtalk.sourceforge.net/> |
| MediaPipe Face Landmarker (`face_landmarker.task`) | Apache-2.0 | <https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker> |
| `libvoicevox_onnxruntime.so` (ONNX Runtime build shipped by VOICEVOX) | MIT | <https://github.com/VOICEVOX/onnxruntime-builder> |

## Vendored libraries (`third_party/`, local Maven repos)

| Library | Version | License | Source URL |
|---|---|---|---|
| sherpa-onnx (ASR + VAD engine, AAR) | 1.13.8 | Apache-2.0 | <https://github.com/k2-fsa/sherpa-onnx> |
| VOICEVOX CORE Android (Java bindings) | 0.17.0 | MIT | <https://github.com/VOICEVOX/voicevox_core> |

## Gradle dependencies

| Dependency | License | Source URL |
|---|---|---|
| AndroidX (Compose, Activity, Lifecycle, Navigation, CameraX, media3/ExoPlayer, Baseline Profile) | Apache-2.0 | <https://developer.android.com/jetpack/androidx> |
| Jetpack Compose (BOM 2026.09.00), Material 3 | Apache-2.0 | <https://developer.android.com/jetpack/compose> |
| Dagger Hilt 2.60.1 | Apache-2.0 | <https://dagger.dev/hilt/> |
| Kotlin 2.3.21 + Coroutines + kotlinx.serialization | Apache-2.0 | <https://kotlinlang.org/> |
| RootEncoder (RTMP/camera broadcast) 2.8.1 | Apache-2.0 | <https://github.com/pedroSG94/RootEncoder> |
| MediaPipe Tasks Vision 0.10.35 | Apache-2.0 | <https://github.com/google-ai-edge/mediapipe> |
| Google Filament 1.76.1 (VRM/glTF rendering) | Apache-2.0 | <https://github.com/google/filament> |
| OkHttp 5.5.0 / Retrofit 3.0.0 | Apache-2.0 | <https://square.github.io/okhttp/> · <https://square.github.io/retrofit/> |
| Coil 3.2.0 (image loading) | Apache-2.0 | <https://github.com/coil-kt/coil> |
| Turbine (flow testing) | Apache-2.0 | <https://github.com/cashapp/turbine> |
| Spotless 8.10.2 + ktlint 1.6.0 (build tooling) | Apache-2.0 / MIT | <https://github.com/diffplug/spotless> · <https://github.com/pinterest/ktlint> |

## Design & UX provenance (not code)

- UI/UX modeled on **skydoves/twitch-clone-compose** — <https://github.com/skydoves/twitch-clone-compose> (Apache-2.0).
- Watch/chat interaction patterns referenced from the **Xtra** Twitch client — <https://github.com/crackededed/Xtra> (Apache-2.0).
- Module architecture modeled on **Now in Android** — <https://github.com/android/nowinandroid> (Apache-2.0).
- "Twitch" is a trademark of its owner; this project is an unaffiliated, non-commercial clone for learning and
  does not bundle Twitch trademarks or brand assets. Live data requires the user's own Twitch access.

Full license texts are available at each project's URL above.
