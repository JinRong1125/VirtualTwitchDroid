---
name: virtualtwitchdroid-architecture
description: Architecture conventions for the VirtualTwitchDroid app — a multi-module Jetpack
  Compose Twitch client whose structure is modeled on Now in Android (android/nowinandroid).
  Use this whenever adding or changing a Gradle module, a feature screen, a ViewModel or
  app-scoped controller, a data repository or data source, Hilt DI wiring, a navigation
  route, or the design system, so the change respects the module boundaries, convention
  plugins, DI, data-layer, and type-safe-navigation patterns the app already follows.
metadata:
  author: VirtualTwitchDroid
  last-updated: '2026-09-15'
  keywords:
  - android
  - architecture
  - nowinandroid
  - modularization
  - hilt
  - compose
  - navigation
  - convention-plugins
---

## What this app is

VirtualTwitchDroid is an anonymous Twitch client in Jetpack Compose. Its module layout, build
convention plugins, DI, and data/UI layering follow **Now in Android**
(`github.com/android/nowinandroid`); its UI/UX is modeled on skydoves/twitch-clone-compose
(browse/publish) and Xtra (the watch screen). Real anonymous stack: HLS (media3/ExoPlayer) +
IRC chat + RootEncoder (camera → RTMP). Repo is **not** under git — leave a clear diff/summary.

Match the existing code before inventing anything: read a sibling module/feature and copy its
shape. When a choice isn't covered here, do what Now in Android does.

## Module graph (never violate these dependency directions)

```
:app  ──▶ every :feature:* and the :core:* it needs (DI entry point, MainActivity, NavHost)
:feature:*  ──▶ :core:data, :core:designsystem, :core:model, :core:common   (UI + presentation)
:core:data  ──▶ :core:network, :core:model, :core:common                    (repositories)
:core:network ──▶ :core:model, :core:common                                 (Retrofit/OkHttp, DTOs)
:core:designsystem ──▶ (no other module)                                    (theme + Twitch UI kit)
:core:model ──▶ (nothing)                                                   (pure Kotlin data types)
:core:testing ──▶ test-only helpers, used by androidTest/test
```

Hard rules:
- **No `:feature` depends on another `:feature`.** Cross-feature navigation is wired only in
  `:app` (`TwitchApp.kt`) by calling one feature's `navigateToXxx` from another's callback.
- **No `:core` depends on a `:feature` or on `:app`.**
- **UI lives in `:core:designsystem` and `:feature:*` only.** `:core:data`/`:core:network`/
  `:core:model` are Android-library/JVM modules with no Compose UI.
- Models cross module boundaries as `:core:model` types; network DTOs stay inside `:core:network`
  and are mapped to model types there.

## Gradle: use the convention plugins, not raw config

Every module applies plugins from `build-logic/convention` via the version catalog — never
hand-roll `android {}` blocks that these already set (compileSdk, Kotlin, Compose, etc.):

- App: `alias(libs.plugins.virtualtwitchdroid.android.application)` + `.application.compose`
- A UI feature: `alias(libs.plugins.virtualtwitchdroid.android.feature)` + `.android.library.compose`
  (the feature plugin already wires Hilt, `:core:data`, `:core:designsystem`, `:core:model`,
  navigation, lifecycle — check `AndroidFeatureConventionPlugin.kt` before adding those by hand)
- A non-UI core library: `.android.library` (+ `.hilt` when it has DI, `.android.library.compose`
  only for `:core:designsystem`)
- Pure Kotlin: `.jvm.library`

Declare dependencies through `libs.versions.toml` catalog aliases only; add a new library there
first. To add a module: `include(":core:foo")` in `settings.gradle.kts`, create
`core/foo/build.gradle.kts` applying the right convention plugin, mirror a sibling.

## DI: Hilt everywhere

