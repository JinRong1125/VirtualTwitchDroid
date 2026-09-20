#!/usr/bin/env bash
# OPTIONAL since 2026-09-20: the models are now BUNDLED in feature/voice/src/main/assets/voice/ and shipped
# in the APK, so a fresh clone builds and runs with nothing pushed (ModelStore copies them out of assets on
# first launch). This script only refreshes the on-device copies without a rebuild — e.g. after changing a
# pinned model — by pushing the same files into filesDir/voice.
#
#   scripts/voice-models.sh <dir-with-models> [device-serial]
#
# <dir-with-models> must contain (names as in feature/voice/.../assets/VoiceModels.kt):
#   encoder-epoch-99-avg-1.int8.onnx  decoder-epoch-99-avg-1.int8.onnx  joiner-epoch-99-avg-1.int8.onnx  tokens.txt
#     (https://huggingface.co/reazon-research/reazonspeech-k2-v2 — Apache-2.0)
#   silero_vad.onnx                   (sherpa-onnx asr-models release — MIT)
#   0.vvm                             (voicevox_vvm 0.16.4 — ずんだもん; credit「VOICEVOX:ずんだもん」)
#   open_jtalk_dic_utf_8-1.11.tgz     (Open JTalk dictionary, gzip tar — BSD-3; the app unpacks it)
#
# Files land in the app's INTERNAL files dir (filesDir/voice) via `run-as`, which is what ModelStore reads;
# it still verifies each file's size + sha256 before use. Nothing here needs root.
set -euo pipefail
SRC="${1:?usage: scripts/voice-models.sh <dir-with-models> [serial]}"
SERIAL="${2:-$(adb devices | awk '/\tdevice$/{print $1; exit}')}"
PKG=com.example.virtualtwitchdroid
FILES=(encoder-epoch-99-avg-1.int8.onnx decoder-epoch-99-avg-1.int8.onnx joiner-epoch-99-avg-1.int8.onnx tokens.txt
       silero_vad.onnx 0.vvm open_jtalk_dic_utf_8-1.11.tgz)
for f in "${FILES[@]}"; do [ -f "$SRC/$f" ] || { echo "missing $SRC/$f" >&2; exit 1; }; done
adb -s "$SERIAL" shell run-as "$PKG" mkdir -p files/voice
for f in "${FILES[@]}"; do
  echo "→ $f"
  adb -s "$SERIAL" push "$SRC/$f" "/data/local/tmp/$f" >/dev/null
  adb -s "$SERIAL" shell "run-as $PKG sh -c 'cat /data/local/tmp/$f > files/voice/$f'"
  adb -s "$SERIAL" shell rm "/data/local/tmp/$f"
done
adb -s "$SERIAL" shell run-as "$PKG" ls -la files/voice
echo "done — the app verifies sha256 on first use (unpacks the dictionary once, ~100 MB)."
