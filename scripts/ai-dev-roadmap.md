# AI dev-process roadmap

A survey-backed assessment of VirtualTwitchDroid's local, AI-driven development process (no CI/CD — the
inner+outer loop runs on the dev machine via `scripts/ai-dev-loop.sh` + ARTEMIS). Goal: **feature
stability · coding quality · dev efficiency · maintainability · cohesiveness.** Items are ranked
within each perspective by value ÷ effort. `[done]` = already in place; `[next]` = recommended.

Grounding (2026 agentic-Android practice): structural guards (Konsist), Kotlin static analysis
(Detekt), screenshot regression, and device logs/screenshots for agent verification — see
spin.atomicobject.com/agentic-android-development, github.com/oscardlfr/AndroidCommonDoc,
minitap.ai AI-coding-agents-automated-testing.

> **Toolchain note (blocks several third-party plugins).** The build runs on the Android Studio JBR
> = **JDK 25** (required for AGP 9.3.2 / Gradle 9.5 / Kotlin 2.3.21). Detekt 1.23.8 was verified to
> **fail on JDK 25** (`Invalid value (25) passed to --jvm-target` → then a raw `25.0.2` parse error,
> even with `jvmTarget` pinned) — its embedded compiler tops out at JVM 22. The other analysis
> plugins that embed a Kotlin/JVM engine (Kover, Roborazzi, dependency-analysis) are the same class
> and are treated as **`[blocked]` until they ship a JDK-25-compatible release**, so they are NOT
> added (adding an incompatible plugin would break every build — against the stability goal). The
> zero-dependency guard-test approach (ArchitectureGuardTest, NoHardcodedFontSizeTest, ThemeTokensTest)
> sidesteps this entirely and is the preferred pattern here until the ecosystem catches up.

## Design
- `[done]` Design-token layer (Color/Type/Shape/Spacing/IconSize/Sizing/Elevation/Motion) + the
  `virtualtwitchdroid-design-tokens` skill (whitelist) + `ThemeTokensTest` value/order locks.
- `[blocked] HIGH` **Screenshot regression tests** (Roborazzi — JVM, no device) for key designsystem
  components + screen states: deterministic UI-drift catch (ARTEMIS only spot-checks). Same JDK-25
  plugin-compat risk class; defer until verified on the JBR. Meanwhile, ARTEMIS + the instrumented
  Compose tests (`virtualtwitchdroid-ui-verification`) cover UI verification.

## Coding
- `[done]` Spotless/ktlint + Android Lint (baseline) + the `virtualtwitchdroid-coding-style` skill +
  `NoHardcodedFontSizeTest`.
- `[blocked] HIGH` **Detekt** (+`detekt-compose`) — Kotlin static analysis beyond Lint. Attempted and
  reverted: **incompatible with JDK 25** (see toolchain note). Re-add when a JDK-25 detekt ships:
  add the `detekt` plugin to root, `apply` it in `subprojects{}` with `buildUponDefaultConfig=true` +
  per-module `detekt-baseline.xml`, then `./gradlew detektBaseline` and wire `detekt` into the loop.
- `[blocked] MED` **Dependency-analysis** (com.autonomousapps) — flag unused/mis-scoped deps. Same
  JDK-25 risk class; defer.

## Testing
- `[done]` JVM unit + instrumented Compose (mock-data) + ARTEMIS journeys; the
  `virtualtwitchdroid-ui-verification` skill; per-module `connectedDebugAndroidTest` in the gate.
- `[done]` **Architecture guard test** (`ArchitectureGuardTest`, `:core:designsystem` unit): enforces
  the key `virtualtwitchdroid-architecture` rule — **no feature→feature dependency** — as a zero-dependency
  source scan (chosen over the Konsist library so there's no Kotlin-parser version to keep in lockstep
  with the bleeding-edge Kotlin 2.3.21 toolchain). Extendable to `*ViewModel`-has-test / package rules.
- `[blocked] MED` **Kover** coverage report — same JDK-25 plugin-compat risk class as Detekt; defer
  until verified on the JBR, then apply `org.jetbrains.kotlinx.kover` + aggregate + wire a report task.