- `@HiltAndroidApp` app, `@AndroidEntryPoint` `MainActivity`.
- Bind repository interfaces to `Default*` impls in a `@Module @InstallIn(SingletonComponent::class)`
  in that module's `di/` package (see `core/data/di/DataModule.kt`). Prefer `@Binds` for
  interface→impl, `@Provides` for constructed singletons (Retrofit, OkHttp, players).
- Constructor-inject with `@Inject`. Scope app-lifetime singletons `@Singleton`.

## Data layer (offline/anonymous-first, Now in Android style)

- A repository is an **interface** in `:core:data/repository/` (e.g. `StreamsRepository`) with a
  `Default*` implementation bound via Hilt. Repositories expose suspend functions and/or
  `Flow`/`StateFlow`; they never expose network DTOs.
- Networking (Retrofit + OkHttp + kotlinx.serialization) lives in `:core:network`; parse into
  `:core:model` types there. Images use **Coil 3** (`io.coil-kt.coil3`, `AsyncImage`).

## Feature layer

A feature module (`:feature:xxx`) contains, per screen:

1. **`XxxScreen.kt`** — a stateless `@Composable XxxScreen(...)` that takes state + lambdas, plus a
   route-level composable that collects state. Build UI only from `:core:designsystem` components
   and the theme — **never** hard-code Twitch colors in a feature; add/borrow a designsystem token.
2. **State holder** — pick one:
   - **`@HiltViewModel XxxViewModel`** for screen-scoped state (the default; see `BrowseViewModel`,
     `CategoryViewModel`, `GamesViewModel`). Expose a `sealed interface XxxUiState` via
     `StateFlow`, built with `stateIn(...)` / `flatMapLatest`. Collect with
     `collectAsStateWithLifecycle()`.
   - **`@Singleton XxxController`** ONLY when media must outlive the screen (mini-player / PiP):
     `PlayerController` (ExoPlayer) and `PublishController` (RootEncoder streamer) are app-scoped so
     playback/broadcast survive leaving the screen. Hoisted out of a ViewModel deliberately.
3. **`navigation/XxxNavigation.kt`** — **type-safe Navigation Compose**:
   `@Serializable data object/class XxxRoute`, a `fun NavGraphBuilder.xxxScreen(...)` extension that
   calls `composable<XxxRoute> { ... }`, and a `fun NavController.navigateToXxx(...)` when other
   destinations navigate here. `:app` assembles these in one `NavHost` under the 5-tab bottom bar
   (Games / Popular / Search / Go Live / Avatar).

Follow [[virtualtwitchdroid-ui]] for the concrete UI/UX conventions (palette, the `:core:designsystem`
kit, mini-player, PiP, the portrait-locked publish screen).

## Build / verify workflow (local AI dev loop, no CI/CD)

- CLI Gradle needs `JAVA_HOME` = Android Studio JBR — `scripts/ai-dev-loop.sh` sets it. See
  [[virtualtwitchdroid-build-toolchain]].
- Gate every change with `scripts/ai-dev-loop.sh` (unit → assemble → install → on-device UI tests
  for `:core:designsystem`, `:feature:browse`, `:feature:stream`). Then verify runtime behavior a
  fixed test can't assert with **ARTEMIS via its MCP tools** (never its CLI); see
  [[virtualtwitchdroid-dev-workflow]] and `scripts/ai-controller.md`. Report files changed + gate result
  + ARTEMIS verdict; nothing outward (no push/publish/network side effects).

## Checklists

**Adding a feature screen**: create module (feature convention plugin) → `XxxScreen` (designsystem
only) → ViewModel (or reuse a controller) with a sealed `UiState` + `stateIn` → type-safe
`navigation/` (Route + `xxxScreen` + `navigateToXxx`) → wire into `TwitchApp` NavHost → unit test
the ViewModel/repository → gate + ARTEMIS.

**Adding data**: model type in `:core:model` → network call + DTO→model mapping in `:core:network`
→ repository interface + `Default*` impl in `:core:data` → bind in `DataModule` → consume from a
ViewModel/controller. Never let a feature touch `:core:network` directly.
