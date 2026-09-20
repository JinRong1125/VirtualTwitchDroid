package com.example.virtualtwitchdroid.core.designsystem.component

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.virtualtwitchdroid.core.designsystem.R
import com.example.virtualtwitchdroid.core.designsystem.theme.Elevation
import com.example.virtualtwitchdroid.core.designsystem.theme.Motion
import com.example.virtualtwitchdroid.core.designsystem.theme.Spacing
import kotlin.math.roundToInt

// Default docked-width fraction of the screen's SHORTER side (width-driven mode): HALF the shorter
// side, i.e. half the viewport WIDTH in portrait / half the viewport HEIGHT in landscape. The watch/
// stream mini uses this default; a caller can override via FloatingMiniWindow's `dockWidthFraction`
// (the publish portrait mini uses 1/3).
private const val MINI_WIDTH_FRACTION = 1f / 2f
private val CAPTION_HEIGHT = 48.dp
private val EDGE_PADDING = 12.dp

/**
 * The docked width (px) for the width-driven sizing mode: [widthFraction] of the screen's shorter side
 * (that fraction of the viewport WIDTH in portrait / HEIGHT in landscape), clamped so the whole card
 * (video + [captionPx]) still fits the height, and never < 1px. Pure so it can be unit-tested for both
 * orientations without a device.
 */
internal fun defaultDockedMiniWidth(
    areaW: Float,
    areaH: Float,
    captionPx: Float,
    padPx: Float,
    aspect: Float,
    widthFraction: Float = MINI_WIDTH_FRACTION,
): Float {
    val rawMiniW = minOf(areaW, areaH) * widthFraction
    val maxTotalH = areaH - 2f * padPx
    return minOf(rawMiniW, (maxTotalH - captionPx) * aspect).coerceAtLeast(1f)
}

/**
 * Resting top (Y, px) of the docked mini window, keeping it clear of the soft keyboard and any focused
 * input. It normally docks [padPx] above the docking area's bottom ([areaH]); when the IME intrudes
 * ([imeBottomPx] px from the window bottom) it docks above the keyboard instead; and when a focused
 * field's top ([focusedFieldTopPx], window Y) would fall under the docked card it docks above that
 * field. Pure so the placement rule is unit-testable without a device. Never above [padPx] from the top.
 */
internal fun miniRestTop(
    areaH: Float,
    imeBottomPx: Float,
    focusedFieldTopPx: Float?,
    miniTotalH: Float,
    padPx: Float,
): Float {
    val keyboardTop = areaH - imeBottomPx
    val naturalTop = keyboardTop - miniTotalH - padPx // bottom-corner dock, kept clear of the keyboard
    // Re-dock above the field ONLY when it would overlap the bottom-docked card (its top dips below the
    // card's top edge). A field higher up the screen — clear of the card — is left alone.
    val top = if (focusedFieldTopPx != null && focusedFieldTopPx > naturalTop) {
        focusedFieldTopPx - miniTotalH - padPx
    } else {
        naturalTop
    }
    return top.coerceAtLeast(padPx)
}

/**
 * A floating, **draggable** in-app "picture-in-picture" window drawn *over* the app, shared by the
 * watch (video) and publish (camera) mini-players so both behave identically.
 *
 * Minimizing/expanding is a true PiP-style **collapse / expand** (no fade), driven by a Compose
 * [updateTransition]: the window's real size + position interpolate between a fullscreen rect and
 * the small bottom-end corner window, so it visibly shrinks *into* / grows *out of* the corner.
 * Once settled it can be dragged anywhere, clamped on-screen. Tapping the content calls [onExpand];
 * the ✕ calls [onClose].
 *
 * The caller keeps this composed while its session exists (so the expand animation can play) and
 * passes [minimized]; when fully expanded and settled this draws nothing. [content] fills the
 * [videoAspectRatio] video/camera area (docked width ≈ half the screen's shorter side); [badge] is an
 * optional overlay (e.g. a LIVE pill) shown once settled.
 *
 * [portraitHeightFraction] / [landscapeHeightFraction]: when set, size the mini's TOTAL height
 * (video + caption) to that fraction of the screen height in that orientation (height-driven), instead
 * of the width-driven default. [dockWidthFraction] tunes that width-driven default (the fraction of
 * the shorter side used as the width; default ½). Opt-in per caller so callers don't resize each
 * other — the watch/stream mini uses the ½ default both ways; the publish camera mini uses ⅓ WIDTH in
 * portrait (dockWidthFraction) and ½ HEIGHT in landscape (landscapeHeightFraction).
 */
