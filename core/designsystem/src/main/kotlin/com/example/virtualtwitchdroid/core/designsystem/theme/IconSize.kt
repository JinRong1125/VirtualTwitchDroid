package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The app's icon-size scale — a single source of truth for `Icon`/glyph sizes (design tokens),
 * mirroring [Spacing]. Screens reference these named steps instead of scattering raw `dp` on icons,
 * so glyphs read at consistent sizes across the app. [md] (24dp) is the Material baseline icon size.
 */
object IconSize {
    /** 12dp — a tiny indicator glyph (e.g. a live dot). */
    val xxs = 12.dp

    /** 16dp — a small inline glyph (a verified badge, a reward-token coin). */
    val xs = 16.dp

    /** 20dp — a compact action / overlay icon. */
    val sm = 20.dp

    /** 24dp — the default Material icon size. */
    val md = 24.dp

    /** 28dp — an emphasized action icon (flip camera, a reward icon). */
    val lg = 28.dp

    /** 40dp — a large status / empty-state icon. */
    val xl = 40.dp

    /** 48dp — a hero / media-transport icon (play, pause). */
    val xxl = 48.dp
}
