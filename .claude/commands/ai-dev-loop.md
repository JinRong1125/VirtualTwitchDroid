---
description: Run the deterministic gates only (unit → format → lint → build → install → instrumented UI). No code changes.
argument-hint: "[--fast | --no-ui | --module :feature:xxx]"
allowed-tools: Bash(scripts/ai-dev-loop.sh:*), Bash(./gradlew:*), Bash(adb:*)
---
Run the project's deterministic verification gate and report the outcome. This is
verification ONLY — do not edit any code, and do not run ARTEMIS.

Run:

```
scripts/ai-dev-loop.sh $ARGUMENTS
```

Then report which stages passed or failed (unit tests / build / install / instrumented
UI tests). If a stage fails, show the key error lines and name the failing module or
test — but do not attempt a fix unless I ask. If no device is connected, note that the
on-device stages were skipped.