## Monitoring (dev-time — no prod telemetry without CI/CD)
- `[done]` **StrictMode** in the debuggable build: logs main-thread I/O + leaks to logcat during
  local + ARTEMIS runs. Zero release impact.
- `[done]` **LeakCanary** (`debugImplementation`, zero code) + **`scripts/leak-scan.sh`**: the AI+
  ARTEMIS integration — an ARTEMIS journey churns lifecycles, LeakCanary heap-analyses any leak to
  logcat, and `leak-scan.sh` surfaces it (exit 1 + heap block) so Claude opens a fix task. Wired into
  the `scripts/ai-dev-loop.sh` verify handoff.
- `[done]` **`scripts/crash-scan.sh`** (AI+ARTEMIS): after a journey, surfaces the device's runtime
  failures — **crashes** (`AndroidRuntime: FATAL EXCEPTION` + `crash` logbuffer) and **ANRs** (exit 1
  + the stack → fix task), plus **StrictMode** violations as advisories (finally *collecting* the
  debug StrictMode monitor). Companion to `leak-scan.sh`, wired into the verify handoff. Verified with
  a real `am crash` (detected, exit 1) and a clean run (exit 0).
- `[next] MED` **Macrobenchmark + Baseline Profile** (the `:baselineprofile` module already exists):
  measure startup/scroll jank locally. Serves stability/perf.
- `[next] LOW` **Compose compiler metrics/stability reports** to catch unstable params causing
  recomposition. Serves perf + quality.

## Efficiency
- `[done]` Gradle build cache on; `--fast` / `--no-ui` loop modes.
- `[done]` **Gradle configuration cache** (`org.gradle.configuration-cache=true`): verified compatible
  with the plugin set (AGP 9.3.2 / Hilt / KSP / baselineprofile / spotless) — stored + reused, config
  time ~1m→~1s. Serves dev efficiency.

## Maintainability / cohesiveness (cross-cutting)
- `[done]` Skills (architecture, coding-style, design-tokens, ui-verification) + project memory +
  `scripts/ai-controller.md` playbook.
- `[next] LOW` Keep this roadmap + the skills current as the token layer / guards evolve.

## Known issues (surfaced by the runtime monitors)
- **RootEncoder preview-Surface CloseGuard (benign).** Under heavy Go-Live navigation churn,
  `crash-scan.sh` surfaces a StrictMode `LeakedClosableViolation` — `Surface.release not called`,
  acquired at `StreamBase.startPreview` (RootEncoder) via `PublishController.attachPreview` cold-start.
  Investigated (2026-09-18): the app pairs `startPreview`/`stopPreview` correctly and releases its own
  swap Surfaces; forcing an unconditional `stopPreview()` at teardown did **not** clear it → the
  un-released Surface is **RootEncoder-internal, not an app leak**. It is GC-reclaimed and
  **LeakCanary-clean** (no retained-object leak, no crash/ANR). Left as-is rather than risk the
  fragile flash-free preview-swap design; revisit if a RootEncoder update fixes it or if LeakCanary
  ever flags a real retention. (Documented at `PublishController.attachPreview` cold-start.)

## Status summary
- **AI+ARTEMIS runtime bridges (landed):** `leak-scan.sh` (LeakCanary memory leaks) and
  `crash-scan.sh` (crashes / ANRs / StrictMode) — an ARTEMIS journey exercises the app, these scan
  what the device recorded and surface failures (non-zero exit + stack/heap) into the loop so Claude
  opens a fix task. Both wired into the `ai-dev-loop.sh` verify handoff and invocable as the
  **`/runtime-scan [serial]`** Claude command (`.claude/commands/runtime-scan.md`).
- **Also landed:** StrictMode monitor, architecture guard test, Gradle configuration-cache — gated
  green + verified.
- **Blocked on the JDK-25 toolchain** (see note): Detekt, Kover, dependency-analysis, Roborazzi.
  Re-attempt each when a JDK-25-compatible release ships; steps are in each item above.
- **Deferred (heavy, not blocked):** Macrobenchmark (new `com.android.test` module using the
  `:baselineprofile` benchmark stack) — worthwhile next, but a module-sized effort.

Next reliable step once the ecosystem catches up to JDK 25: Detekt (coding quality) → Kover
(coverage) → Roborazzi (design regression). Until then, prefer zero-dependency guard tests.
