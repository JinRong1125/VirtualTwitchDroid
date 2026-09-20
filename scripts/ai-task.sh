#!/usr/bin/env bash
#
# ai-task.sh — natural-language entry point to the whole local dev loop.
#
# You type WHAT you want; Claude Code (head-less) understands it and drives the loop
# (edit → scripts/ai-dev-loop.sh gates → ARTEMIS verify → fix → report), following the
# playbook in scripts/ai-controller.md. This is the "AI understands the request" layer —
# ai-dev-loop.sh stays a dumb deterministic tool the agent calls.
#
# Usage:
#   scripts/ai-task.sh make the Live Channels header teal
#   scripts/ai-task.sh "the publish camera flashes black entering PiP — find and fix it"
#   scripts/ai-task.sh upgrade media3 to the latest and fix any breakage
#
#   AUTO=1 scripts/ai-task.sh ...   # unattended: skip permission prompts (trusted local only)
#   MODEL=opus  EFFORT=high  scripts/ai-task.sh ...
#
set -euo pipefail
cd "$(dirname "$0")/.."

REQUEST="$*"
if [ -z "$REQUEST" ]; then
  echo "usage: scripts/ai-task.sh <what to change / test / upgrade>" >&2
  exit 2
fi

command -v claude >/dev/null || { echo "The 'claude' CLI is not on PATH." >&2; exit 1; }

# Tools the agent may use unattended: shell (gradle/adb/the script + the runtime-monitor scans),
# file edits, search, skills, the code-reviewer subagent (Agent/Task — both names, since the tool is
# `Task` on the headless CLI and `Agent` in-session; playbook step 4b), and the ARTEMIS device tools.
ALLOWED="Bash Edit Write Read Grep Glob Skill Agent Task \
mcp__artemis__mobile_run_task mcp__artemis__mobile_manage_task \
mcp__artemis__mobile_inspect_trace mcp__artemis__mobile_diagnose \
mcp__artemis__mobile_get_device_state"

# Permission posture: default auto-accepts edits (other tools still gated); AUTO=1 is
# fully unattended. Use AUTO=1 only in a trusted local checkout.
if [ "${AUTO:-0}" = "1" ]; then
  PERM=(--dangerously-skip-permissions)
else
  PERM=(--permission-mode acceptEdits)
fi

EXTRA=()
[ -n "${MODEL:-}" ]  && EXTRA+=(--model "$MODEL")
[ -n "${EFFORT:-}" ] && EXTRA+=(--effort "$EFFORT")

echo "▶ AI dev-loop task: $REQUEST"
exec claude -p "$REQUEST" \
  --append-system-prompt "$(cat scripts/ai-controller.md)" \
  --allowedTools "$ALLOWED" \
  "${PERM[@]}" "${EXTRA[@]}"
