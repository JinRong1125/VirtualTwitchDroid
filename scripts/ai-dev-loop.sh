#!/usr/bin/env bash
#
# ai-dev-loop.sh — the deterministic backbone of the local AI development loop.
#
# It runs the "inner loop" (compile + unit tests) and the "outer loop" (install +
# on-device instrumented tests) for VirtualTwitchDroid, then hands off to the AGENTIC step:
# the AI (Claude Code) drives ARTEMIS via its MCP tools to verify/monitor the
# human-shaped journeys that a fixed test can't assert. See scripts/artemis-journeys.md.
#
#   code ──▶ verify ──▶ observe ──▶ (trace → fix) ──▶ code
#
# This script owns the parts with NO model in the path (fast, reproducible). ARTEMIS
# is invoked by the agent, not here, because locally it runs through the MCP session.
#
# Usage:
#   scripts/ai-dev-loop.sh              # full loop: unit → format → lint → build → install → ui
#   scripts/ai-dev-loop.sh --fast       # inner loop only: unit tests (no format/lint/build/device)
#   scripts/ai-dev-loop.sh --no-ui      # unit + format + lint + build + install, skip on-device tests
#   scripts/ai-dev-loop.sh --module :feature:browse   # scope UI tests to one module
#
set -euo pipefail
cd "$(dirname "$0")/.."

# --- config -----------------------------------------------------------------
# The CLI's default `java` is too old for Gradle 9 / AGP 9; use Android Studio's JBR.
: "${JAVA_HOME:=/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME
APK="app/build/outputs/apk/debug/app-debug.apk"
APP_ID="com.example.virtualtwitchdroid"
TRACES_DIR="$HOME/artemis/traces"   # ARTEMIS session evidence lands here

# UI-test modules (each has connectedDebugAndroidTest coverage).
UI_MODULES=(":core:designsystem" ":feature:browse" ":feature:stream" ":feature:publish" ":feature:avatar")

# --- args -------------------------------------------------------------------
RUN_UNIT=1; RUN_FORMAT=1; RUN_LINT=1; RUN_BUILD=1; RUN_INSTALL=1; RUN_UI=1; ONE_MODULE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --fast)    RUN_FORMAT=0; RUN_LINT=0; RUN_BUILD=0; RUN_INSTALL=0; RUN_UI=0 ;;
    --no-ui)   RUN_UI=0 ;;
    --module)  shift; ONE_MODULE="${1:-}"; UI_MODULES=("$ONE_MODULE") ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

# --- pretty helpers ---------------------------------------------------------
bold=$'\033[1m'; dim=$'\033[2m'; grn=$'\033[32m'; red=$'\033[31m'; ylw=$'\033[33m'; cyn=$'\033[36m'; rst=$'\033[0m'
step() { printf "\n${bold}${cyn}▶ %s${rst}\n" "$*"; }
ok()   { printf "${grn}✔ %s${rst}\n" "$*"; }
warn() { printf "${ylw}▸ %s${rst}\n" "$*"; }
die()  { printf "${red}✘ %s${rst}\n" "$*" >&2; exit 1; }

gradlew() { ./gradlew "$@" --console=plain; }

# --- device -----------------------------------------------------------------
pick_device() {
  adb devices | awk '/\tdevice$/{print $1; exit}'
}

# --- 1. inner loop: unit tests ---------------------------------------------
if [ "$RUN_UNIT" = 1 ]; then
  step "Inner loop — JVM unit tests (testDebugUnitTest)"
  gradlew testDebugUnitTest || die "Unit tests failed — fix before going to the device."
  ok "Unit tests green"
fi

# --- 1a. formatting: Spotless + ktlint (coding style) -----------------------
# Reads the root .editorconfig. `spotlessCheck` fails on unformatted code; run `./gradlew
# spotlessApply` to auto-fix. Static (no device), so it runs before the build.
if [ "$RUN_FORMAT" = 1 ]; then
  step "Formatting — Spotless / ktlint (spotlessCheck)"
  gradlew spotlessCheck || die "Unformatted code — run: ./gradlew spotlessApply"
  ok "Formatting clean (ktlint via Spotless)"
