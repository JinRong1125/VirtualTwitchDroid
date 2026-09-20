package com.example.virtualtwitchdroid.core.designsystem.theme

/**
 * The app's motion-duration scale (in milliseconds) — a single source of truth for animation
 * timings, mirroring [Spacing]. Feed a step straight into `tween(durationMillis = …)`. The bands
 * follow Material 3's short / medium / long motion durations so transitions feel consistent.
 */
object Motion {
    /** 150ms — a small, quick transition (a toggle, a fade). */
    val short = 150

    /** 320ms — an element / state transition (the mini-player collapse & expand). */
    val medium = 320

    /** 500ms — a full-screen navigation slide. */
    val long = 500
}
