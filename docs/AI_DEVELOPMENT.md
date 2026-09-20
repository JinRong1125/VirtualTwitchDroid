# AI-assisted development with ARTEMIS

VirtualTwitchDroid is built with a local, **CI-free** AI development loop. Instead of a cloud
pipeline, quality is enforced on the developer's own machine by a deterministic gate plus
verification on a real Android device driven by [**ARTEMIS**](https://github.com/google/artemis), an autonomous mobile UI agent that
Claude Code controls over MCP.

- **Project home:** <https://github.com/JinRong1125/VitrualTwitchDroid>
- **The playbook:** [`../scripts/ai-controller.md`](../scripts/ai-controller.md)
- **The scripts:** [`../scripts/README.md`](../scripts/README.md)
- **The device journeys:** [`../scripts/artemis-journeys.md`](../scripts/artemis-journeys.md)

## The loop

Every request — a feature, a fix, a refactor — runs through the same five steps:

```
1. tiny spec        restate intent + an objective acceptance check
2. smallest change  edit the real call sites, add/adjust tests
3. gate             scripts/ai-dev-loop.sh  (no model in the path)
4. verify + observe ARTEMIS journey on a device, then crash/leak scan
5. report           files changed · gate result · ARTEMIS verdict
        │
        └── on failure: ARTEMIS trace → fix → back to step 3
```

Steps 1, 2 and 5 are the agent's judgment. Step 3 is a dumb, reproducible script. Step 4 is where
ARTEMIS proves the app actually behaves — the thing a fixed unit test cannot assert.

## Step 3 — the deterministic gate

[`scripts/ai-dev-loop.sh`](../scripts/ai-dev-loop.sh) owns everything with no LLM in the path, so it
is fast and repeatable:

```bash
scripts/ai-dev-loop.sh          # unit → format (Spotless/ktlint) → lint → build → install → UI
scripts/ai-dev-loop.sh --no-ui  # same, minus on-device instrumented tests
scripts/ai-dev-loop.sh --fast   # unit tests only (no device connected)
```

The unit phase also runs the zero-dependency **guard tests** that lock the house rules
(`ArchitectureGuardTest`, `ThemeTokensTest`, `NoHardcodedFontSizeTest`, and the bundled-asset
guards). The full gate is legitimately slow (config-cache rebuild + on-device tests); it must run to
completion — a killed `androidTest` run poisons the DEX-merge intermediates.

## Step 4 — verify and observe with ARTEMIS

ARTEMIS is invoked **by the agent through its MCP tools**, never by its own CLI. Claude Code hands
ARTEMIS a natural-language journey; ARTEMIS plans, drives the device, and reports checkpoint results
with a screenshot ledger.

### Two execution models

| Model | When | How it runs |
|---|---|---|
| **Flash** | Quick, deterministic UI path discovery while coding. | A single reactive Observe–Think–Act loop; chains taps to catch transient UI. |
| **Pro** | Long, multi-branch verify/observe journeys; deep diagnostics; a written report. | A Plan → Operate → Check → (optional) Output multi-agent graph with a pre-execution safety net and a read-only verifier. |

Typical call (from the MCP session):

```
mobile_run_task(
  model              = "Pro",
  device_serial      = "<from adb devices>",
  locked_app_package = "com.example.virtualtwitchdroid",
  verification_level = "checkpoints",
  task_desc          = "<one journey from scripts/artemis-journeys.md>",
)
# poll mobile_manage_task(status); on FAIL → mobile_inspect_trace → open a fix task.
```

The canonical journeys (J1–J8: watch + PiP, browse, publish, mutual-exclusion, avatar tracking,
VTuber broadcast, Zundamon voice) live in
[`scripts/artemis-journeys.md`](../scripts/artemis-journeys.md). Reuse a matching journey; otherwise
have ARTEMIS explore exactly what changed.

> **Never** let automation press **Go Live** — the Go Live screen holds a real Twitch stream key.
> Toggle and inspect the avatar/voice controls, but do not start the broadcast.

### The observe half — runtime monitoring

After a journey that churns lifecycles, surface what the device recorded back into the loop:

```bash
scripts/crash-scan.sh [serial]   # crashes / ANRs (exit 1 + stack) + StrictMode advisories
scripts/leak-scan.sh  [serial]   # LeakCanary memory leaks (exit 1 + heap analysis)
```

A non-zero exit is a real signal: a crash or leak is a fix; a benign, library-internal advisory is
investigated and documented. The `/runtime-scan` command runs both bridges at once.

## How it combines with the repo

The loop is self-contained in a clone — there is nothing external to configure:

1. Clone <https://github.com/JinRong1125/VitrualTwitchDroid> with **Git LFS** (large model binaries
   are bundled) and open it in Android Studio (**JDK 25** / the Android Studio JBR).
2. Connect an authorized Android device (a Pixel 8a is the reference) or start an emulator.
3. Run `scripts/ai-dev-loop.sh` to gate, then drive an ARTEMIS journey to verify. No account, key,
   or server is needed for browsing, watching, the avatar, or the voice — only going live needs your
   own Twitch stream key.

All assets (the ReazonSpeech ASR models, VOICEVOX `0.vvm`, Open JTalk dictionary, the Zundamon VRM,
the MediaPipe face model) ship in the APK, so a fresh clone builds and runs with no downloads.

## Guardrails

- **Not under git while developing** — the working tree is the source of truth; leave a clear
  change summary. (The published repo above is the shareable snapshot.)
- **Bleeding-edge toolchain** (AGP 9.3.2 / Gradle 9.5 / Kotlin 2.3.21) breaks some third-party
  Gradle plugins; prefer zero-dependency guard tests over adding an analysis plugin.
- **Nothing outward** — the loop makes local changes only: no push, publish, or network side
  effects, and no automated Go Live.
