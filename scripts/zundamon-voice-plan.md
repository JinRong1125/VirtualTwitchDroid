# Zundamon voice — local Japanese speech → ASR → VOICEVOX TTS → lip-synced VRM (design & phased plan)

> **Historical design log** — this records how the feature was planned and built, and some tables below
> reflect an earlier download-based plan. For the **current** shipping state — all voice models, including
> the VOICEVOX `0.vvm`, **bundled** in the APK via Git LFS so a fresh clone runs with no downloads — see
> [`../feature/voice/README.md`](../feature/voice/README.md) and [`../README.md`](../README.md).

**Status: IMPLEMENTED (2026-09-19)** — `:feature:voice` + seams in `:core:common` + Go Live toggle + Avatar-tab
rehearsal card. Verified on the Pixel 8a (spoken Japanese → recognised → ずんだもん → lips) and on the emulator (TTS,
lips, Go Live audio-source swap). Measured on the 8a (Tensor G3, CPU): Zipformer ASR **RTF 0.05–0.06** (2 threads);
VOICEVOX synthesis **RTF 1.0–1.2 at 4 threads** (1.6–1.75 at 2) → end-of-speech → first Zundamon audio ≈ 2–3.5 s for a
short sentence; engines load in ~1.4 s (VOICEVOX) + ~3 s (Zipformer) after a one-time ~3 s dictionary unpack. Not yet
verified: the broadcast path *while live* (never Go Live from automation) — the `BufferAudioSource` adapter is unit-tested
and the swap/attach/mic-open path is verified off-air. Model files: `scripts/voice-models.sh` (adb push) or the in-app
download (`ModelStore`, sha256-pinned). Earlier revisions of this document are the design; §12 records what was built.
**Goal:** while streaming in VTuber mode, the streamer speaks Japanese into the phone; the app recognises the
speech on device, speaks the recognised text with **VOICEVOX ずんだもん**, and the VRM Zundamon's mouth moves
to *that* voice. Viewers hear Zundamon, not the streamer. **Purely local** — every model runs on the phone; the
network is used only for the RTMP stream (and, once, to fetch model files).
Companion to `scripts/vtuber-avatar-plan.md` (the face-tracked avatar this extends). Survey sources in §11.

---

## 1. Verdict

Feasible on pure Android, with two hard constraints:

1. **The chain is `mic → VAD → Japanese ASR → Japanese text → VOICEVOX`; no translation.** VOICEVOX speaks
   Japanese and the streamer speaks Japanese, so the text passes straight through (an optional, off-by-default
   rule-based "Zundamon-ify" of sentence endings is the only text stage, §2.2). Two Japanese-only ASR models
   trained on the same 35 k-hour ReazonSpeech v2 corpus are available for sherpa-onnx; the **default is the
   ReazonSpeech k2 Zipformer** (≈161 MB int8, fastest, Apache-2.0, CER 6.6 / 8.2 / 9.9) with **NVIDIA
   Parakeet-TDT-CTC 0.6B-ja** (655 MB, CER 6.4 / 7.1 / 9.0,
   punctuated, CC-BY-4.0) as an opt-in accuracy tier for phones with the memory for it. Both drop the licence
   question the multilingual SenseVoice model had. Non-Japanese speakers and any other output language are
   out of scope (§10).
2. **Everything the diagram needs exists as an Android artifact today** — `voicevox_core` 0.17.0 ships an
   Android Java package (`jp.hiroshiba.voicevoxcore:voicevoxcore-android`, arm64 + x86_64) under MIT; the
   Zundamon talk voice is `0.vvm` (59 MB) from `voicevox_vvm`; `sherpa-onnx` 1.13.8 ships an AAR with Silero
   VAD + offline/online recognisers (Apache-2.0). No NDK/C++ work is required (the Java API loads the JNI
   libraries itself).
3. **Design rule A — the mic never reaches the stream, and it has one owner.** RootEncoder's
   `MicrophoneSource` opens `AudioRecord` when the stream starts; the voice pipeline needs the mic
   *continuously* for VAD/ASR. In Zundamon-voice mode the broadcast audio must be **Zundamon's PCM, not the
   mic**, so the mic moves to the voice pipeline and RootEncoder gets a **custom `AudioSource`** fed from the
   TTS output (§3) — RootEncoder's own `BufferAudioSource`, not a home-made ring. Same pattern as the avatar
   replacing `Camera2Source`. (Android would allow two `AudioRecord`s in one app; a single owner is a
   design choice that makes "mic never leaks" provable.)
4. **Hard constraint B — latency is utterance-level, not word-level.** VAD endpointing + offline ASR +
   synthesis lands **~1.5–3.5 s after the streamer stops talking** (§6). This is the "dubbing" experience every
   local re-voicing demo has; it is acceptable *because the avatar's mouth follows the TTS*, so lips and voice
   are always in sync with each other even though both lag the streamer. Streaming ASR would cut the ASR part
   but no streaming Japanese model exists in sherpa-onnx today (§2).
5. **Lip-sync comes for free and exact.** VOICEVOX's `AudioQuery` carries every **mora with its consonant /
   vowel durations** (plus pauses). That is a phoneme-timed viseme track — far better than amplitude
   envelopes — and it maps 1:1 onto the VRM presets the rig already drives (`aa ih ou ee oh`). Zundamon's VRM
   has all five. (This is *not* the mic-RMS mouth fallback that was rejected earlier: it is text-driven and
   timeline-exact.) **Corollary: for the whole Zundamon-voice session the tracked mouth is off** — viewers
   never hear the streamer, so a mouth flapping to the streamer's words 2–4 s before Zundamon's audio would
   look broken; the mouth is closed between utterances and driven only by the viseme track (§5).

Biggest real risk is **CPU budget on the phone**: MediaPipe face tracking + Filament + H.264 encode already
run; adding the ASR model (≈161 MB int8, or 655 MB for the accuracy tier) and VOICEVOX inference on the same
cores must be measured on the Pixel 8a first (Phase 0). The emulator is fine for correctness (both libraries
ship x86_64) but not for timing.

---

## 2. Engine survey — what runs on Android today (2026-09)

### 2.1 Speech recognition (+ VAD): **sherpa-onnx** (chosen)

| Item | Fact | Source |
|---|---|---|
| Version / licence | v1.13.8 (2026-09-10), Apache-2.0 | GitHub releases |
| Android artifact | `sherpa-onnx-1.13.8.aar` (50 MB, arm64-v8a / armeabi-v7a / x86_64 / x86, bundles `libonnxruntime.so` + `libsherpa-onnx-jni.so`). **Not on Maven Central** (only a third-party repack is) → vendor the release AAR under `third_party/sherpa-onnx/` | releases (verified 2026-09-19) |
| Kotlin API | `Vad(assetManager, VadModelConfig(SileroVadModelConfig(...)))` → `acceptWaveform / empty / front / reset`; `OfflineRecognizer(config)` → `createStream()`, `stream.acceptWaveform(samples, 16000)`, `decode(stream)`, `getResult(stream).text`; `OnlineRecognizer` for streaming models | `android/SherpaOnnxVadAsr` sample |
| VAD model | `silero_vad.onnx` (~2 MB); 16 kHz mono, 512-sample windows | sample app |
| Streaming Japanese | **none** in the online-transducer catalogue (no Japanese) → offline recognition per VAD segment | sherpa docs |

**Japanese ASR model choice** (all offline, 16 kHz mono, run through the same `OfflineRecognizer`):

