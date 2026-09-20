# ARTEMIS journeys — the agentic verify / observe step

These are the canonical, human-shaped checks the AI runs through [ARTEMIS](https://github.com/google/artemis) **after**
`scripts/ai-dev-loop.sh` passes its deterministic gates. They assert things a fixed
instrumented test can't phrase (continuous playback, surface hand-offs, "did video
actually play"). Keep them in the repo so every run is comparable.

**How the AI runs one** (locally, via the ARTEMIS MCP tools — no CLI):

```
mobile_run_task(
  model            = "Pro",
  device_serial    = "<serial from adb devices>",
  locked_app_package = "com.example.virtualtwitchdroid",
  verification_level = "checkpoints",
  conversation_id  = "<this session>",
  task_desc        = "<one journey block below>",
)
# then: poll mobile_manage_task(status);  on FAIL → mobile_inspect_trace → open a fix task.
```

Mode guide: **Flash** for quick, deterministic path discovery while coding; **Pro**
for these verify/observe journeys (plan + checkpoints + exit review + trace evidence).
Physical device recommended — an emulator with a broken TLS trust store can't decode
the Twitch video CDN.

---

## J1 · Watch + PiP continuity  *(verify)*
Launch the app (Popular). Tap the first live channel; wait ≤25s for the video to play
(a pre-roll ad may run first). Tap the player, then the top-left "Minimize" chevron.
Assert the app returns to the channel list with a small floating video window in the
corner that **keeps playing** — no black frame, no spinner, no restart. Tap the window
to expand; assert playback continues. Report PASS/FAIL per step.

## J2 · Games → category → watch  *(verify)*
From the bottom bar tap "Games". Assert a grid of real categories with names + viewer
counts loads (≤15s), not an error/empty/spinner. Tap the first category; assert a screen
titled with that category, a channel list, a back arrow, and the bottom bar still visible.
Tap the first channel; assert the stream plays. Report PASS/FAIL per step.

## J3 · Publish camera + PiP  *(verify)*
Tap "Go Live". Grant camera + mic if prompted; assert a live camera preview (not black).
Tap the top-left Minimize chevron: assert the camera floats over the app, still live.
Return to Go Live: assert the float expands back and the preview is not black. Assert the overlay
layout: face toggle top-right; Mute microphone stacked on top of Video quality (gear) bottom-left;
"Go Live" centred at the bottom; PiP bottom-right. Tap the bottom-right PiP button: assert system PiP
shows ONLY the camera (no header/bottom bar).
Report PASS/FAIL per step, and flag any black-frame flash on a transition.

## J4 · Single mini-player (mutual exclusion)  *(verify)*
Minimize a watched stream (floating video appears). Go to "Go Live" and minimize the
camera. Assert only ONE floating window exists at a time — starting the camera float
removed the watch float, and vice-versa. Report PASS/FAIL.

## J5 · Nightly smoke  *(observe — run on a schedule)*
Run J2 → J1 → J3 back-to-back as one monitoring pass against the current build. Any
failure is a regression: capture the trace + step ledger via `mobile_inspect_trace`
and turn it into a coding task (the feedback arc). Keep going through all steps even
if one fails. **Then close the observe loop with the runtime monitors** (below) — the
lifecycle churn in J1/J3 is exactly what makes crashes / ANRs / leaks observable.

## J6 · Avatar tracks the camera face  *(verify — needs a real face in the device camera)*
Tap "Avatar" in the bottom bar; grant camera if prompted. Point a camera lens at a real face (use
"Use back camera" if the front lens can't see one). Assert within 10s the HUD says "Face detected"
(not "No face in view") and the fps figure is > 10. Read the "Head yaw · pitch · roll" line and the
top expressions. Turn and tilt the head and make expressions (open mouth, smile, surprise): assert
the yaw/pitch/roll numbers and the expression bars change to match, and the rendered avatar above the
HUD turns/tilts with them (take two screenshots ≥3s apart and compare the avatar's head). Report
PASS/FAIL per assertion with the numbers.

## J7 · VTuber mode broadcasts the avatar  *(verify — same camera setup as J6)*
Tap "Go Live"; grant camera + mic; assert the camera preview shows the face (not black). Tap the
face-icon toggle at the top-right of the preview (content description "Broadcast the avatar (VTuber
mode)"): assert the preview switches to a 3D anime-style avatar on a dark purple background within 5s
and the toggle icon turns purple. Tap the camera-switch button (top-center) so tracking uses the back
camera; assert the avatar's mouth/eyes/head now mirror the live face as it moves. Tap the top-left
Minimize chevron: assert the floating mini shows the **avatar**, still
animating. Expand it; tap the face toggle again: assert the camera preview returns. Never tap "Go
Live" itself (it would publish to the real Twitch key on screen). Report PASS/FAIL per step.

## J8 · Zundamon voice rehearsal  *(verify — voice models are bundled in the APK; `scripts/voice-models.sh` only refreshes on-device copies. Needs a real device's mic; the emulator's is silent)*
Tap "Avatar"; wait ~5s for the character. In the voice card tap "Listen (日本語)"; wait ~25s (engines
load). Assert the status text next to the button starts with "Listening" and the button reads "Stop";
assert the credit line "VOICEVOX:ずんだもん · ReazonSpeech k2 v2 (sherpa-onnx)" is visible. (With a
Japanese speaker near the phone — e.g. macOS `say -v Kyoko "今日はいい天気ですね"` — the recognised text
appears under the field and the character speaks it; the health log shows `voice mouth:` frames.) Tap
"Stop": assert "Voice off". Variant on Go Live: with VTuber mode on, the voice toggle appears directly under
the top-right face toggle ("Speak with the Zundamon voice"), turns purple when tapped, and an overlay
under the camera-flip button shows the state + credit;
never tap "Go Live" itself. Report PASS/FAIL per step.

---

### Reading the result
- `mobile_manage_task(status)` → `test_summary` gives machine-readable checkpoint pass/fail.
- On failure, `mobile_inspect_trace(view_step_screenshots | view_step_details)` gives the
  screenshots + exact action that broke — attach that to the fix task so the coding agent
  reproduces from evidence, not a vague "it broke".
- Evidence root: ARTEMIS's traces directory, `<artemis-home>/traces/<trace_id>/` (notes, screenshots, ledger).

### Runtime monitoring (pair with every observe pass)
After a journey, surface what the device recorded into the loop — the second half of "observe":

```
/runtime-scan <serial>          # the Claude command that runs both bridges, or call them directly:
scripts/crash-scan.sh <serial>  # crashes / ANRs (exit 1 + stack) + StrictMode advisories
scripts/leak-scan.sh  <serial>  # LeakCanary memory leaks (exit 1 + heap-analysis block)
```

A non-zero exit is a real signal: triage it (crash/leak → fix; a benign, library-internal,
GC-reclaimed advisory → investigate, then document if not app-owned — see the known issue in
`scripts/ai-dev-roadmap.md`). These are wired into the `scripts/ai-dev-loop.sh` verify handoff.
