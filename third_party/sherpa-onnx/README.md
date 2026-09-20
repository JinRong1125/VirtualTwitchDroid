# Vendored sherpa-onnx Android AAR (v1.13.8, Apache-2.0)

`m2/com/k2fsa/sherpa/onnx/sherpa-onnx/1.13.8/sherpa-onnx-1.13.8.aar` (with a minimal POM, served as a local Maven repository restricted to its group) is the official release asset
`https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar`
(not on Maven Central). It bundles onnxruntime + the JNI/Kotlin API for arm64-v8a, armeabi-v7a, x86_64, x86;
`:app` filters ABIs to arm64-v8a + x86_64 (VOICEVOX ships only those).

Models are NOT vendored: Silero VAD (`silero_vad.onnx`, MIT) and the ReazonSpeech k2 v2 Zipformer int8
files (`reazon-research/reazonspeech-k2-v2`, Apache-2.0) are pushed or downloaded — see `scripts/voice-models.sh`.
