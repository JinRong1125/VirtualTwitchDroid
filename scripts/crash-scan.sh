#!/usr/bin/env bash
#
# crash-scan.sh — bridge on-device runtime failures → the AI dev loop.
#
# ARTEMIS drives human-shaped journeys; this scans what the device recorded during them and surfaces
# the stability-critical failures so Claude Code becomes aware and can open a fix task — the crash/ANR
# counterpart to leak-scan.sh (LeakCanary). It reads:
#   • CRASHES  — the `crash` logbuffer + "FATAL EXCEPTION" (unhandled Kotlin/Java exceptions).
#   • ANRs     — "ANR in <package>" (main-thread stalls the OS killed or flagged).
#   • STRICTMODE — the debug StrictMode monitor's violations (main-thread I/O, leaked Closeables).
#
# Flow (part of the AGENTIC verify step, AFTER scripts/ai-dev-loop.sh installs the debug APK):
#   1. Run an ARTEMIS journey (or `scripts/ai-dev-loop.sh` then a journey).
#   2. Run this. Exit 1 + a stack => a crash/ANR to fix; StrictMode findings are advisory (exit 0).
#
# Usage: scripts/crash-scan.sh [device_serial]   (auto-picks the first connected device if omitted)
set -uo pipefail

grn=$'\033[32m'; red=$'\033[31m'; ylw=$'\033[33m'; rst=$'\033[0m'
PKG="com.example.virtualtwitchdroid"

DEVICE="${1:-$(adb devices | awk '/\tdevice$/{print $1; exit}')}"
[ -n "$DEVICE" ] || { printf "${red}✘ No authorized device.${rst}\n" >&2; exit 2; }

fail=0

# --- Crashes (dedicated crash buffer + FATAL EXCEPTION in main) --------------
CRASH="$(adb -s "$DEVICE" logcat -d -b crash 2>/dev/null)"
FATAL="$(adb -s "$DEVICE" logcat -d 2>/dev/null | grep -E "AndroidRuntime: FATAL EXCEPTION" -A 25)"
if [ -n "${CRASH// }" ] || [ -n "$FATAL" ]; then
  printf "${red}✘ CRASH detected on %s:${rst}\n" "$DEVICE"
  { [ -n "${CRASH// }" ] && echo "$CRASH"; [ -n "$FATAL" ] && echo "$FATAL"; } | head -40
  fail=1
else
  printf "${grn}✔ No crashes in this run.${rst}\n"
fi

# --- ANRs -------------------------------------------------------------------
ANR="$(adb -s "$DEVICE" logcat -d 2>/dev/null | grep -E "ANR in ${PKG}" -A 6)"
if [ -n "$ANR" ]; then
  printf "${red}✘ ANR detected:${rst}\n"; echo "$ANR" | head -20; fail=1
else
  printf "${grn}✔ No ANRs in this run.${rst}\n"
fi

# --- StrictMode advisories (non-fatal; the debug monitor) -------------------
SM="$(adb -s "$DEVICE" logcat -d 2>/dev/null | grep -F "StrictMode policy violation" | sort -u)"
if [ -n "$SM" ]; then
  printf "${ylw}▸ StrictMode advisories (main-thread I/O / leaked resources — review, non-fatal):${rst}\n"
  echo "$SM" | head -10
fi

if [ "$fail" -ne 0 ]; then
  printf "${red}✘ Runtime failures found — feed the stack(s) above into a fix task.${rst}\n"
  exit 1
fi
printf "${grn}✔ Device run clean (no crashes / ANRs).${rst}\n"
exit 0
