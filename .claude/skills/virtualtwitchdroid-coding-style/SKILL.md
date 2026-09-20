---
name: virtualtwitchdroid-coding-style
description: Kotlin + Jetpack Compose coding-style rules for the VirtualTwitchDroid app — the conventions
  that the `.editorconfig` and the `:app:lintDebug` gate cannot fully auto-enforce. Use this
  WHENEVER writing or editing Kotlin (`.kt`) or Compose code in this repo (a screen, ViewModel,
  controller, repository, data source, designsystem component, or convention plugin), so
  AI-written code matches the house style — formatting, imports, Kotlin idioms, Compose patterns,
  and (critically) the design tokens — before it hits lint.
metadata:
  author: VirtualTwitchDroid
  last-updated: '2026-09-18'
  keywords:
  - kotlin
  - compose
  - coding-style
  - editorconfig
  - lint
  - design-tokens
  - formatting
---

## How style is enforced here

`.editorconfig` (repo root) declares the style, and it is enforced by two gates in
`scripts/ai-dev-loop.sh`:
- **Spotless + ktlint** — `spotlessCheck` fails the loop on any unformatted code; run
  `./gradlew spotlessApply` to auto-format. ktlint reads the `.editorconfig` below.
- **Android Lint** — `:app:lintDebug` (correctness/perf/security), filtered by `app/lint-baseline.xml`.

Write to the rules below so `spotlessApply` is a no-op; a few ktlint rules aren't auto-fixable
(e.g. `max-line-length`, `comment-wrapping`) — those you must satisfy by hand. See
[[virtualtwitchdroid-lint-style]].

## Formatting (from `.editorconfig`)

- 4-space indent, spaces only; UTF-8; LF line endings; final newline; no trailing whitespace.
- Max line length **120**. Wrap long calls/params one-per-line with a trailing comma on the last.
- **No wildcard imports** (`import foo.*`) — import each symbol explicitly, ordered.
- Use trailing commas on multi-line argument/parameter lists.

## Kotlin idioms the repo uses

- Prefer `val` over `var`; immutable data (`data class`, `List`/`Map`) by default.
- Expression bodies for one-liners (`fun x() = ...`); `when` over long `if/else` chains.
- Model closed sets as `sealed interface` + `data object`/`data class` (e.g. every `XxxUiState`).
- `internal` for module-private API; `private` for file/class-private; public only what crosses a
  module boundary. Constructor-inject with `@Inject`; scope app-lifetime singletons `@Singleton`.
- Coroutines/Flow: `StateFlow` built with `stateIn(scope, WhileSubscribed(5_000), initial)`,
  `flatMapLatest` for re-triggerable sources, `.catch { }` so a stream failure never crashes the
  collector. Use `kotlin.time.Duration` (e.g. `delay(1.seconds)`), not bare `Long` millis.

## Compose conventions

- `@Composable` functions are **PascalCase** (lint's function-naming rule is configured to allow
  this for `@Composable`/`@Preview`). Composables return `Unit`, take a trailing `modifier: Modifier
  = Modifier` after required params.
- Each screen: a **stateless** `XxxScreen(state, lambdas, modifier)` + a route-level composable that
  collects state via `collectAsStateWithLifecycle()` and forwards callbacks. Hoist state up.
- Add a `@Preview` wrapped in `TwitchTheme { }`. Keep side effects in `LaunchedEffect`/
  `DisposableEffect`, never in composition.

## Design tokens — DO NOT hardcode (highest-leverage rule)

Every visual value comes from the `:core:designsystem` theme; raw literals are a style defect here.
See [[virtualtwitchdroid-design-tokens]].

- Text size/weight → `MaterialTheme.typography.*` (headlineMedium, titleLarge/Small, bodyLarge/
  Medium/Small, labelLarge/Medium/Small). Never `fontSize = 13.sp`.
- Corner radii → `MaterialTheme.shapes.*` (extraSmall/small/medium/large/extraLarge). Never
  `RoundedCornerShape(6.dp)`.
- Padding/gaps → `Spacing.*` (none/xxs/xs/sm/md/lg/xl/xxl, an 8dp scale). Avoid raw `.dp` literals.
- Colors → tokens in `theme/Color.kt` (`TwitchPurple`, `LiveRed`, `XtraLiveRed`, `LiveBlue`, …) or
  `MaterialTheme.colorScheme.*`. Never a hex `Color(0xFF…)` in a feature.

## Comments & naming

- Match the **comment density and idiom of the surrounding file** — this repo favors short KDoc on
  public composables/functions explaining the *why*, not the *what*.
- Names read like the neighbours; mirror a sibling file's shape before inventing a new one.

## Before finishing a Kotlin change

1. No wildcard imports, no lines > 120, trailing commas on multi-line lists.
2. No hardcoded `sp`/`dp`/`RoundedCornerShape`/hex color — use the tokens above.
3. Gate with `scripts/ai-dev-loop.sh`; `:app:lintDebug` must report no NEW issues, and prefer
   fixing a real lint error at the source (e.g. `@RequiresApi`) over adding to the baseline.

Follow [[virtualtwitchdroid-architecture]] for structure/module rules and [[virtualtwitchdroid-ui]] for UI/UX.
