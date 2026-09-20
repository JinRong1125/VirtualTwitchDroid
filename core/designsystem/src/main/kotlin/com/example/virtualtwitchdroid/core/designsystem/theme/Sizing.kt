package com.example.virtualtwitchdroid.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The app's component-size scale — a single source of truth for the box size of small controls and
 * avatars (design tokens), mirroring [Spacing]. This sizes the CONTROL (a circular button, an avatar
 * image); the glyph drawn inside it uses [IconSize].
 */
object Sizing {
    /** 24dp — a small inline avatar (a channel row). */
    val sm = 24.dp

    /** 32dp — a header avatar. */
    val md = 32.dp

    /** 40dp — a compact circular control (overlay minimize / PiP / mic, a mute toggle). */
    val lg = 40.dp

    /** 56dp — an emphasized circular control (flip camera). */
    val xl = 56.dp

    /** 64dp — the central media play/pause button. */
    val xxl = 64.dp
}
