package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * Shared hand-off between a focused text field (inside a feature screen) and the app-level
 * [FloatingMiniWindow] overlay: the field publishes its top edge (window Y, px) while focused so the
 * mini window can dock *above* it instead of covering it / being covered by the keyboard.
 *
 * One instance is provided at the app root via [LocalMiniWindowAnchor]; it is `null` whenever no
 * opted-in field holds focus.
 */
class MiniWindowAnchor {
    /** Top edge (Y, px, in window coordinates) of the currently focused opted-in field, or null. */
    var focusedInputTopInWindow: Float? by mutableStateOf(null)
        private set

    /**
     * True while the visible screen pans the whole window for the IME (`SOFT_INPUT_ADJUST_PAN`, e.g. the
     * camera/Go-Live screen). The pan already carries the floating mini up above the keyboard, so on
     * those screens the mini must NOT add its own inset-based lift — that would double-compensate and
     * shove it off the top. Set by the panning screen while it is shown.
     */
    var windowPansForIme: Boolean by mutableStateOf(false)

    // The field that currently holds the slot. Ownership makes focus transfer between two opted-in
    // fields safe: a blurring field must not null a slot the newly focused field has already claimed
    // (Compose does not guarantee blur fires before the next field's focus).
    private var owner: Any? = null

    /** [owner] claims the slot (or updates its reported position). */
    fun claim(owner: Any, topInWindow: Float) {
        this.owner = owner
        focusedInputTopInWindow = topInWindow
    }

    /** [owner] releases the slot — a no-op once another field has claimed it. */
    fun release(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            focusedInputTopInWindow = null
        }
    }
}

/**
 * App-root-provided [MiniWindowAnchor]. The default is a shared, inert instance rather than `error()`
 * so a screen rendered without the provider (a @Preview or an isolated Compose test — neither has a
 * mini overlay reading the slot) still composes; the real provider is at the app root above all usage.
 */
val LocalMiniWindowAnchor = staticCompositionLocalOf { MiniWindowAnchor() }

/**
 * Opt a text field into mini-window avoidance: while focused it reports its top edge to
 * [LocalMiniWindowAnchor], so a [FloatingMiniWindow] docks above it rather than overlapping it. The
 * report tracks re-layout (e.g. the field lifting for the IME) and clears on blur/dispose. A per-field
 * ownership token keeps focus transfer between two opted-in fields from clobbering the slot.
 */
@Composable
fun Modifier.avoidMiniWindowWhenFocused(): Modifier {
    val anchor = LocalMiniWindowAnchor.current
    val ownerId = remember { Any() }
    var topInWindow by remember { mutableStateOf(0f) }
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(anchor, ownerId) {
        onDispose { anchor.release(ownerId) }
    }
    return this
        .onGloballyPositioned { coords ->
            topInWindow = coords.positionInWindow().y
            if (focused) anchor.claim(ownerId, topInWindow)
        }
        .onFocusChanged { state ->
            focused = state.isFocused
            if (state.isFocused) anchor.claim(ownerId, topInWindow) else anchor.release(ownerId)
        }
}