fi

# --- 1b. static analysis: Android Lint (coding-style + correctness) ---------
# checkDependencies=true makes :app:lintDebug analyze every module in one pass; results are
# filtered through lint-baseline.xml so only NEW issues (regressions) fail the loop.
if [ "$RUN_LINT" = 1 ]; then
  step "Static analysis — Android Lint (:app:lintDebug, baseline-filtered)"
  gradlew :app:lintDebug || die "Lint found NEW issues beyond the baseline — see app/build/reports/lint-results-debug.html"
  ok "Lint clean (no new issues vs lint-baseline.xml)"
fi

# --- 2. build ---------------------------------------------------------------
if [ "$RUN_BUILD" = 1 ]; then
  step "Assemble debug APK"
  gradlew :app:assembleDebug || die "Build failed."
  ok "APK built: $APK"
fi

# --- 3. install -------------------------------------------------------------
DEVICE=""
if [ "$RUN_INSTALL" = 1 ] || [ "$RUN_UI" = 1 ]; then
  DEVICE="$(pick_device || true)"
  [ -n "$DEVICE" ] || die "No authorized device/emulator. Connect + unlock one, then re-run. (adb devices)"
  ok "Device: $DEVICE"
fi
if [ "$RUN_INSTALL" = 1 ]; then
  step "Install on $DEVICE"
  adb -s "$DEVICE" install -r "$APK" >/dev/null || die "Install failed."
  ok "Installed $APP_ID"
fi

# --- 4. outer loop: deterministic on-device UI tests ------------------------
if [ "$RUN_UI" = 1 ]; then
  step "Outer loop — instrumented UI tests on $DEVICE"
  # One Gradle invocation PER module: (1) --no-parallel because the modules' androidTest APKs otherwise dex
  # the same external jars in concurrent D8 transforms and race on the transform cache; (2) separate JVMs
  # because merging all five test APKs' external DEX in one 6 GB JVM ran out of heap once the voice
  # feature's native AARs joined the build (2026-09-19) — each module alone fits comfortably.
  for m in "${UI_MODULES[@]}"; do
    gradlew --no-parallel "${m}:connectedDebugAndroidTest" || die "Instrumented tests failed in $m — inspect its build/reports/androidTests."
  done
  ok "Instrumented tests green (${UI_MODULES[*]})"
fi

# --- 5. hand off to the AGENTIC step ---------------------------------------
step "Verify / Observe — hand off to ARTEMIS (agentic)"
cat <<EOF
${dim}The deterministic gates passed. Now the AI drives ARTEMIS via its MCP tools to
check the human-shaped journeys a fixed test can't assert, on device ${DEVICE:-<none>}.${rst}

  Run the canonical journeys in ${bold}scripts/artemis-journeys.md${rst} with:
    mobile_run_task(model="Pro", device_serial="${DEVICE:-<serial>}",
                    verification_level="checkpoints", conversation_id="<run>", task_desc=<journey>)
  then poll mobile_manage_task; on FAIL, mobile_inspect_trace → feed the trace back as a fix task.

  ${bold}Runtime monitoring${rst} — after a journey (churn lifecycles: stream → minimize → back →
  tab switch → rotate), surface what the device recorded back into the loop with the Claude command
    ${bold}/runtime-scan ${DEVICE:-<serial>}${rst}
  which runs both bridges (or call them directly):
    ${bold}scripts/crash-scan.sh ${DEVICE:-<serial>}${rst}   # crashes / ANRs (exit 1) + StrictMode advisories
    ${bold}scripts/leak-scan.sh  ${DEVICE:-<serial>}${rst}   # LeakCanary memory leaks (exit 1 + heap analysis)
  A non-zero exit + a stack/heap block => open a fix task from it.

  Evidence (traces, notes, screenshots): ${TRACES_DIR}
EOF
ok "Loop backbone complete — ready for the ARTEMIS verify/observe pass."
