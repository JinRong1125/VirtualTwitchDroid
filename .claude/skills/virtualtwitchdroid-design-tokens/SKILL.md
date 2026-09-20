---
name: virtualtwitchdroid-design-tokens
description: The authoritative catalog of VirtualTwitchDroid's design-token layer — the ONLY visual
  constants (color, type, shape, spacing, icon size, component size, elevation, motion) the app may
  use. Load this WHENEVER writing or editing any Compose UI in this repo (a screen, a designsystem
  component, a badge/card/overlay) and you need a color, text style, corner radius, padding/gap,
  icon or control size, elevation, or animation duration — pick a token from the whitelist here
  instead of a raw literal, so the app stays cohesive and no invented values creep in.
metadata:
  author: VirtualTwitchDroid
  last-updated: '2026-09-18'
  keywords:
  - design tokens
  - theme
  - compose
  - spacing
  - color
  - single source of truth
---

## Single source of truth

Every visual constant lives in **`:core:designsystem/.../theme/`** and is referenced by a named
token — never a raw literal in a feature. This is the whitelist: if a value you want is not a token
below, either use the nearest token or add a step to the scale (see the last section) — **do not
invent a name and do not hardcode a literal.** Raw literals in `feature/` + `core/designsystem` UI
are a style defect here, and some are machine-enforced (see Guards).

The layer spans **eight** families. Reference them as the token object (`Spacing.md`, `IconSize.sm`,
…) or, for M3 subsystems, via `MaterialTheme.*`.

## The whitelist

### Color — `Color.kt`, and `MaterialTheme.colorScheme.*`
Brand/semantic colors: `TwitchPurple`, `LiveRed`, `XtraLiveRed`, `LiveBlue`, `EmojiGold`,
`ChatMessageNotice`, `PlayerScrim` (~40% black controls scrim). Prefer `MaterialTheme.colorScheme.*`
(`primary`, `surface`, `onSurface`, `onSurfaceVariant`, `error`, …) for anything role-based. Never a
raw `Color(0xFF…)` hex in a feature.

### Typography — `Type.kt` (`TwitchTypography`), via `MaterialTheme.typography.*`
`headlineMedium` (28/ExtraBold), `titleLarge` (22/Bold), `titleMedium` (16), `titleSmall` (14/Bold),
`bodyLarge`/`bodyMedium`/`bodySmall` (16/14/12), `labelLarge`/`labelMedium`/`labelSmall` (14/12/11).
Never a raw `fontSize = N.sp` (enforced — see Guards). For a short label centered in a colored
pill/badge, chain **`.pillCentered()`** (in `Type.kt`) onto the style.

### Shape (corner radius) — `Shape.kt` (`TwitchShapes`), via `MaterialTheme.shapes.*`
`extraSmall` 4 · `small` 8 · `medium` 12 · `large` 16 · `extraLarge` 28 (dp). Never
`RoundedCornerShape(n.dp)` when a step fits.

### Spacing (padding & gaps) — `Spacing.kt` — 8dp-based scale
`none` 0 · `xxs` 2 · `xs` 4 · `sm` 8 (base unit) · `md` 12 · `lg` 16 · `xl` 24 · `xxl` 32 (dp).
Use for `padding(...)`, `Arrangement.spacedBy(...)`, `Spacer` gaps.

### IconSize (icon / glyph sizes) — `IconSize.kt`
`xxs` 12 · `xs` 16 · `sm` 20 · `md` 24 (Material baseline) · `lg` 28 · `xl` 40 · `xxl` 48 (dp).
Use on an `Icon`/emote glyph's `Modifier.size(...)`.

### Sizing (control & avatar BOX sizes) — `Sizing.kt`
`sm` 24 · `md` 32 · `lg` 40 · `xl` 56 · `xxl` 64 (dp). Use for the box of a circular control or an
avatar image; the glyph inside it uses `IconSize`.

### Elevation (surface depth) — `Elevation.kt`
`none` 0 · `low` 4 (a raised/tinted surface) · `high` 12 (a floating overlay's shadow) (dp). Use for
`tonalElevation` / `shadowElevation`.

### Motion (animation durations, ms) — `Motion.kt`
`short` 150 · `medium` 320 (element/state transition) · `long` 500 (full-screen nav slide). Feed into
`tween(durationMillis = Motion.medium)`.

## Guards (keep the layer honest)

- **`ThemeTokensTest`** (`:core:designsystem` unit) locks the scales: Spacing/IconSize/Sizing/
  Elevation/Motion must stay **strictly increasing**, and a few values are pinned **exactly**
  (`IconSize.md == 24dp`, `Spacing.sm == 8dp`, the five `TwitchShapes` radii, the `TwitchTypography`
  sizes). Reordering a scale or changing a pinned value fails the test. Note: nudging a non-pinned
  value (e.g. `Motion.medium` 320→300) stays green — so still visually verify a value change on device.
- **`NoHardcodedFontSizeTest`** fails the build if any `fontSize = N.sp` reappears in feature/
  designsystem UI.
- Both run in `scripts/ai-dev-loop.sh` (unit phase). Spotless/ktlint + `:app:lintDebug` also gate.

## Adding a token / step

1. Add the `val` (or `.dp`) to the right object in `theme/`, in scale order, with a one-line KDoc
   saying the value **and** when to use it.
2. Extend `ThemeTokensTest` to cover it (keep the scale strictly increasing).
3. Migrate existing raw literals of that value to the new token; run `scripts/ai-dev-loop.sh`.

## Why a skill (not just code)

The theme code is the source of truth, but an agent editing a feature can't see it without searching.
This skill is the **machine-readable whitelist + rationale in one place** so token names are picked,
never invented (the failure mode design-token-for-agents guidance warns about), matching the app's
[[virtualtwitchdroid-coding-style]] rules. Values/status are also mirrored in the design-tokens memory.
