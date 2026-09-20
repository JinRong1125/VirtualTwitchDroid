---
description: Run the local AI dev loop (spec → code → gate → verify → report) for a request
argument-hint: <what to change / test / upgrade>
allowed-tools: Edit, Write, Read, Grep, Glob, Skill, Agent, Task, Bash(scripts/ai-dev-loop.sh:*), Bash(scripts/crash-scan.sh:*), Bash(scripts/leak-scan.sh:*), Bash(./gradlew:*), Bash(adb:*), mcp__artemis__mobile_run_task, mcp__artemis__mobile_manage_task, mcp__artemis__mobile_inspect_trace, mcp__artemis__mobile_diagnose, mcp__artemis__mobile_get_device_state
---
Carry the request below through the whole local development loop, following this playbook
exactly — restate a tiny spec, make the smallest change, run the deterministic gate with
`scripts/ai-dev-loop.sh`, verify independently (a test or an ARTEMIS observation, never
"looks right"), then report files changed + gate results + any ARTEMIS verdict.

@scripts/ai-controller.md

---
The request: $ARGUMENTS
