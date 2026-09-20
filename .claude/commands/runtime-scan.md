---
description: Surface on-device runtime failures (crashes / ANRs / StrictMode / memory leaks) from the last ARTEMIS/app run into the loop.
argument-hint: "[device_serial]"
allowed-tools: Bash(scripts/crash-scan.sh:*), Bash(scripts/leak-scan.sh:*), Bash(adb:*)
---
The AI+ARTEMIS runtime-monitoring pass. Run this AFTER an ARTEMIS journey (or manual use)
has exercised the debug app — ideally one that churns lifecycles (stream → minimize → back →
tab switch → rotate), which is what makes crashes / ANRs / leaks observable. This is
verification/observe ONLY — report findings; do not edit code unless I ask.

Run both device-signal bridges (pass `$ARGUMENTS` through as the optional device serial;
they auto-pick the first connected device if it is empty):

```
scripts/crash-scan.sh $ARGUMENTS
scripts/leak-scan.sh $ARGUMENTS
```

- `crash-scan.sh` → **crashes** (`AndroidRuntime: FATAL EXCEPTION` + the `crash` logbuffer) and
  **ANRs** (exit 1 + the stack), plus **StrictMode** violations as advisories.
- `leak-scan.sh` → LeakCanary **memory leaks** (exit 1 + the heap-analysis block).

Then report: for each of crashes, ANRs, StrictMode advisories, and leaks, say clean or show the
key stack/heap lines + the offending component. If either script exits non-zero, call out the
failure clearly and propose the fix (but do not apply it unless I ask). If no LeakCanary output or
no device is found, say the journey/device step was skipped.
