package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale — a single source of truth for padding and gaps (design tokens).
 *
 * Screens should reference these named steps instead of scattering raw `dp` literals, so the whole
 * app breathes on one consistent rhythm. The steps roughly double, matching an 8dp base grid.
 */
object Spacing {
    /** 0dp — no gap. */
    val none = 0.dp

    /** 2dp — hairline gaps inside dense chips/badges. */
    val xxs = 2.dp

    /** 4dp — tight inner padding. */
    val xs = 4.dp

    /** 8dp — the base grid unit; default gap between related items. */
    val sm = 8.dp

    /** 12dp — comfortable padding inside cards and overlays. */
    val md = 12.dp

    /** 16dp — standard screen/content margin. */
    val lg = 16.dp

    /** 24dp — section separation / empty-state padding. */
    val xl = 24.dp

    /** 32dp — large emphasis spacing. */
    val xxl = 32.dp
}
