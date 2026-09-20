#!/usr/bin/env bash
#
# leak-scan.sh — bridge LeakCanary → the AI dev loop.
#
# LeakCanary (debug builds) watches for retained Activities/Fragments/Views/ViewModels and, on a
# leak, dumps + analyses the heap and prints the result to logcat. This script surfaces that result
# so Claude Code becomes aware of a leak and can open a fix task — the monitoring counterpart to the
# ARTEMIS verify pass.
#
# Flow (part of the AGENTIC verify step, AFTER scripts/ai-dev-loop.sh installs the debug APK):
#   1. Drive an ARTEMIS journey that exercises lifecycle churn (open a stream → minimize → back →
#      switch tabs → rotate), which is what makes leaks observable.
#   2. Run this script. Exit 1 + the heap-analysis block => a real leak to fix; exit 0 => clean.
#
# Usage: scripts/leak-scan.sh [device_serial]   (auto-picks the first connected device if omitted)
set -uo pipefail

grn=$'\033[32m'; red=$'\033[31m'; ylw=$'\033[33m'; rst=$'\033[0m'

DEVICE="${1:-$(adb devices | awk '/\tdevice$/{print $1; exit}')}"
[ -n "$DEVICE" ] || { printf "${red}✘ No authorized device.${rst}\n" >&2; exit 2; }

DUMP="$(adb -s "$DEVICE" logcat -d -s LeakCanary 2>/dev/null)"

if [ -z "$DUMP" ]; then
  printf "${ylw}▸ No LeakCanary output on %s yet. Launch the debug app and run an ARTEMIS journey that churns lifecycles (stream → minimize → back → tab switch), then re-run this.${rst}\n" "$DEVICE"
  exit 0
fi

echo "$DUMP" | grep -qi "LeakCanary is running" &&
  printf "${grn}✔ LeakCanary active on %s${rst}\n" "$DEVICE"

if echo "$DUMP" | grep -qi "APPLICATION LEAKS"; then
  # Print the human-readable heap-analysis block(s) for the AI to read.
  echo "$DUMP" | sed -n '/HEAP ANALYSIS RESULT/,/^.*METADATA/p'
  LEAKS="$(echo "$DUMP" | grep -oiE "[0-9]+ APPLICATION LEAK" | grep -oE "^[0-9]+" | sort -rn | head -1)"
  if [ "${LEAKS:-0}" -gt 0 ] 2>/dev/null; then
    printf "${red}✘ LeakCanary found %s application leak(s) — feed the heap analysis above into a fix task.${rst}\n" "$LEAKS"
    exit 1
  fi
fi

printf "${grn}✔ No application leaks reported in this run.${rst}\n"
exit 0