| Model (sherpa-onnx name) | Params / int8 size | Speed (sherpa docs, desktop CPU) | Accuracy (CER) | Punctuation | Licence | Role |
|---|---|---|---|---|---|---|
| **ReazonSpeech k2 v2 Zipformer** — `sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01` (from `reazon-research/reazonspeech-k2-v2`) | 159 M / **≈161 MB** int8 as 4 loose files from Hugging Face (encoder 154.7 + decoder 3.0 + joiner 2.7 + `tokens.txt`; **the sherpa release `.tar.bz2` is 713 MB because it bundles fp32 — do not ship it**) | **RTF 0.054** at 1 thread (fp32 0.082) | 35 k h ReazonSpeech v2. **CER int8 6.6 / 8.2 / 9.9** on JSUT / CommonVoice v8 / TEDxJP-10k (fp32 6.45 / 7.85 / 9.09; Whisper large-v3 7.18 / 8.18 / 9.96; nemo-v2 7.31 / 8.81 / 10.42 — ReazonSpeech v2.1 blog) | **no** (transcripts are unpunctuated: 「…呼びかけています」) | Apache-2.0 | **default** |
| **Parakeet-TDT-CTC 0.6B ja** — `sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8` (from `nvidia/parakeet-tdt_ctc-0.6b-ja`) | 600 M / **625 MiB** unpacked (489 MB `.tar.bz2`) | RTF 0.106 at 2 threads (≈ 2–4× the Zipformer's cost on a phone) | **JSUT 6.4 / CommonVoice 8.0 7.1 / TEDxJP-10k 9.0** (model card) — 0.2 / 1.1 / 0.9 points better than the int8 Zipformer, same 35 k h corpus + a hard-sample fine-tune | **yes** per the model card (the sherpa sample shows only `?` — confirm 、。 on Phase-0 fixtures) | CC-BY-4.0 | opt-in **accuracy tier** (≥ 8 GB phones) |
| SenseVoice small (zh/en/ja/ko/yue) | 234 M / 228 MB | RTF ~0.10 on one A76 | multilingual, not Japanese-specialised | via `use_itn` | FunASR Model License (attribution, commercial terms unclear) | **dropped** with the translation stage |
| Moonshine tiny-ja / base-ja (`sherpa-onnx-moonshine-{tiny,base}-ja-quantized-2026-02-27`) | 27 M / 62 M — 48 / 104 MB archives | docs RTF ≈ 0.12 (hardware unspecified) | tiny-ja CER 17.9 (Fleurs) / 18.3 (CV17) — >2× worse than k2-v2; base-ja unpublished | **yes** (、。) | "other" on HF — unverified | **no** — accuracy |
| Whisper large-v3 / kotoba-whisper (distilled ja) | 0.8–1.6 B / ≥ 1.5 GB | far above real time on a phone CPU | best-in-class | yes | MIT / Apache-2.0 | **no** — cannot share the phone with tracking + render + encode |
| Seen, not needed | ReazonSpeech ja-en bilingual Zipformer (5 k h + 5 k h, `…ja-en-reazonspeech-2025-01-17`), Omnilingual-ASR 300 M CTC (292 MB), Qwen3-ASR 0.6B (879 MB), SenseVoice int8 2025-09-09 (166 MB), a Snapdragon-only QNN ReazonSpeech build | | generalists or smaller corpora | | | no ReazonSpeech k2-v3 / kotoba-whisper export / streaming ja model exists (checked 2026-09-19) |
| Dolphin CTC small (multilingual Eastern langs incl. ja) | ~ 200 MB int8 | n/a on phone | generalist | — | Apache-2.0 | not needed |

Why the Zipformer is the default: it is the smallest and fastest model trained on the full 35 k-hour
ReazonSpeech corpus, its published CER beats Whisper large-v3 at a tenth of the size, and it is Apache-2.0. Its one gap — no punctuation — costs little
here: VOICEVOX's Open JTalk front end builds accent phrases itself, and utterance boundaries come from the VAD
segments, so each recognised segment becomes one sentence with a natural pause before the next (§2.2 adds a
sentence-final 。 so VOICEVOX's post-pause and the lips' release fall where the streamer paused). Parakeet is
kept as an opt-in tier because its measured CER and punctuation are the best a phone can run today; Phase 0
measures both on the Pixel 8a (§6); the published gap is 0.2–1.1 CER points, so the tier may not survive Phase 0
(§8). The Zipformer is rated for ~30 s clips (model card; Parakeet states no limit) → the VAD's
`maxSpeechDuration` is set to 8 s — a **soft** cap (sherpa raises the VAD threshold to 0.9 after it, so the
segment ends at the next dip; Kotlin defaults are 5 s / `minSilenceDuration` 0.25 s, so both are set explicitly). Why sherpa-onnx at all: Android's built-in `SpeechRecognizer` is not guaranteed offline nor
language-stable; Vosk has weak Japanese.

### 2.2 Text stage: no translation — pass-through + light normalisation

There is **no machine translation** in this pipeline (dropped by request). The recognised Japanese goes to
VOICEVOX after a pure-Kotlin `TextNormaliser` (unit-tested):
- append 「。」to a segment that ends without terminal punctuation (Zipformer output), so VOICEVOX inserts its
  sentence-final pause and the viseme track closes cleanly;
- collapse repeated fillers / whitespace; drop segments shorter than 2 characters (VAD false triggers);
- **Latin tokens**: ReazonSpeech is trained on TV captions and emits Latin words/acronyms (NHK, YouTube, AI…).
  Open JTalk reads unknown Latin words **letter by letter** ("Cisco" → シーアイエスシーオー); VOICEVOX *ENGINE*
  fixed this with e2k/kanalizer in 2025, but the *core* port (voicevox_core PR #1186) is still open and not in
  0.17.0. Rule: a small katakana lexicon for common tokens (loaded into VOICEVOX's `UserDict`), letter-spelling
  kept for ≤ 4-letter acronyms, longer unknown Latin words dropped — unit-tested from a fixture list.
- numerals need nothing (Open JTalk reads Arabic numerals; core normalises half-width → full-width) — one
  fixture (「2024年」「3人」) guards it;
- **optional, off by default:** rule-based "Zundamon-ify" (sentence-final だ/です → のだ, 私 → ずんだもん…).

Everything the earlier multilingual design needed — language-ID routing, ML Kit, its attribution and its
English pivot — is gone. If a non-Japanese streamer is ever needed, the multilingual ASR + translation
variant is recorded in this file's history and in §10.

### 2.3 Japanese TTS with the Zundamon voice: **VOICEVOX CORE** (chosen)

| Item | Fact | Source |
|---|---|---|
| Version / licence | **0.17.0** (2026-08-13); source *and* prebuilt binaries MIT since 0.16 | GitHub releases / README |
| Android Java package | `java_packages.zip` (23.8 MB) in the release contains a local Maven repo with `jp.hiroshiba.voicevoxcore:voicevoxcore` (desktop) and **`voicevoxcore-android:0.17.0`** (AAR, arm64-v8a + x86_64, `libvoicevox_core_java_api.so` + `libc++_shared.so` in `jniLibs`, loaded via `System.loadLibrary`; depends on gson + `jakarta.validation-api` / `jakarta.annotation-api`). **Does not bundle the ORT `.so`.** Declares **`minSdkVersion 26`** (app is 24 → `tools:overrideLibrary` + feature gated to SDK ≥ 26, or bump minSdk). Not on Maven Central → vendor the repo into `third_party/voicevox/` and add it as a `maven { url = uri(...) }` repository | release assets, unpacked AAR (review) |
| Runtime | VOICEVOX's own ORT build from `VOICEVOX/onnxruntime-builder`, placed in the app's `jniLibs` and loaded through `Onnxruntime.loadOnce()` (must be **this** build, not stock ORT). Pin the version 0.17.0 recommends: **`voicevox_onnxruntime-android-arm64-1.23.2.tgz`** (6.5 MB; x64 7.1 MB) — `Onnxruntime.LIB_RECOMMENDED_VERSION = "1.23.2"`, accepted range 1.17–1.29 (1.17.3, 5.4 MB, still loads) | onnxruntime-builder releases, 0.17.0 notes |
| Dictionary | Open JTalk `open_jtalk_dic_utf_8-1.11` (BSD-3; ~22 MB compressed, ~100 MB on disk); must be a **real directory** — copy from assets (or download) into `filesDir` once, pass the path to `OpenJtalk(path)` | VOICEVOX docs; gaprot.jp Android write-up |
| Voice | `voicevox_vvm/vvms/0.vvm` = ずんだもん talk: **59.3 MB in vvm 0.17.0 (a pre-release, `vvm_format_version=2`)**, 58.2 MB in the stable 0.16.4 (also loadable by core 0.17.0) — pin one and record its sha256. Style ids: ノーマル **3**, あまあま 1, ツンツン 7, セクシー 5, ささやき 22, ひそひそ 38, ヘロヘロ 75, なみだめ 76 (song: `s0.vvm`) | voicevox_vvm README / releases |
| Java API | `Synthesizer.builder(onnxruntime, openJtalk).cpuNumThreads(n).build()`, `loadVoiceModel(VoiceModelFile)`, `createAudioQuery(text, styleId)` → `AudioQuery` (`accentPhrases[].moras[]` with `consonant?`/`consonantLength: Double?` (null for vowel-only moras), `vowel`/`vowelLength`, `AccentPhrase.pauseMora?`; `prePhonemeLength`/`postPhonemeLength`, `speedScale`, `pitchScale`, `outputSamplingRate` = 24 000, `outputStereo`), `synthesis(audioQuery, styleId)` → **WAV bytes** (parse to the `data` chunk; `outputSamplingRate`/`outputStereo` only rewrite the header — no resampling happens inside core); `tts(text, styleId)` one-shot; blocking + async flavours. **No chunked/streaming synthesis in the 0.17.0 Java API** (`render`/`precomputeRender` are Rust-only, hidden; PR #1432 open) | unpacked 0.17.0 sources (review), PR #1432 |
| Terms | Generated audio is usable commercially/non-commercially **with the credit「VOICEVOX:ずんだもん」**; the Zundamon *character* (our VRM) stays under the zunko.jp guideline (non-commercial) | voicevox_vvm README, zunko.jp |

Alternatives considered and rejected for v1: **AivisSpeech / Style-Bert-VITS2** (better prosody, but no
Android runtime and GPU-class inference), **sherpa-onnx TTS** (Kokoro / VITS Japanese voices run on Android
but there is no Zundamon voice — kept as the engine for a future non-Japanese output), **RVC-style
speech-to-speech voice conversion** (keeps the streamer's timing, no ASR, but not real-time on a phone), **VOICEVOX ENGINE over LAN** (not local to the phone; useful only as a dev shortcut).

### 2.4 Playback and encode

The RootEncoder stream (and, optionally, an `AudioTrack` monitor through headphones) consume the same PCM.
VOICEVOX emits 24 kHz mono 16-bit; the stream encoder is configured stereo at `AUDIO_SAMPLE_RATE` (44.1 kHz)
— a small linear-interpolation resampler + mono→stereo duplication (pure Kotlin, unit-tested) feed the
encoder. RootEncoder's audio path is **source-driven**: an `AudioSource` pushes `Frame(byte[] PCM16, offset,
size, timeStampUs)` into the encoder's 80-slot queue; if nothing is pushed the audio track stalls, so silence
must be pushed while idle. RootEncoder 2.8.1 already ships **`BufferAudioSource(bufferCapacityMs, latencyMs)`**
— a ring that its IO coroutine drains in paced 2048-byte chunks, **zero-fills when empty**, stamps
`now − latency`, and exposes `setBuffer(bytes, offset, size): Int` (bytes accepted) — plus `SilenceAudioSource`
/ `NoAudioSource`. The Zundamon source is a thin adapter over `BufferAudioSource`; only the resampler and the
utterance queue are ours.

---

## 3. How it plugs into THIS app (the load-bearing constraint)

```
                    (streamer)                                    VTuber mode today
                        │ 🎤                                          │
      AudioRecord 16 kHz mono  ── owned by :feature:voice ──┐        CameraX ─► MediaPipe Face ─► FaceRig
                        │                                    │              (blink, brows, head, gaze …)
                   Silero VAD  (sherpa-onnx)                 │                        │
                        │ speech segment (0.5–8 s)            │                        ▼
      Japanese ASR  (sherpa-onnx OfflineRecognizer:          │            AvatarRenderer (Filament)
       ReazonSpeech Zipformer, or Parakeet-ja tier)          │           ▲ mouth presets from the viseme
                        │ Japanese text                       │           │ track for the whole session
             TextNormaliser (+ 。, optional Zundamon-ify)     │           │ (anchored to the encoder handoff,
                        │ Japanese text                       │   ┌───────┘  sampled at frameTimeNanos)
             VOICEVOX createAudioQuery ──► AudioQuery ────────┼───┘ (moras → visemes)
                        │ synthesis(styleId = 3)              │
                        ▼ PCM 24 kHz                          │
              ┌─────────┴───────────┐                         │
              ▼                     ▼                         │
        AudioTrack (monitor)   ZundamonAudioSource ───────────┼──► RootEncoder GenericStream
        🔊 headphones          (RootEncoder AudioSource,      │        (audio) ─► AAC ─► RTMP
                                resampled to encoder rate)    │
                                                              └──── mic NEVER reaches the stream in this mode
```

Seams (features never depend on each other — enforced by `ArchitectureGuardTest`; Hilt composes at `:app`):

| Interface (in `:core:common`, `media/`) | Implemented by | Consumed by | Purpose |
|---|---|---|---|
| `SpeechStreamSource` — `supported`, `state: StateFlow<VoiceState>` (idle / listening / recognising / speaking), `lagSeconds`, `start() / stop()`, `setMuted(Boolean)` / `muted`, `attachAudioSink(sink: PcmSink)` / `detachAudioSink()` | `:feature:voice` (`ZundamonVoiceController`, `@Singleton`) | `:feature:publish` | Owns the mic + pipeline; hands the TTS PCM to the broadcast |
| `MouthTrackSource` — `active: StateFlow<Boolean>` (a Zundamon-voice session is on) + `fun sample(uptimeNanos: Long): VisemeFrame` (weights for aa/ih/ou/ee/oh; closed when idle) | `:feature:voice` | `:feature:avatar` | Timeline-exact lip-sync, sampled by the renderer's own frame clock |
| existing `AvatarStreamSource` | `:feature:avatar` | `:feature:publish` | unchanged |

`PublishController` grows a third mode next to `vtuberMode`: **`zundamonVoice`** (only offered while
`vtuberMode` is on and the voice feature reports `supported`), and **owns the pipeline's lifetime**:

- `audioSourceFor(zundamon)` is used by both `initialize()` and the toggle (today `initialize()` hard-codes
  `MicrophoneSource()` and `micSource()` casts to it — both must become mode-aware, or **mute silently
  breaks** in this mode and the persisted mute is not re-applied after re-init).
- Enabling: `changeAudioSource(ZundamonAudioSource)` under the existing `streamMutex` (exactly like
  `changeVideoSource` for the avatar), then `SpeechStreamSource.start()`. RootEncoder only calls
  `AudioSource.start()` from `startStream`/`startRecord`, **never at preview**, so the pipeline must be started
  by the controller on toggle-on (the streamer rehearses off-air; ARTEMIS can verify without going live) —
  the `AudioSource` itself only attaches/detaches the `PcmSink`, like `AvatarVideoSource.stop()` →
  `detachOutput()`.
- Disabling, `setVtuberMode(false)`, and `releaseCamera()` all run one critical section: stop the pipeline
  (mic closed, ASR/TTS cancelled), swap back to `MicrophoneSource`. In-flight Zundamon audio is **cut**
  (not drained) — simplest, and the streamer chose to leave the mode. Leaving the Go Live tab must never
  leave the voice feature's `AudioRecord` running.
- The mute button routes to `SpeechStreamSource.setMuted` in this mode: output silenced and ASR paused.

`AvatarStreamController`'s session and the Avatar tab's `AvatarSurface` both sample `MouthTrackSource` in
their Choreographer callbacks (§5).

**Mic ownership rule:** `AudioRecord` is opened by `:feature:voice` only, at 16 kHz mono. Source:
`VOICE_RECOGNITION` when the monitor is off (AEC/AGC tuning of `VOICE_COMMUNICATION` degrades ASR); switch to
`VOICE_COMMUNICATION` only when the monitor speaker is on. When Zundamon-voice mode is off, the voice feature
holds no `AudioRecord` and RootEncoder's `MicrophoneSource` behaves as today. Never both.

**Echo / self-hearing:** Zundamon's monitor playback re-enters the mic and would be re-recognised.
Mitigations, in order: (1) **half-duplex gate** — VAD output is ignored while `speaking` and for 300 ms
after; (2) `VOICE_COMMUNICATION` AEC; (3) monitor playback **off by default** on the stream (the streamer
uses headphones or reads the recognised text on screen). Unit-tested as a state machine.

---

## 4. Module & dependency layout (proposed)

New module **`:feature:voice`** (`com.android.library`, Hilt; Compose only for a small settings sheet) —
depends on `:core:common`, `:core:designsystem`. Sub-packages mirror `:feature:avatar`:

```
feature/voice/
  audio/    MicCapture (AudioRecord → Flow<ShortArray>), Resampler (24k mono → 44.1k stereo), WavPcm (data-chunk parser)
  asr/      SpeechSegmenter (VAD wrapper + half-duplex gate), Recogniser (sherpa OfflineRecognizer; model tier), RecognisedUtterance(text)
  text/     TextNormaliser (terminal 。, filler/short-segment filter), ZundamonStyle (rule-based, optional)
  tts/      VoicevoxEngine (Onnxruntime/OpenJtalk/Synthesizer lifecycle), AudioQuery chunker, UtteranceQueue (backlog policy), VisemeTrack builder
  lipsync/  VisemeTimeline (anchored tracks, sample(uptimeNanos)), pure
  assets/   ModelStore (download / verify sha256 / unpack tar.gz + tar.bz2 into filesDir), ModelManifest
  stream/   ZundamonVoiceController (SpeechStreamSource + MouthTrackSource impl)
  di/
feature/publish/  ZundamonAudioSource (PcmSink adapter over RootEncoder BufferAudioSource) — RootEncoder types stay in publish
```

| Purpose | Artifact | Size on device | Licence |
|---|---|---|---|
| VAD + ASR runtime | `sherpa-onnx-1.13.8.aar` vendored (`third_party/sherpa-onnx/`) | 50 MB AAR → ~15 MB per ABI in the APK | Apache-2.0 |
| VAD model | `silero_vad.onnx` | 2 MB (assets) | MIT |
| ASR model (default) | ReazonSpeech k2 v2 int8: 4 loose files from `huggingface.co/reazon-research/reazonspeech-k2-v2` (encoder/decoder/joiner `.int8.onnx` + `tokens.txt`; no archive to unpack) | ≈161 MB (**downloaded**, not in APK) | Apache-2.0 (credit "ReazonSpeech" in the about screen) |
| ASR model (accuracy tier, opt-in) | `sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8` + `tokens.txt` (`.tar.bz2`) | 655 MB unpacked / 489 MB archive (**downloaded** only when the user picks the tier; replaces the Zipformer on disk) | CC-BY-4.0 (credit "NVIDIA Parakeet-TDT-CTC 0.6B ja") |
| TTS core | `jp.hiroshiba.voicevoxcore:voicevoxcore-android:0.17.0` (vendored local Maven repo from `java_packages.zip`; `minSdk 26`) | ~2 MB + 1.8 MB `libc++_shared.so` per ABI | MIT |
| TTS runtime | `libvoicevox_onnxruntime.so` **1.23.2** (arm64 + x86_64) placed in `jniLibs` | 6.5 MB per ABI | MIT |
| Dictionary | `open_jtalk_dic_utf_8-1.11/` (`.tar.gz`) | ~100 MB (**downloaded** / dev: `adb push`) | BSD-3 |
| Voice | `0.vvm` | 59 MB (**downloaded**) | zunko.jp terms + credit「VOICEVOX:ずんだもん」 |
| Archive unpacking | Apache Commons Compress (tar, gz, bz2 — the platform has no bz2/tar) | ~1 MB | Apache-2.0 |

APK growth ≈ 27 MB per ABI. **`abiFilters arm64-v8a, x86_64` is required** (the sherpa AAR still carries
armeabi-v7a/x86; a 32-bit install would `UnsatisfiedLinkError` on VOICEVOX). Model files ≈ **320 MB** (default
tier; ≈ 815 MB when Parakeet replaces the Zipformer) live in
`filesDir/voice/` via a `ModelStore` that downloads from the official GitHub release URLs (sha256 pinned in a
manifest), unpacks (budget the ~100 MB dictionary unpack into the first-run UX), shows progress on the settings
sheet, and never runs the pipeline until every file verifies. "Purely local" means *inference* is local; the
one-time asset fetch is unavoidable at these sizes (Play Asset Delivery is the store-distribution answer later).

**Toolchain caveats:** all three are AARs / `.so` files → expected to resolve on AGP 9.3.2 / Kotlin 2.3.21
(only Gradle *plugins* broke; see [[virtualtwitchdroid-build-toolchain]]). The VOICEVOX AAR's `minSdkVersion 26`
fails the manifest merge against our `minSdk 24` → `tools:overrideLibrary="jp.hiroshiba.voicevoxcore"` in
`:feature:voice`'s manifest and `supported = SDK_INT >= 26` in the feature flag (or bump minSdk). Two JNI ONNX
Runtimes coexist in the process (sherpa's `libonnxruntime.so` and `libvoicevox_onnxruntime.so`) — different
sonames, sherpa resolves in its own local group, VOICEVOX `dlopen`s by filename → low risk; if a clash appears,
use `sherpa-onnx-static-link-onnxruntime-1.13.8.aar` (38.7 MB, ORT statically linked). Housekeeping: add
`:feature:voice` to `UI_MODULES` in `scripts/ai-dev-loop.sh`; give `:app`'s Hilt graph fake
`SpeechStreamSource`/`MouthTrackSource` bindings for variants without the module; the credit strings go in
resources (`NoHardcodedUiStringTest`).

---

## 5. Lip-sync design (the piece that ties into the existing rig)

`AudioQuery.accentPhrases[*].moras[*]` gives, per mora: `consonant`/`consonantLength`, `vowel`/`vowelLength`,
and each phrase's optional `pauseMora`; `prePhonemeLength` / `postPhonemeLength` pad the utterance. Build a
`VisemeTrack` (pure Kotlin, unit-tested from a fixture `AudioQuery`):

| VOICEVOX vowel | VRM preset | weight |
|---|---|---|
| `a` | `aa` | 1.0 |
| `i` | `ih` | 0.8 |
| `u` | `ou` | 0.8 |
| `e` | `ee` | 0.8 |
| `o` | `oh` | 1.0 |
| `N`, `cl`, pause | all 0 (closed) | — |
| devoiced `A I U E O` (e.g. the す of です) | same preset as the voiced vowel | 0.4 |

Each mora contributes a segment: during the consonant the previous shape holds, then an attack of
min(40 ms, consonantLength) — or a fixed 40 ms when `consonantLength` is null (vowel-only mora) — to the vowel
weight, held for `vowelLength`. **Adjacent vowels crossfade**; the mouth closes only at `N`/`cl`/pauses and
at the end (60 ms release) — a release-then-attack per mora would flap visibly at ~8 moras/s. Times are scaled
by `speedScale`; `prePhonemeLength` offsets the start.

**Clock.** The viewer's A/V sync is decided by encoder timestamps, not by any local speaker, so the track is
anchored to **the uptime at which the utterance's first PCM bytes are handed to the RootEncoder source**
(plus that source's latency margin — `BufferAudioSource` stamps `now − latencyMs`). Choreographer
`frameTimeNanos`, `SystemClock.uptimeMillis` (tracker frames) and RootEncoder's `TimeUtils.getCurrentTimeMicro`
are the same monotonic clock, so the avatar samples `MouthTrackSource.sample(frameTimeNanos)` inside the two
existing Choreographer callbacks (`AvatarStreamController.Session.frameCallback` for the stream,
`AvatarSurface.doFrame` for the Avatar tab) — no ticking flow, no publisher jitter. Each synthesis chunk gets
its own anchor when its bytes are handed over, so a buffer underrun (the source zero-fills) never leaves lips
moving over silence. The optional `AudioTrack` monitor is a *consumer* of the same PCM and is never the clock.

**Override semantics.** While a Zundamon-voice session is `active`, the renderer applies the sampled frame as
the **sole source of the five mouth presets** (closed between utterances); the tracked `jawOpen`-derived
visemes are dropped for the whole session (§1.5), and mouth-carrying emotions are attenuated (`FaceRigMapper`
maps `HAPPY` from `mouthSmile*`; Zundamon's VRM 0.x `joy` deforms the mouth and 0.x has no `overrideMouth`),
so a smiling streamer does not fight the visemes. Blink, brows, head, gaze stay tracked. The override is applied
**after** `FaceRigSmoother` (its EMA would blur the 40 ms attacks), in both render paths. Zundamon's VRM 0.x
`a/i/u/e/o` presets map onto the same `VrmExpression` keys the parser already resolves.

---

## 6. Latency and CPU budget (targets to verify in Phase 0 on the Pixel 8a)

| Stage | Estimate | Notes |
|---|---|---|
| VAD endpoint | 300 ms trailing silence (was 400) | `SileroVadModelConfig.minSilenceDuration`; shorter → more fragments |
| ASR, 5 s utterance — Zipformer int8 | 0.3–1 s | RTF 0.054 on a desktop core → expect 0.06–0.2 on the 8a's big cores; 2 threads |
| ASR, 5 s utterance — Parakeet 0.6B int8 (tier) | 1–3 s | RTF 0.106 at 2 desktop threads → expect 0.2–0.6 on the phone; ~1 GB RAM — **measure before offering the tier** |
| Text normalisation | < 1 ms | pure Kotlin |
| VOICEVOX `createAudioQuery` | 50–200 ms | Open JTalk on CPU |
| VOICEVOX `synthesis`, per ~3 s of speech | 0.6–2 s (RTF 0.2–0.67) | unknown on Tensor G3 — **measure**; the top of the range is marginal for chunking (below) |
| **Total, end of speech → first Zundamon audio** | **~1.5–3.5 s** (default tier) | lips and voice are mutually exact regardless |

**Chunked synthesis** (there is no streaming API): split the `AudioQuery` into sub-queries of 1–3 accent
phrases, with `prePhonemeLength`/`postPhonemeLength` zeroed on inner chunks (else 0.1 s of pad per chunk
becomes audible gaps), synthesise sequentially, hand each chunk to the encoder source as it completes and
anchor its viseme track then. This only helps if **chunk RTF < 1** (chunk N+1 finishes before chunk N ends
playing) — an explicit Phase-0 go/no-go; above it, synthesise whole utterances and accept the extra second.

**Backlog policy** (without it the lag is unbounded): Zundamon's speech is typically *longer* than the
streamer's (VOICEVOX's default rate is slower than lively conversational speech), so continuous talking enqueues faster than
playback drains. `UtteranceQueue` keeps ≤ 2 pending utterances, raises `speedScale` to 1.1–1.3 when the
backlog exceeds ~4 s, and drops the oldest beyond ~8 s; the current lag is exposed (`lagSeconds`) on the
overlay and in the health line. Phase-5 soak: 5 min of continuous speech, lag stays bounded.

**CPU sharing:** face tracking (GPU delegate on device) + Filament + encoder already use ~2 cores' worth. A
coroutine limiter does *not* cap ONNX Runtime's own intra-op pools, so the native thread counts are set
explicitly — sherpa `OfflineModelConfig.numThreads` and VOICEVOX `Synthesizer.builder(...).cpuNumThreads(n)`,
start at 2, tuned in Phase 0 and logged in the health line — and the pipeline stages run on
`Dispatchers.Default.limitedParallelism(1)` so ASR and TTS never overlap each other.
Memory: Zipformer ~250 MB (Parakeet tier ~1 GB), VOICEVOX + dictionary ~250 MB → ~500 MB extra (plausible, unmeasured
— fine on the 8 GB Pixel 8a, will trip the low-memory killer on 4–6 GB phones): load on mode enable, **release
on disable**, read PSS in Phase 0/5. Thermal: expect throttling in a long stream; log per-stage timings in the
same periodic health line the renderer prints.

---

## 7. Phasing — each phase independently gate-able (`scripts/ai-dev-loop.sh`)

| Phase | Deliverable | Acceptance (objective) |
|---|---|---|
| **0 — Spike (1–2 days)** | `:feature:voice` skeleton with both vendored AARs resolving (manifest merge with the `minSdk 26` override); `ModelStore` pulling the dictionary (`.tar.gz`), `0.vvm` and the four Zipformer files (+ Parakeet `.tar.bz2` on demand); a debug screen that synthesises a fixed sentence and recognises a fixed Japanese WAV | Logged on the Pixel 8a: whole-utterance and **per-chunk `synthesis` RTF** (go/no-go for chunking), ASR RTF + CER on a small fixture set for **both** the Zipformer and Parakeet (go/no-go for offering the tier), all at native thread counts 1/2/4; PSS before/after engine load and after release; APK per-ABI size delta; both libraries load and run on the x86_64 emulator; no ORT symbol clash |
| **1 — TTS + lip-sync from text** | `VoicevoxEngine`, `WavPcm`, `VisemeTrack`/`VisemeTimeline`, `MouthTrackSource`, override applied in both Choreographer paths (avatar session + Avatar tab, after the smoother); debug text field on the Avatar tab | Unit: viseme track from a fixture `AudioQuery` (null consonant, crossfades, devoiced weights, speedScale, chunk anchors), WAV data-chunk parsing; emulator: typed text → Zundamon speaks and the health log shows mouth presets following the track, tracked mouth off while the session is active |
| **2 — Mic → VAD → Japanese ASR → Zundamon** | `MicCapture`, `SpeechSegmenter` + half-duplex echo gate, `Recogniser` (Zipformer), `TextNormaliser`, `UtteranceQueue` | Unit: segmenter/gate/queue state machines (incl. backlog speed-up and drop), resampler, normaliser (terminal 。, short-segment drop); emulator (host mic passthrough) / 8a: spoken Japanese → recognised text → Zundamon repeats it; no self-recognition loop with the speaker on |
| **3 — Accuracy tier + style** | Parakeet-ja as a selectable `Recogniser` tier (download on demand, gated by device RAM + Phase-0 numbers), `ZundamonStyle` optional | Unit: tier selection/gating, style rules; device: same fixture set recognised by both tiers, CER + latency logged; model download UX |
| **4 — Broadcast integration** | `ZundamonAudioSource` (adapter over RootEncoder `BufferAudioSource`), `SpeechStreamSource` with `setMuted`, `audioSourceFor(zundamon)` in `initialize()` + toggle, lifecycle (toggle-off / `setVtuberMode(false)` / `releaseCamera()` stop the pipeline), `zundamonVoice` toggle on Go Live (VTuber mode only), credits「VOICEVOX:ずんだもん」+ "ReazonSpeech" (or "NVIDIA Parakeet" on the tier) on screen | Unit: resampler, sink pacing off `setBuffer`'s return, lifecycle state machine; RootEncoder local record (`startRecord` to a file — never Go Live from automation) contains Zundamon audio and no mic, and **lip-open vs audio onset ≤ 1 video frame** when frame-stepping the MP4; ARTEMIS journey toggles the mode off-air and verifies the state text + mute; leaving the tab closes the mic (no `AudioRecord` in `dumpsys media.audio_flinger`); `runtime-scan` clean |
| **5 — Hardening** | thermal/perf tuning, chunking on/off by measured RTF, engine release on mode-off, error surfaces | 20-min soak on the 8a: no ANR, PSS flat, per-stage timings within §6; 5-min continuous-speech soak: lag bounded |

Tests are plain JVM for everything pure (`VisemeTrack`, chunker, resampler, normaliser + Latin-token rule, gates, `UtteranceQueue`); the
engines are behind small interfaces (`Recogniser`, `Speaker`) with fakes, like `FaceTracker`.

---

## 8. Open questions to resolve during Phase 0

1. VOICEVOX `synthesis` speed on Tensor G3 CPU (no GPU path on Android) — whole-utterance and per-chunk RTF
   decide whether chunking is on (§6).
2. Is the Parakeet tier worth offering? Published CER puts it 0.2 / 1.1 / 0.9 points ahead of the int8
   Zipformer (JSUT / CV8 / TEDx) at 4× the size and 2–4× the CPU. Phase 0 measures RTF, RAM and CER on the same
   Japanese fixtures on the 8a; if the gap stays ≈ 1 point, drop the tier and save 655 MB.
3. ~~Does the AAR bundle the ORT?~~ **Resolved (review unpacked the AAR): no** — the app ships
   `libvoicevox_onnxruntime.so` 1.23.2 in `jniLibs`.
4. Two ONNX Runtimes in one process — low risk (different sonames); confirm on a real device in Phase 0, else
   the static-link AAR.
5. Monitor (`AudioTrack`) default: **off** (avoids the echo loop; it is never the lip clock anyway) — the
   recognised text and the current lag are shown on the Go Live overlay instead.
6. Where the ~320 MB of models come from for a store build (Play Asset Delivery vs. in-app download).
7. `minSdk`: override the VOICEVOX AAR's 26 and gate the feature, or raise the app's `minSdk` 24 → 26 (Android
   8.0; share of active devices below it is negligible in 2026 — the simpler path, but a product decision).

---

## 9. Risks

- **CPU contention** with tracking/render/encode → dropped video frames during synthesis. Mitigation: bounded
  dispatcher, native thread caps, chunked synthesis, measure in Phase 0; the Parakeet tier is gated on those numbers.
- **Echo loop** (§3) — designed out with the half-duplex gate; verify with the speaker on.
- **Licence stack**: MIT (core) + BSD (dict) + zunko.jp (voice, credit) + Apache-2.0 (Zipformer) / CC-BY-4.0
  (Parakeet, attribution). The Zundamon *VRM* is non-commercial; the *voice* is commercial-OK with credit. Show
  「VOICEVOX:ずんだもん」and the ASR credit wherever the avatar credit is shown; strings as resources.
- **No punctuation from the default ASR**: mitigated by VAD-segment sentences + the normaliser's terminal 。;
  if pauses inside long segments sound wrong, lower `maxSpeechDuration` or offer the Parakeet tier.
- **Latin words spelled letter by letter** by Open JTalk (no e2k in core 0.17.0): mitigated by the §2.2 lexicon /
  drop rule; revisit when voicevox_core merges the e2k port (PR #1186).
- **Recognition errors are spoken aloud**: a mis-heard word becomes Zundamon's words on stream. Show the
  recognised text on the overlay before/while it is spoken; a later "hold to discard" is cheap to add.
- **Unbounded lag** under continuous speech — designed out by the backlog policy (§6); verified by the soak.
- **API churn**: `voicevox_core` 0.16→0.17 redesigned the Java API; pin 0.17.0 and vendor the repo; vvm 0.17.0
  is a pre-release format — pin the file and its sha256.

---

## 10. Out of scope (deliberately)

**Any translation** (non-Japanese streamers → Japanese Zundamon would need a multilingual ASR such as
SenseVoice plus on-device MT such as ML Kit — surveyed in this file's first revision, dropped by request);
Japanese → other-language output (needs sherpa-onnx TTS voices, not Zundamon); word-level streaming
recognition; emotion-driven VOICEVOX style switching (the eight Zundamon styles are there — a later toggle);
singing (`s0.vvm`); LLM rewriting; PC/LAN VOICEVOX ENGINE.

---

## 11. Sources (survey, 2026-09-19)

- VOICEVOX CORE releases (0.17.0, `java_packages.zip`, android arm64/x86_64 zips, MIT since 0.16): https://github.com/VOICEVOX/voicevox_core/releases
- VOICEVOX CORE README (licence, bindings): https://github.com/VOICEVOX/voicevox_core
- Java API reference (Onnxruntime / OpenJtalk / Synthesizer / VoiceModelFile / AudioQuery / Mora): https://deepwiki.com/VOICEVOX/voicevox_core/3.3-java-api
- Java streaming API PR (not merged/relied on): https://github.com/VOICEVOX/voicevox_core/pull/1432
- VOICEVOX ONNX Runtime Android builds (1.23.2 recommended by 0.17.0): https://github.com/VOICEVOX/onnxruntime-builder/releases
- Zundamon VVM (`0.vvm`, style ids, credit terms): https://github.com/VOICEVOX/voicevox_vvm — terms https://zunko.jp/con_ongen_kiyaku.html
- VOICEVOX CORE on Android via NDK (assets → filesDir, Pixel 6a): https://gaprot.jp/2025/07/28/android-ndkvoicevox/
- sherpa-onnx releases (v1.13.8 AAR variants): https://github.com/k2-fsa/sherpa-onnx/releases
- sherpa-onnx Android build/AAR docs: https://k2-fsa.github.io/sherpa/onnx/android/build-sherpa-onnx.html
- sherpa-onnx Android VAD + ASR sample (Kotlin API used above): https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxVadAsr
- Japanese offline Zipformer (ReazonSpeech k2 v2; sizes, RTF, sample output): https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/zipformer-transducer-models.html — model card (159 M params, Apache-2.0): https://huggingface.co/reazon-research/reazonspeech-k2-v2
- Japanese NeMo Parakeet-TDT-CTC 0.6B for sherpa-onnx (625 MB int8, RTF, punctuated output): https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-ctc/nemo/japanese.html — model card (CER 6.4 / 7.1 / 9.0, CC-BY-4.0): https://huggingface.co/nvidia/parakeet-tdt_ctc-0.6b-ja
- ReazonSpeech project (35 k h corpus, model family, licences): https://research.reazon.jp/projects/ReazonSpeech/ — v2.1 release blog with the CER table (k2-v2 fp32/int8 vs Whisper large-v3 vs nemo/espnet): https://research.reazon.jp/blog/2024-08-01-ReazonSpeech.html
- Moonshine ja models for sherpa-onnx (2026-02-27): https://k2-fsa.github.io/sherpa/onnx/moonshine/models-v2.html
- VOICEVOX Latin-word reading: engine e2k PRs #1694/#1711/#1733 (2025), core port PR #1186 (open): https://github.com/VOICEVOX/voicevox_core/pull/1186
- SenseVoice models (considered, dropped with translation): https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html
- Online (streaming) Zipformer catalogue — no Japanese: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html
- Prior art: zundamon-vc (mic → VOICEVOX, PC): https://github.com/notvalproate/zundamon-vc ; Open-LLM-VTuber (local avatar voice loop): https://github.com/Open-LLM-VTuber/Open-LLM-VTuber

---

## 12. What was built (2026-09-19)

| Piece | Where |
|---|---|
| Seams | `core/common/media/SpeechStreamSource.kt` (`VoiceState`, `PcmSink` with `consuming`, `SpeechStreamSource`: start/stop/say/setMuted/attachAudioSink), `MouthTrackSource.kt` (`VisemeFrame`, `active`, `sample(uptimeNanos)`) |
| Module | `:feature:voice` (library + Hilt, no UI): `VoiceSupport` (SDK ≥ 26 + 64-bit), `assets/` (`VoiceModels` pinned names/sizes/sha256/URLs, `ModelStore` verify/download/unpack into `filesDir/voice`, `TarGz`), `asr/` (`MicCapture` 16 kHz VOICE_RECOGNITION, `JapaneseRecogniser` = Silero VAD + Zipformer via sherpa-onnx, `ListenPolicy` half-duplex gate), `text/TextNormaliser` (terminal 。, fillers, Latin→katakana lexicon), `tts/` (`VoicevoxSpeaker` — ORT from jniLibs, Open JTalk, `0.vvm`, style 3, 4 threads; `UtteranceQueue` backlog policy), `lipsync/` (`VisemeTrack` from mora timings with crossfades, `VisemeTimeline` anchored to uptime), `audio/` (`WavPcm`, `Resampler` 24 k mono → 44.1 k stereo, `MonitorPlayer`), `stream/ZundamonVoiceController` (one singleton behind both seams), `di/VoiceModule` |
| Avatar | `rig/MouthOverride` (voice owns the five presets, mouth emotions ×0.3), renderer samples `MouthTrackSource` at `frameTimeNanos` in both render paths (session + Avatar tab), `AvatarScreen` rehearsal card (Listen / Say / state+lag / credit), RECORD_AUDIO asked on first Listen |
| Publish | `ZundamonAudioSource` (RootEncoder `AudioSource` over `BufferAudioSource`, exposes a `PcmSink`), `PublishController.zundamonVoice` / `setZundamonVoice` (`changeAudioSource` under `streamMutex`, VTuber-mode only), `audioSourceFor` in `initialize()`, mute routed to the voice, `setVtuberMode(false)` and `releaseCamera()` stop the voice; overlay button + status line + credit |
| Vendored | `third_party/voicevox/m2` (voicevoxcore-android 0.17.0 local Maven repo), `third_party/sherpa-onnx/sherpa-onnx-1.13.8.aar`, `feature/voice/src/main/jniLibs/{arm64-v8a,x86_64}/libvoicevox_onnxruntime.so` (1.23.2); `:app` `abiFilters arm64-v8a, x86_64`, `packaging.pickFirsts` for the jakarta NOTICE/LICENSE duplicates |
| Tests | 39 JVM tests in `:feature:voice` (viseme track/timeline, WAV, resampler, normaliser, queue, gate, tar.gz, chunk plan, RTF estimator, clause boundary, controller, support), `MouthOverrideTest`, `ZundamonAudioSourceTest`; gate green; instrumented suites on the emulator |
| Dev tooling | `scripts/voice-models.sh <dir> [serial]` pushes the 7 model files into the app's files dir via `run-as` |

*Adversarial review of the implementation (BLOCK → fixed, 2026-09-19):* (H1) a failed model download left the
voice in an unrecoverable ERROR with `active` stuck true — the avatar's mouth stayed captured; `start()` now
resets ERROR, the listen job is cleared on every exit, and `active` drops when nothing is left to speak.
(H2) `UtteranceQueue` was mutated from three threads unsynchronised — now locked. (H3) lip anchors: the
broadcast anchor ignored audio still queued in RootEncoder's ring and added a +100 ms latency that the ring does
not have (a byte written at uptime W is encoded at ≈ W) — `PcmSink.queuedMs` is tracked by `ZundamonAudioSource`
and the anchor is `write time + queued`; the monitor scheduled lips only *after* its blocking write — now
before. (M1) the echo gate checks segment start *and* end and only reopens when the timeline is silent; (M2)
`ensureActive()` after synthesis so `stop()` mid-synthesis never plays into a torn-down session; (M3) failures
are marshalled to main; (M4) `initialize()` with the voice off stops a rehearsal so two `AudioRecord`s never
coexist, and the Avatar card yields ("In use by Go Live") while a broadcast sink is attached; (M6) the
sherpa-onnx AAR is served from a local Maven layout so `:feature:voice` bundles as an AAR (verified). The
controller is now built from injected seams (clock, dispatchers, engine/mic/monitor factories) and
`ZundamonVoiceControllerTest` covers recovery, say-through-monitor (lips before write), broadcast delivery +
anchor, the echo gate and mute. Declined: releasing engines on disable (kept warm by design; ~500 MB, revisit
for 4–6 GB phones), resumable downloads, a Wi-Fi gate for the one-time fetch. The Phase-4 frame-step A/V check
of a recorded MP4 has not been run. Gradle's heap was raised to 6 GB (`gradle.properties`) and the gate now runs the
connected suites **one module per Gradle invocation** (`scripts/ai-dev-loop.sh`): merging all five test APKs' external
DEX in one JVM ran out of heap once the voice AARs joined the build; per module each fits (46/46 green on the emulator).

**Latency pass (2026-09-19, after the implementation):** measured on the Pixel 8a, VOICEVOX threads 2 → RTF 1.6–1.75,
4 → 1.0–1.4, 6 → 1.5–1.65 (spills onto the little cores) → **4 threads**. VAD trailing silence 400 → **300 ms**.
Chunked synthesis is in, but **gated so it can never stutter**: `ChunkPlan` splits only at a clause boundary
(`ClauseBoundary`: a phrase that already carries a pause mora, or one ending in が・けど・ので・から・し・て・で —
the recogniser emits no 、, so without this rule nothing ever split; a cut there gets a 0.18 s synthetic pause so the
two vocoded pieces meet in silence), never leaves a chunk or a tail shorter than 1 s, and closes a chunk only when
the remaining audio, synthesised as one piece, finishes before the already-scheduled audio has played. The cost
model is `synthesis(d) ≈ 0.7 s + rtf · d`: the per-call overhead is measured, not assumed — the first cut of this
planner ignored it and produced 0.9–1.1 s tail chunks at RTF 2.2–2.4 (≈1 s of dead air). `RtfEstimator` (EMA,
starts pessimistic at 1.5) tracks the *marginal* RTF with the overhead subtracted, × 1.15 safety. Otherwise the
sentence is synthesised whole. Synthesis (TTS worker) and playback (a separate playback worker holding the blocking
sink/AudioTrack writes) are pipelined, so a split actually overlaps.

Measured on the 8a (4 threads, cores uncapped): 「今日は朝から雨が降っていましたが午後になってようやく晴れてきましたね。」
(5.38 s of audio) → 2 chunks, first audio **4.2 s** after the text arrived instead of 6.8 s whole; the 1.82 s tail
synthesised in 2.61 s while the 3.55 s first chunk played, so no gap. Shorter sentences (≤ ~3 s) stay whole: at
RTF ≈ 1 no split can be gap-free, so their gain is only the VAD's 100 ms. Measurement caveat: while the phone charges
over USB its skin sensors reach thermal status 1–3 and the mid/big cores are capped at ~1.1 GHz, which doubles RTF
(1.4–2.1) — check `scaling_max_freq` before trusting a number; the planner then simply stops splitting, as designed.
A first review of the naive version (fixed 1-phrase first chunk, sequential) correctly predicted ~1.4 s of dead air
per boundary at this RTF — that design was replaced by the gated/pipelined one. Also from that review: zero-phrase
utterances complete normally, `stop()` bumps an epoch so queued chunks are dropped, the echo gate is reset on stop,
and the monitor is created/used only on the playback worker.

**GPU / accelerator audit (2026-09-19, Pixel 8a):** everything with a GPU path already runs on it — MediaPipe
Face Landmarker on the GPU delegate (EGL on the Mali-G715; detector 164/164 and landmark 471/471 nodes
delegated, only the small blendshape model on XNNPACK, which is MediaPipe's fixed graph), Filament on Vulkan,
H.264 on the hardware MediaCodec. The two CPU engines have **no usable GPU path on Android today**:
VOICEVOX CORE knows only CUDA and DirectML as GPU devices and its Android runtime reports neither
(`Onnxruntime.supportedDevices()` → cpu only), so `VoicevoxSpeaker` now asks the runtime and picks
`AccelerationMode.GPU` only when one is reported (CPU on every phone; a future runtime with a mobile GPU
path is picked up without a code change); sherpa-onnx's only Android accelerator is NNAPI, and the vendored
1.13.8 AAR is compiled with `__ANDROID_API__ = 21`, so its NNAPI branch is compiled out ("Android NNAPI
requires API level >= 27. Current API level 21 Fallback to cpu!" — the Pixel 8a does ship a `google-edgetpu`
NNAPI driver). `JapaneseRecogniser` gained a `provider` seam with CPU fallback; the default stays `cpu`
(measured identical, RTF 0.05–0.09). Moving VOICEVOX off the CPU would mean building `voicevox_core` against
a runtime with a mobile EP (NNAPI / QNN) — a separate project, not a configuration switch.

**Recogniser flag (2026-09-19, removed 2026-09-20):** an Avatar-tab flag to switch to Android's platform
`SpeechRecognizer` existed for one day; the bench below showed the two engines within a point of each other on
accuracy, and the platform engine needs the on-device Japanese pack (else it silently goes to a network-capable
service), so the user chose sherpa-onnx only. `NativeSpeechSource`/`NativeRestartPolicy`, the `SpeechEngine` seam
and the chips were deleted; the engine-independent refactors stayed (`stopListening`, `accept`).

**VoiceModelFile finalizer fix + normal-case verification (2026-09-20).** `VoicevoxSpeaker` no longer wraps the
VVM load in `.use { }`: that closed the `VoiceModelFile` (dropping its native handle), then VOICEVOX's own
`VoiceModelFile.finalize` dropped it a second time on the null handle and threw `RuntimeException: Null pointer in
rust value` from the finalizer thread (non-fatal, but real). It is now held as a field for the synthesizer's
lifetime and never closed, so the native model drops exactly once, at GC, on a valid handle. Verified on the 8a:
the error, present before, is absent across a 10-sentence run and after forced teardown. Normal-case bench
(`scripts/voice-bench.py --set normal`, results `scripts/voice-bench-normal.md`): 10/10 everyday lines recognised
and spoken, mean CER 7%; the homophone rule did NOT misfire on ordinary verbs (読んだ本 / 済んだこと / 遊んでもらって
all 0%); the only non-zero CER is recogniser spelling (是非/下さい, correct reading) or a loanword mishear
(コメント→ごめん答) — ASR, not our code, and not fixable without a lexicon. One filler (「ちょっと」) was dropped at an
utterance start (VAD onset), noted as an ASR limitation, not chased.

**Fast-start chunking (2026-09-20).** `ChunkPlan` gained a first-chunk target ([FIRST_CHUNK_TARGET_S] = 1.5 s):
the first chunk is closed at the earliest clause boundary once it reaches the target, even when synthesising the
whole rest in one piece would not keep up. The later boundaries then subdivide the rest gap-free; if they cannot
(one long trailing breath group) the single residual gap lands on a clause boundary — a natural breath where the
mouth is closed anyway — and only the first chunk gets this, so there is at most one. Keeps the exact ずんだもん
voice (VOICEVOX unchanged). Measured on the 8a (uncapped) for 「今日は朝から雨が降っていましたが午後になってよう
やく晴れてきましたね」 (5.4 s): first audio **~4.2 s → ~2.15 s**. The very first utterance of a session (RTF
estimate still at its pessimistic 1.5) makes 2 chunks with one ~1 s breath at the 、; from the second utterance the
estimate converges to ~1.0 and it becomes 3 chunks that keep up gap-free. Tune the target up for fewer breaths, down
for earlier audio. From an internet survey (2026): on-device, no drop-in engine (Kokoro, Style-Bert-VITS2) beats
VOICEVOX's ~RTF 1.0 for Japanese while keeping the Zundamon voice; a real step change would need a trained
Matcha-TTS Japanese Zundamon voice (flow matching, RTF ~0.1), which is a separate training + licensing effort.

**Models bundled in the APK (2026-09-20).** All seven model files (~232 MB: encoder 155 MB, 0.vvm 58 MB,
Open JTalk dict tar.gz 24 MB, decoder/joiner/silero/tokens ~6 MB) now live in `feature/voice/src/main/assets/voice/`
and ship in the APK, so a fresh `git clone` builds and launches the voice with **no download and no `adb push`**
(the user's goal). `ModelStore` no longer fetches over HTTP: on first launch it copies each asset into
`filesDir/voice/` (still size + sha256 verified, dict still unpacked there) because the engines need real file
paths — VOICEVOX's `VoiceModelFile`/`OpenJtalk` cannot read an `AssetManager`. `app/build.gradle.kts` sets
`androidResources.noCompress` for onnx/vvm/gz so they are stored raw. Debug APK grows ~196 MB → ~430 MB, and the
device also keeps the extracted copies in filesDir (a deliberate storage-for-simplicity trade; sherpa could read
its own models straight from assets to reclaim ~160 MB, deferred). `BundledVoiceModelsTest` fails the gate if any
asset is missing or the wrong hash. **Repo/Git:** the 155 MB encoder exceeds GitHub's 100 MB/file limit, so the
repo must use Git LFS — the user handles the git/LFS setup. `scripts/voice-models.sh` is now optional (device
refresh only).

**Sherpa accuracy tuning (2026-09-20):** kept greedy decoding — `modified_beam_search` + hotwords (ずんだもん…)
was benched on the 8a and tripled the decode time (RTF 0.05 → ~0.17) with no gain on the name and a broken tongue
twister, so it was reverted. Instead the lexicon-less Zipformer's misreads of the stream's own names are corrected
in `TextNormaliser`: it renders ずんだもん as 〈kanji〉んだ + a もん-sound tail, seen on-device as もん/モン/門/紋 (the
head kanji and the tail both drift run to run: 澄んだ門, 休んだもん, 済んだ紋, 弾んだモン…). The rule maps those four
tails back to ずんだもん (門/紋/モン after んだ are never real words; real homophones like 文/問 are left alone) and
四国[めメ目][たタ][んン] → 四国めたん. It is best-effort, not a fix — an unseen spelling still slips through and a
genuine 「〜んだもん」 colloquialism is rewritten too (fine on a Zundamon stream, wrong for general dictation). The
real fix is a recogniser with a lexicon, i.e. a model/library change. Verified end-to-end on the 8a: raw ASR
「澄んだ門の声で」/「休んだもんです」 → Zundamon spoke 「ずんだもんの声で」/「ずんだもんです」.

**Engine bench (2026-09-19, `scripts/voice-bench.py` → `scripts/voice-bench-results.md`):** 12 sentences (short,
medium, long, and "difficult": proper nouns, technical terms, numbers, loanwords, tongue twisters) spoken by macOS
`say -v Kyoko` at the Pixel 8a, once per engine, with the Avatar-tab card listening; accuracy (CER vs the spoken
text) and device-clock latency from end of speech. Result: both engines recognised 11/12 (neither caught the 0.6 s
「はい」 at 50 % volume in that run); mean CER 11 % (sherpa-onnx) vs 12 % (Android), and most of both engines' error
is normalisation, not hearing — Android writes 「3時15分」/「YouTube と Twitch」, sherpa writes 「YouTube」; the real
misses were sherpa's 「明日は」→「まずは」, 「機械」→「社会」, an extra 「許」, and both engines losing a clause at the
sentence-internal 「。」 pause of 「ずんだもんです。よろしくお願いします」 (sherpa dropped the proper-noun clause, Android
endpointed after it and lost the rest while re-arming). Median end-of-speech → text: sherpa 1.6 s, Android 1.3 s.
The first bench exposed a systematic sherpa defect — the Zipformer dropped the **first word** of 6/11 sentences
(「今日はいい天気ですね」→「いい天気ですね」) although the VAD segment covered it; `SegmentPadding` (0.4 s of silence
before, 0.3 s after each segment) fixed every case except the proper-noun one. TTS/first-audio columns of that
bench are not comparable between engines: the phone was thermally capped (mid cores at 0.7–1.2 GHz) and got
hotter during the Android half.

Deviations from the design: the emulator's microphone is silent (launched with `-no-audio`), so recognition was verified
on the Pixel 8a with macOS `say -v Kyoko` as the speaker; the `.tar.gz` dictionary is unpacked by a small pure-Kotlin tar
reader instead of Commons Compress; the Latin-token rule keeps unknown tokens (Open JTalk spells them) rather than
dropping them; `UI_MODULES` in the gate script is unchanged because `:feature:voice` has no instrumented tests.