@Composable
fun FloatingMiniWindow(
    minimized: Boolean,
    title: String,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    videoAspectRatio: Float = 16f / 9f,
    portraitHeightFraction: Float? = null,
    landscapeHeightFraction: Float? = null,
    dockWidthFraction: Float = MINI_WIDTH_FRACTION,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val transition = updateTransition(targetState = minimized, label = "FloatingMiniWindow")
    val progress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = Motion.medium) },
        label = "progress",
    ) { collapsed -> if (collapsed) 1f else 0f }

    // Fully expanded and settled → nothing to draw (the fullscreen screen is showing).
    if (!minimized && progress <= 0.001f) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        val captionH = with(density) { CAPTION_HEIGHT.toPx() }
        val pad = with(density) { EDGE_PADDING.toPx() }

        // Dock size. Default (no height fraction given, e.g. the watch mini): HALF of the screen's
        // shorter side wide (½ width in portrait, ½ height in landscape), height following the window
        // aspect, clamped to fit.
        // Height-fraction mode (the publish camera mini): total height = fraction × screen height, width
        // following the aspect (clamped to the screen). coerceAtLeast(1f)/coerceAtMost guard degenerate
        // sizes so miniW is never 0/negative/off-screen (that would crash the width/aspect/drag math).
        val landscape = areaW > areaH
        val heightFraction = if (landscape) landscapeHeightFraction else portraitHeightFraction
        val maxTotalH = areaH - 2f * pad
        val miniW = if (heightFraction != null) {
            val videoH = ((heightFraction * areaH).coerceAtMost(maxTotalH) - captionH).coerceAtLeast(1f)
            (videoH * videoAspectRatio).coerceIn(1f, (areaW - 2f * pad).coerceAtLeast(1f))
        } else {
            defaultDockedMiniWidth(areaW, areaH, captionH, pad, videoAspectRatio, dockWidthFraction)
        }
        val cardW = lerp(areaW, miniW, progress)
        val miniTotalH = miniW / videoAspectRatio + captionH
        val restX = (areaW - miniW - pad).coerceAtLeast(0f)
        // Dock clear of the soft keyboard and of any focused, opted-in input field (see MiniWindowAnchor).
        // On a screen that PANS the window for the IME the pan already lifts the mini, so skip the
        // inset-based lift there (adding it would double up and push the mini off the top).
        val anchor = LocalMiniWindowAnchor.current
        val imeBottom = if (anchor.windowPansForIme) 0f else WindowInsets.ime.getBottom(density).toFloat()
        val restY = miniRestTop(areaH, imeBottom, anchor.focusedInputTopInWindow, miniTotalH, pad)
        val baseX = lerp(0f, restX, progress)
        val baseY = lerp(0f, restY, progress)

        var drag by remember { mutableStateOf(Offset.Zero) }
        val settled = progress > 0.98f
        val dragX = if (settled) drag.x.coerceIn(pad - restX, areaW - miniW - pad - restX) else 0f
        val dragY = if (settled) drag.y.coerceIn(pad - restY, areaH - miniTotalH - pad - restY) else 0f

        Surface(
            shape = RoundedCornerShape(if (settled) 10.dp else 0.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (settled) Elevation.high else Elevation.none,
            modifier = Modifier
                .offset { IntOffset((baseX + dragX).roundToInt(), (baseY + dragY).roundToInt()) }
                .width(with(density) { cardW.toDp() })
                .pointerInput(areaW, areaH, settled) {
                    if (settled) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            drag = Offset(drag.x + delta.x, drag.y + delta.y)
                        }
                    }
                },
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(videoAspectRatio)
                        .clip(
                            RoundedCornerShape(
                                topStart = if (settled) 10.dp else 0.dp,
                                topEnd = if (settled) 10.dp else 0.dp,
                            ),
                        )
                        .background(Color.Black)
                        .clickable(onClick = onExpand),
                ) {
                    content()
                    if (settled && badge != null) {
                        Box(Modifier.align(Alignment.TopStart).padding(Spacing.sm)) { badge() }
                    }
                }
                if (settled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close_mini_player),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
