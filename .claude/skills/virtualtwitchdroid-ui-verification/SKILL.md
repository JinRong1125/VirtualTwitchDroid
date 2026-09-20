---
name: virtualtwitchdroid-ui-verification
description: How to VERIFY that a UI feature actually works in the VirtualTwitchDroid app — the two-layer
  model of (1) instrumented Compose UI tests driven by mock/fake data (deterministic, and the only
  way to reach live-only or network-only UI without real outward side effects) and (2) ARTEMIS for
  the reachable human journeys. Use this WHENEVER authoring or changing a screen/component and you
  need proof it works: which layer to use, how to add instrumented tests to a module, the house test
  style, the shared fakes, and the fail-before/pass-after discipline.
metadata:
  author: VirtualTwitchDroid
  last-updated: '2026-09-18'
  keywords:
  - android
  - testing
  - compose ui test
  - instrumented
  - mock data
  - artemis
  - verification
---

## The core idea: two verification layers

A feature is "done" only when something **separate from the edit** proves it works — never "I
changed it, looks right" (see `scripts/ai-controller.md`). There are two layers, and you pick by
**whether the state is reachable on a real device without an outward side effect**:

| Layer | Use for | Runs |
|-------|---------|------|
| **Instrumented Compose UI test** (mock/fake data) | Deterministic behavior; **live-only / network-only** UI you can't reach on-device without broadcasting, hitting the real Twitch API, or a real socket. Scroll/measure/state-mapping assertions. | `connectedDebugAndroidTest` in the gate |
| **ARTEMIS** (MCP tools) | Reachable human journeys a fixed test can't assert (real camera, PiP, cross-screen navigation, timing). | Agentic, after the gate — see `scripts/artemis-journeys.md` |

**Why mock data matters:** some UI only appears in a state we must NOT trigger for real. Example:
the publish received-chat overlay renders only while **live** — verifying it on-device would mean
actually broadcasting to Twitch (a forbidden outward side effect). So we drive the *stateless*
composable with **fake `ChatMessage`s** in an instrumented test and assert the real layout/scroll
behavior. ARTEMIS then only confirms the *reachable* idle screen is intact.

Rule of thumb: **if reaching the state needs a broadcast / network / real account, verify it with a
mock-data instrumented test.** Reserve ARTEMIS for what a test genuinely can't observe.

## Adding instrumented Compose tests to a module

`:core:designsystem`, `:feature:browse`, `:feature:stream`, `:feature:publish` are wired for this.
To add a new module:

1. **Deps** in the module's `build.gradle.kts` (mirror `:feature:browse`):
   ```kotlin
   androidTestImplementation(projects.core.testing)          // shared fakes + TestData
   androidTestImplementation(libs.androidx.junit)
   androidTestImplementation(libs.androidx.espresso.core)     // pin newer Espresso for API 37+
   androidTestImplementation(libs.androidx.compose.ui.test.junit4)
   debugImplementation(libs.androidx.compose.ui.test.manifest)
   ```
   The instrumentation runner (`androidx.test.runner.AndroidJUnitRunner`) and
   `testOptions.animationsDisabled = true` come from the library convention plugin — nothing to add.

2. **Gate**: add the module to `UI_MODULES` in `scripts/ai-dev-loop.sh` so
   `<module>:connectedDebugAndroidTest` runs every loop.

## House test style (match it exactly)

- **No `@RunWith`** — the convention plugin sets the runner.
- Rule: `androidx.compose.ui.test.junit4.v2.createComposeRule()` for a component, or
  `...v2.createAndroidComposeRule<androidx.activity.ComponentActivity>()` for a whole screen.
- Wrap content in `TwitchTheme { … }` (`com.example.virtualtwitchdroid.core.designsystem.theme.TwitchTheme`).
- **Feed stateless composables mock data**: a screen takes an `XxxUiState` sealed value + lambdas;
  a component takes model objects. Assert via **semantics**: `onNodeWithText`,
  `onNodeWithContentDescription`, `assertIsDisplayed`, `performClick`, `performScrollToIndex`,
  `hasScrollAction()`. Measure layout with `Modifier.onSizeChanged { … }`.
- **Null out image URLs** (`thumbnailUrl = null`, `avatarUrl = null`) so Coil never hits the network
  in a test. (`:core:designsystem` androidTest adds INTERNET permission so Coil fails gracefully.)
- If a composable is `private` but needs a direct test, widen it to **`internal`** (androidTest sees
  a module's `internal`). Prefer testing the stateless composable, not the controller-driven route.

## Shared fakes (`:core:testing`, consumed via `androidTestImplementation(projects.core.testing)`)

- `com.example.virtualtwitchdroid.core.testing.data.TestData` — `sampleGames`, `sampleChannels`,
  `sampleChatMessages`.
- `com.example.virtualtwitchdroid.core.testing.repository.FakeRepositories` — `FakeStreamsRepository`,
  `FakeStreamRepository`, `FakeChatRepository` (a `MutableSharedFlow<ChatEvent>` with a test-only
  `send(event)` and a `subscriptionCount`).
- `com.example.virtualtwitchdroid.core.testing.util.MainDispatcherRule` — coroutine main-dispatcher rule
  (host-side unit tests).

For one-off fakes, build the model inline (the repo has **no** `PreviewParameterProvider` convention).

## The discipline: fail-before, pass-after

The proof must discriminate the change. Write the test so it **fails before** your fix and **passes
after** — run `./gradlew :<module>:connectedDebugAndroidTest` at both points. A test that passes
before the change proves nothing. Worked example: the chat overlay's scroll test asserts an old line
is still displayed after a new message arrives — red before the auto-scroll gate, green after.

## Worked example — a live-only overlay

```kotlin
// feature/publish/src/androidTest/.../ChatReceiveOverlayTest.kt
class ChatReceiveOverlayTest {
    @get:Rule val rule = createComposeRule() // v2

    @Test fun scrollingUp_reachesOldChat_andNewMessageDoesNotYankReaderToBottom() {
        val messages = mutableStateOf(fakeChat(1..40))
        rule.setContent { TwitchTheme { Box(Modifier.height(400.dp)) {
            ChatReceiveOverlay(messages = messages.value, modifier = Modifier.fillMaxWidth())
        } } }
        rule.onNode(hasScrollAction()).performScrollToIndex(0)
        rule.onNodeWithText("line-001", substring = true).assertIsDisplayed()
        rule.runOnIdle { messages.value = messages.value + fakeChat(41..41) }
        rule.onNodeWithText("line-001", substring = true).assertIsDisplayed() // not yanked away
    }
}
```

See [[virtualtwitchdroid-architecture]] for module/DI structure and [[virtualtwitchdroid-coding-style]] for the
Kotlin/Compose style these tests must also follow. The bundled `testing-setup` skill has Google's
Compose-testing + Hilt-testing reference material for deeper patterns.
