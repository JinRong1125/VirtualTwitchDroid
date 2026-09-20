# :feature:voice

An **on-device Japanese voice changer**: the streamer speaks, and the avatar speaks back as VOICEVOX
**ずんだもん**, lip-synced — entirely local, no network.

Pipeline (`ZundamonVoiceController`, one Hilt `@Singleton` behind the `:core:common` `SpeechStreamSource` +
`MouthTrackSource` seams):

```
mic (AudioRecord) → Silero VAD → ReazonSpeech k2 v2 Zipformer (sherpa-onnx) → TextNormaliser
→ VOICEVOX CORE (0.vvm, style ずんだもん) → PCM → PcmSink (broadcast) | AudioTrack (rehearsal) + viseme lip-sync
```

- **ASR**: Silero VAD + ReazonSpeech Zipformer int8 via the vendored sherpa-onnx AAR (greedy decode, CPU).
- **TTS**: VOICEVOX CORE 0.17.0 with Open JTalk; `ChunkPlan` starts audio on the first accent-phrase chunk
  (RTF-gated, gap-bounded) to cut long-line latency while keeping the ずんだもん voice.
- **Lip-sync**: mora timings from the `AudioQuery` become a viseme track anchored to the audio, sampled by
  the renderer at frame time.
- **Models**: bundled in `assets/voice/` and materialised into `filesDir` by `ModelStore` on first launch
  (sha256-pinned). **All** models ship in the repo (large ones via Git LFS), including the VOICEVOX `0.vvm`,
  so a fresh clone runs with no downloads — see [LICENSES.md](../../LICENSES.md). Credit「VOICEVOX:ずんだもん」
  is always shown.

Half-duplex: recognition is gated while the avatar is speaking (echo). Japanese only, no translation.
