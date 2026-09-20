# `.claude/` — Claude Code configuration for this project

This folder configures the local, CI-free **AI development loop** (see
[`../docs/AI_DEVELOPMENT.md`](../docs/AI_DEVELOPMENT.md)). It is what lets [Claude
Code](https://claude.com/claude-code) drive the repo's spec → change → gate → verify loop, with
device verification through [ARTEMIS](https://github.com/google/artemis).

Everything here is **shared, project-level config and is committed** — except your own local
settings and runtime state, which are git-ignored (see the bottom of this file). Nothing here
contains secrets: API keys and device serials live only in your own machine's config, never in the
repo.

## What's in here

### `commands/` — slash commands
Type these in Claude Code as `/<name>`.

| Command | What it does |
|---|---|
| `ai-task` | The main entry point. You describe *what* you want in plain language; Claude Code turns it into the full loop (tiny spec → smallest change → gate → ARTEMIS verify → report), following [`../scripts/ai-controller.md`](../scripts/ai-controller.md). |
| `ai-dev-loop` | Runs the deterministic gate only ([`../scripts/ai-dev-loop.sh`](../scripts/ai-dev-loop.sh)): unit → format → lint → build → install → instrumented UI. |
| `runtime-scan` | After an ARTEMIS journey, runs the device-signal bridges (`crash-scan.sh` + `leak-scan.sh`) to surface crashes, ANRs, StrictMode advisories and LeakCanary leaks. |

### `skills/` — packaged expertise Claude loads on demand
Project-specific skills encode this repo's house rules; the rest are general Android skills.

- **Project:** `virtualtwitchdroid-architecture` (module boundaries, DI, nav),
  `virtualtwitchdroid-design-tokens` (the visual-constant whitelist),
  `virtualtwitchdroid-coding-style` (Kotlin/Compose house style),
  `virtualtwitchdroid-ui-verification` (how to prove UI works).
- **General Android:** `android-profiler`, `agp-9-upgrade`, `r8-analyzer`, `testing-setup`,
  `edge-to-edge`, `adaptive`, `camerax`.

### Local & runtime (git-ignored — created per developer)
- `settings.local.json` — **your** Claude Code permission allowlist. Claude Code builds it as you
  approve tools; it holds machine-specific paths, so it is never committed.
- `scheduled_tasks.lock` (and any `scheduled_tasks/`) — runtime state for a running session.

## Setup for a fresh clone

You need nothing from the previous developer's machine — set up your own:

1. **Toolchain** — Android Studio with **JDK 25** (the Android Studio JBR). See the root
   [`../README.md`](../README.md) for building the app.
2. **Git LFS** — the repo tracks large model binaries; run `git lfs install` before cloning so the
   `.onnx` / `.vvm` / `.vrm` assets are fetched.
3. **Claude Code** — install it (<https://claude.com/claude-code>) and open this project. The
   commands and skills above load automatically from this folder. On first use, Claude Code creates
   your own `.claude/settings.local.json` as you approve tools.
4. **A device** — connect an authorized Android device (a Pixel 8a is the reference) or start an
   emulator. `adb devices` should list it.
5. **ARTEMIS (for the verify step)** — install and configure the
   [ARTEMIS](https://github.com/google/artemis) MCP server in your Claude Code MCP config, with your
   own LLM API key in its `.env` (never in this repo). Then the `mcp__artemis__*` tools appear;
   `mobile_diagnose` checks the device, credentials and environment and returns ordered fixes.

Once that's in place, run `/ai-task <what you want>` and the loop runs locally — there is no CI and
no shared server. Full process: [`../docs/AI_DEVELOPMENT.md`](../docs/AI_DEVELOPMENT.md).
