package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The app's elevation scale — a single source of truth for surface elevation (a tonal tint and/or a
 * drop shadow), mirroring [Spacing]. Surfaces reference these named steps instead of raw `dp` so the
 * app layers on one consistent depth rhythm, aligned with Material 3's elevation levels.
 */
object Elevation {
    /** 0dp — flat, sitting directly on the background. */
    val none = 0.dp

    /** 4dp — a gently raised surface (a chat input bar tinted above the content behind it). */
    val low = 4.dp

    /** 12dp — a floating overlay's drop shadow (the draggable mini-player window). */
    val high = 12.dp
}
