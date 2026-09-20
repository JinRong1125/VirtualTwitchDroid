# Vendored VOICEVOX CORE for Android (0.17.0, MIT)

`m2/` is the `voicevoxcore-android` artifact from the official release asset
`https://github.com/VOICEVOX/voicevox_core/releases/download/0.17.0/java_packages.zip`
(local Maven layout; javadoc jar removed). It is not published to Maven Central, so
`settings.gradle.kts` adds this directory as a repository restricted to `jp.hiroshiba.voicevoxcore`.

`feature/voice/src/main/jniLibs/*/libvoicevox_onnxruntime.so` is VOICEVOX's own ONNX Runtime build
(1.23.2, the version 0.17.0 recommends) from `VOICEVOX/onnxruntime-builder`; terms in
`ONNXRUNTIME-TERMS.txt`. The AAR does not bundle it.

Voice model (`0.vvm`, ずんだもん talk) and the Open JTalk dictionary are NOT vendored — see
`scripts/voice-models.sh` (pushes them to the device) and the in-app downloader. Generated audio must be
credited「VOICEVOX:ずんだもん」(https://zunko.jp/con_ongen_kiyaku.html).
