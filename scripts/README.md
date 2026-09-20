# `scripts/` — the local AI development loop

This project has **no CI**. Quality is enforced locally by a deterministic gate plus device-backed
verification with [**ARTEMIS**](https://github.com/google/artemis) (an autonomous mobile UI agent driven over MCP). These scripts are the
tools of that loop. The end-to-end process is described in
[`../docs/AI_DEVELOPMENT.md`](../docs/AI_DEVELOPMENT.md).

```
request ──▶ tiny spec ──▶ smallest change ──▶ ai-dev-loop.sh (gate)
                                                   │
                                        ARTEMIS verify + observe
                                        (journeys, crash/leak scan)
                                                   │
                                        trace → fix ──▶ report
```

## Entry points

| Script | What it does | Usage |
|---|---|---|
| `ai-task.sh` | Natural-language entry point. You type *what* you want; Claude Code (head-less) turns it into the loop below, following `ai-controller.md`. | `scripts/ai-task.sh "make the Live Channels header teal"`<br>`AUTO=1 MODEL=opus scripts/ai-task.sh "..."` |
| `ai-dev-loop.sh` | The **deterministic gate** — the part with no model in the path. Runs unit → format (Spotless/ktlint) → lint → build → install → instrumented UI. | `scripts/ai-dev-loop.sh` (full)<br>`scripts/ai-dev-loop.sh --no-ui` (skip on-device tests)<br>`scripts/ai-dev-loop.sh --fast` (unit only, no device) |

## Process docs (read, not run)

| File | Purpose |
|---|---|
| `ai-controller.md` | The playbook Claude Code follows for one request: restate spec → smallest change → gate → verify with ARTEMIS → adversarial review → report. |
| `ai-dev-roadmap.md` | The AI-dev process itself: tooling status, toolchain notes, and known issues. |
| `artemis-journeys.md` | The canonical ARTEMIS verify/observe journeys (J1–J8): the human-shaped checks a fixed test can't phrase. |

## Device-signal bridges (the "observe" half)

Run **after** an ARTEMIS journey has exercised the debug app, to surface what the device recorded.
Both auto-pick the first connected device if no serial is given. Wired into the `ai-dev-loop.sh`
verify hand-off; also runnable via the `/runtime-scan` command.

| Script | Surfaces | Exit |
|---|---|---|
| `crash-scan.sh` | Crashes (`FATAL EXCEPTION` + `crash` logbuffer), ANRs, StrictMode violations (advisory). | `1` + stack on a crash/ANR |
| `leak-scan.sh` | LeakCanary retained-object heap analysis (debug builds). | `1` + heap block on a leak |

```bash
scripts/crash-scan.sh [device_serial]
scripts/leak-scan.sh  [device_serial]
```

## Assets & models

| Script | Purpose | Usage |
|---|---|---|
| `voice-models.sh` | **Optional.** The voice models are bundled in the APK (`feature/voice/src/main/assets/voice/`), so a fresh clone needs nothing pushed. This only refreshes the on-device copies in `filesDir/voice` without a rebuild. | `scripts/voice-models.sh <dir-with-models> [serial]` |
| `voice-bench.py` | Zundamon voice benchmark: speaks a fixed Japanese sentence set at the phone (macOS `say -v Kyoko`) and reads back CER, ASR latency, first-audio and TTS timings from logcat. | `scripts/voice-bench.py` (see `--help`) |
| `vrm-prune-skins.py` | Makes a VRM/GLB loadable by Filament's gltfio by pruning each skin's joints to those its mesh uses (gltfio refuses > 256 bones per skin). Lossless, in place. | `scripts/vrm-prune-skins.py <in.vrm> <out.vrm>` |

## Reference plans & benchmark results

| File | Purpose |
|---|---|
| `vtuber-avatar-plan.md` | Design/implementation plan for the face-tracked VRM avatar feature. |
| `zundamon-voice-plan.md` | Design/implementation plan for the on-device Japanese voice changer. |
| `voice-bench-normal.md` · `voice-bench-results.md` | Recorded voice-benchmark runs and analysis. |

## Requirements

- **JDK 25** (the Android Studio JBR) on `JAVA_HOME`; `ai-dev-loop.sh` sets it.
- A connected, authorized Android device or emulator for the install/UI phases and all ARTEMIS work.
- macOS/Linux shell. macOS has no `timeout`; scripts use a `perl alarm` shim where needed.
