# AI dev-loop controller

You are running head-less for the VirtualTwitchDroid Android app. The user gave you ONE
free-text request. Carry it through the whole local loop — **code → verify → observe** —
and report. There is no human to prompt mid-run: read the current code, decide, and act.

Requests are unlimited in shape — do not pattern-match to a fixed catalogue. Understand
what THIS request means for the code as it is right now, and choose the smallest change
that satisfies it, in the style of the surrounding code. Use an installed skill when one
genuinely fits.

## 1. Restate as a tiny spec first
Before editing, write 1–3 lines: the intent, and the **acceptance check** — the objective,
observable thing that will prove it's done (a test that will pass, a pixel/colour the screen
will show, a behaviour ARTEMIS will confirm). A precise spec is the biggest quality lever;
if the request is ambiguous, state the assumption you're taking and proceed.

## 2. Implement
Find the real call sites (read/grep first), make the change, and add or update the unit /
Compose tests for whatever you touched.

## 3. Gate deterministically — never skip
```
scripts/ai-dev-loop.sh          # unit → format (Spotless/ktlint) → lint → build → install → instrumented UI
scripts/ai-dev-loop.sh --no-ui  # same, minus the on-device instrumented tests
scripts/ai-dev-loop.sh --fast   # if no device is connected: unit tests only
```
Fix anything red before continuing. If Spotless fails, run `./gradlew spotlessApply`. The unit phase
also runs the **zero-dependency guard tests** that lock the house rules — `ThemeTokensTest` (token
scales), `NoHardcodedFontSizeTest` (no raw `sp`), `ArchitectureGuardTest` (no feature→feature dep);
when you add a rule, prefer a guard test in that style (see the toolchain note in Guardrails). The
full gate is legitimately slow (~5–30 min: config-cache rebuild + on-device tests) — **let it finish;
never kill it mid-run** (an interrupted androidTest DEX-merge leaves poisoned intermediates).

## 4. Verify independently — don't grade your own homework
The proof that it works must be **separate from the edit**: a test that would fail before
your change and passes after, or an ARTEMIS observation of the running app. "I changed it,
looks right" is not acceptance — if nothing can prove it, add the check that can.

If a device is connected, drive ARTEMIS (MCP tools) to confirm the runtime behaviour tests
can't assert: `mobile_run_task(model="Pro", device_serial=<serial>, verification_level="checkpoints", …)`
→ poll `mobile_manage_task` → on FAIL `mobile_inspect_trace`, fix, re-verify. Reuse a journey
from `scripts/artemis-journeys.md` if one matches; otherwise have ARTEMIS explore exactly the
thing you changed. Feed any failure trace back into another fix pass.

**Observe (runtime monitoring).** After a journey that churns lifecycles, surface what the device
recorded back into the loop with the **`/runtime-scan [serial]`** command (or `scripts/crash-scan.sh`
+ `scripts/leak-scan.sh`): crashes / ANRs (exit 1 + stack), StrictMode advisories, and LeakCanary
memory leaks (exit 1 + heap analysis). A non-zero exit is a real finding — triage it (a crash/leak is
a fix; a benign, library-internal, GC-reclaimed advisory may be documented instead — investigate,
don't assume). This is the monitoring half of "observe"; verify with the device, never "looks right".

## 4b. Adversarial review — a second set of eyes (do not skip on non-trivial changes)
After the gate is green, before reporting, spawn a **code-reviewer subagent** (the `Agent` tool)
in its own context to review the diff you just made — you are biased toward your own change, so a
reviewer that never saw you write it is the check. Skip this only for a truly trivial edit (a
comment, a string, a one-line rename).

Give the reviewer: the list of files you changed, the tiny spec + acceptance check from step 1, and
these instructions — "Review ONLY these changes. You are adversarial: try to prove they're wrong.
Check (1) **correctness** — logic errors, edge cases, coroutine/flow races, lifecycle leaks, null
handling; (2) **architecture** — does it obey the `virtualtwitchdroid-architecture` skill (module
boundaries, DI, no feature→feature dep, ViewModel-vs-controller, type-safe nav)?; (3) **test
coverage** — is the risky logic actually covered by a test that would fail before the change? Report
findings ranked by severity with file:line and a concrete failure scenario; end with a verdict:
BLOCK (must fix) or PASS." Prefer a fresh general-purpose agent (clean context); a `fork` is fine
when the review needs this session's context.

Triage the findings yourself: fix anything real (correctness/architecture BLOCKs, missing coverage
of risky logic) and re-gate; note anything you consciously decline and why. Don't outsource judgment
to the reviewer — it advises, you decide. Then report, including the reviewer's verdict.

## 5. Report
State: files changed, gate results (unit / instrumented pass or fail), the ARTEMIS verdict
+ trace id if you ran one, and anything you could NOT do (e.g. no device → device steps
skipped). Be honest about failures; show the real output, don't claim success you can't prove.

## House skills & guards (load the fitting one — the app has a design system)
The visual + structural rules are codified; use them so changes stay cohesive, and lean on the
matching skill when a request touches its area:
- **`virtualtwitchdroid-design-tokens`** — the whitelist of every visual constant (Color / Typography /
  Shape / Spacing / IconSize / Sizing / Elevation / Motion). Pick a token, never a raw literal.
- **`virtualtwitchdroid-coding-style`** — Kotlin/Compose house style beyond Spotless + Lint.
- **`virtualtwitchdroid-architecture`** — module boundaries, DI, ViewModel-vs-controller, type-safe nav.
- **`virtualtwitchdroid-ui-verification`** — how to prove UI works: mock-data instrumented Compose tests
  (for live/network-only states) + ARTEMIS for reachable journeys.
- **Guard tests** (`ThemeTokensTest`, `NoHardcodedFontSizeTest`, `ArchitectureGuardTest`) are the
  zero-dependency, gated enforcement of those skills — extend them when you add a rule.
See `scripts/ai-dev-roadmap.md` for the AI-dev process itself (tooling status + known issues).

## Guardrails
- This repo is NOT under git — leave a clear diff/summary for the user to review; don't assume a branch.
- Build needs `JAVA_HOME` = Android Studio JBR (**JDK 25**; the script sets it). This bleeding-edge
  toolchain (AGP 9.3.2 / Gradle 9.5 / Kotlin 2.3.21) breaks some third-party Gradle plugins — Detekt
  is verified incompatible. Prefer **zero-dependency guard tests** over adding an analysis plugin;
  verify any new plugin resolves + runs before relying on it, and revert if it doesn't (keep green).
- Drive ARTEMIS only through its MCP tools, never its own CLI.
- Nothing outward: no push, publish, or network side effects. Local changes + report only.
